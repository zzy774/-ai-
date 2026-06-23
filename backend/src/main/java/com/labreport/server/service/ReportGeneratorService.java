package com.labreport.server.service;

import com.labreport.server.ai.AiProvider;
import com.labreport.server.ai.AiProviderFactory;
import com.labreport.server.model.entity.Project;
import com.labreport.server.model.entity.ReportRecord;
import com.labreport.server.model.entity.UploadedFile;
import com.labreport.server.model.mapper.ProjectMapper;
import com.labreport.server.model.mapper.ReportRecordMapper;
import com.labreport.server.model.mapper.UploadedFileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGeneratorService {

    private final ProjectMapper projectMapper;
    private final ReportRecordMapper recordMapper;
    private final UploadedFileMapper fileMapper;
    private final AiProviderFactory aiFactory;

    @Value("${storage.report-output-dir:./outputs}")
    private String outputDir;

    @Value("${storage.upload-dir:./uploads}")
    private String uploadDir;

    // ================================================================
    //  PUBLIC API — called by ReportController
    // ================================================================

    /**
     * 预览：仅让AI生成内容，不产出DOCX。
     * 返回 JSON: { sections: [{title, content}], templatePlaceholders: [...] }
     */
    public Map<String, Object> preview(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw new RuntimeException("项目不存在");

        // 1. 找模板
        Path templatePath = findTemplateFile(projectId);
        // 2. 解析模板结构
        TemplateInfo templateInfo = templatePath != null
            ? parseTemplate(templatePath)
            : new TemplateInfo(); // 无模板则使用默认结构

        // 3. 收集代码
        String codeContext = collectCodeFiles(projectId);

        // 4. 让AI生成内容
        String aiRaw = callAIForContent(projectId, templateInfo, codeContext, false);
        List<SectionContent> sections = parseAiJson(aiRaw, templateInfo);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sections", sections);
        result.put("templatePlaceholders", templateInfo.placeholders);
        result.put("templateSections", templateInfo.sectionHeadings);
        result.put("hasTemplate", templatePath != null);
        return result;
    }

    // ================================================================
    //  ASYNC GENERATION — 7-phase pipeline
    // ================================================================

    @Async("reportTaskExecutor")
    public void generateAsync(Long projectId, Long recordId, List<String> umlImageNames) {
        ReportRecord record = recordMapper.selectById(recordId);
        Project project = projectMapper.selectById(projectId);
        if (record == null || project == null) return;

        try {
            // Phase 1: 找模板 + 解析模板
            updateProgress(record, 1, "解析模板", "正在分析 DOCX 模板结构…");
            Path templatePath = findTemplateFile(projectId);
            TemplateInfo templateInfo = templatePath != null
                ? parseTemplate(templatePath)
                : new TemplateInfo();

            log.info("模板解析完成: {} 个章节, {} 个占位符",
                templateInfo.sectionHeadings.size(), templateInfo.placeholders.size());

            // Phase 2: 收集代码
            updateProgress(record, 2, "收集代码", "正在读取上传的代码文件…");
            String codeContext = collectCodeFiles(projectId);
            log.info("代码收集完成: {} 字符", codeContext.length());

            // Phase 3: AI分析模板 + 代码 → 生成内容
            updateProgress(record, 3, "AI思考中", "AI 正在按照模板分析代码并撰写报告…");
            String aiRaw = callAIForContent(projectId, templateInfo, codeContext, true);
            List<SectionContent> sections = parseAiJson(aiRaw, templateInfo);

            // Phase 4: 排版
            updateProgress(record, 4, "排版中", "正在将AI内容填入模板…");

            // Phase 5-6: 生成DOCX
            updateProgress(record, 5, "生成DOCX", "正在输出Word文档…");
            Path outputPath;
            if (templatePath != null) {
                outputPath = fillTemplate(project, templatePath, templateInfo, sections, umlImageNames);
            } else {
                outputPath = generateFromScratch(project, sections, umlImageNames);
            }

            // Phase 7: 完成
            updateProgress(record, 7, "完成", "报告生成完毕！");
            record.setStatus("COMPLETED");
            record.setOutputFilePath(outputPath.toString());
            record.setOutputFileSize(Files.size(outputPath));
            record.setGeneratedAt(LocalDateTime.now());
            recordMapper.updateById(record);

        } catch (Exception e) {
            log.error("报告生成失败", e);
            record.setStatus("FAILED");
            record.setErrorMessage(e.getMessage());
            recordMapper.updateById(record);
        }
    }

    // ================================================================
    //  TEMPLATE HANDLING
    // ================================================================

    /** 找到项目的模板文件（仅 TEMPLATE 类型，.doc 自动转换） */
    private Path findTemplateFile(Long projectId) {
        List<UploadedFile> files = fileMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UploadedFile>()
                .eq(UploadedFile::getProjectId, projectId)
                .eq(UploadedFile::getFileType, "TEMPLATE"));
        for (UploadedFile f : files) {
            String name = f.getOriginalName().toLowerCase();
            if (name.endsWith(".docx")) return Path.of(f.getFilePath());
            if (name.endsWith(".doc")) return convertDocToDocx(Path.of(f.getFilePath()));
        }
        return null;
    }

    /** .doc → .docx 转换 */
    private Path convertDocToDocx(Path docPath) {
        String docxName = docPath.getFileName().toString().replaceAll("\\.doc$", ".docx");
        Path docxPath = docPath.resolveSibling(docxName);
        if (Files.exists(docxPath)) return docxPath;
        try {
            log.info("正在转换 .doc -> .docx: {}", docPath);
            ProcessBuilder pb = new ProcessBuilder("python", "-c",
                "import win32com.client as win32; word=win32.Dispatch('Word.Application'); " +
                "word.Visible=False; doc=word.Documents.Open(r'" + docPath.toAbsolutePath() + "'); " +
                "doc.SaveAs2(r'" + docxPath.toAbsolutePath() + "', FileFormat=16); " +
                "doc.Close(); word.Quit()"
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String out = new String(proc.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            int exit = proc.waitFor();
            if (exit == 0 && Files.exists(docxPath)) {
                log.info("转换成功: {}", docxPath);
                return docxPath;
            }
            log.warn("Word转换失败(exit={}): {}", exit, out);
        } catch (Exception e) {
            log.error("转换异常", e);
        }
        return null;
    }

    /** 解析DOCX模板：纯文本模式检测章节标题，只保留后面有占位符的标题 */
    private TemplateInfo parseTemplate(Path templatePath) {
        TemplateInfo info = new TemplateInfo();
        String[] sectionKeywords = {"目的","原理","环境","步骤","结果","分析","总结","代码","设计",
            "实现","讨论","方法","工具","要求","内容","背景","方案","实验内容",
            "选题","需求","可行性","概要","详细","测试","部署","维护","附录","参考","致谢","引言","概述","摘要","关键词",
            "功能","模块","架构","流程","数据","界面","接口","安全","性能",
            "系统","设计","说明"};
        try (FileInputStream fis = new FileInputStream(templatePath.toFile())) {
            XWPFDocument doc = new XWPFDocument(fis);

            List<XWPFParagraph> allParas = doc.getParagraphs();
            List<Integer> possibleHeadings = new ArrayList<>();

            for (int i = 0; i < allParas.size(); i++) {
                XWPFParagraph para = allParas.get(i);
                String text = para.getText().trim();
                if (text.isEmpty()) continue;
                text = text.replaceAll("\t.*", "").trim();

                // 排除干扰: 完整句子(有标点)、祈使句、说明文字
                if (text.contains("：") || text.contains("，") || text.contains("。")
                    || text.contains("请") || text.contains("不要") || text.contains("只")
                    || text.contains("指导") || text.contains("建议")
                    || (text.startsWith("一、") || text.startsWith("二、") || text.startsWith("三、"))) {
                    // 提取${}后跳过标题候选判断
                    Matcher m2 = Pattern.compile("\\$\\{([^}]+)\\}").matcher(text);
                    while (m2.find()) {
                        String var = m2.group(1).trim();
                        if (!info.placeholders.contains(var)) info.placeholders.add(var);
                    }
                    continue;
                }

                // 章节标题候选
                boolean isCandidate = false;
                if (text.length() <= 18
                    && !text.contains("（") && !text.contains("(")
                    && !text.contains("此处") && !text.contains("...")
                    && !text.contains("略")) {
                    for (String kw : sectionKeywords) {
                        if (text.contains(kw)) {
                            isCandidate = true;
                            break;
                        }
                    }
                }
                if (isCandidate) possibleHeadings.add(i);

                // 提取 ${...} 占位符
                Matcher m = Pattern.compile("\\$\\{([^}]+)\\}").matcher(text);
                while (m.find()) {
                    String var = m.group(1).trim();
                    if (!info.placeholders.contains(var)) info.placeholders.add(var);
                }
            }

            // 只保留后面紧跟占位符段落的标题
            java.util.Set<Integer> headingIdxSet = new HashSet<>(possibleHeadings);
            for (int h = 0; h < possibleHeadings.size(); h++) {
                int start = possibleHeadings.get(h);
                int end = (h + 1 < possibleHeadings.size()) ? possibleHeadings.get(h + 1) : allParas.size();
                boolean hasPlaceholder = false;
                for (int j = start + 1; j < Math.min(start + 10, end); j++) {
                    String text = allParas.get(j).getText().trim();
                    if ((text.contains("...") || text.contains("（略）") || text.contains("（此处") || text.contains("此处填写"))
                            && text.length() < 100) {
                        hasPlaceholder = true;
                        break;
                    }
                }
                if (hasPlaceholder) {
                    info.sectionHeadings.add(allParas.get(start).getText().trim().replaceAll("\t.*", "").trim());
                }
            }

            doc.close();
        } catch (Exception e) {
            log.warn("模板解析失败: {}", e.getMessage());
        }

        if (info.sectionHeadings.isEmpty()) {
            // Try to find any headings at all
            log.warn("模板中未检测到占位符段落！请确保模板中有（此处填写...）或...（略）作为标记");
            info.sectionHeadings = List.of("实验目的","实验原理","实验环境","实验步骤","实验结果","代码分析","实验总结");
        }

        log.info("模板解析: {} 个章节(有占位符), {} 个${}变量",
            info.sectionHeadings.size(), info.placeholders.size());

        return info;
    }

    // ================================================================
    //  CODE COLLECTION
    // ================================================================

    private String collectCodeFiles(Long projectId) {
        List<UploadedFile> files = fileMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UploadedFile>()
                .eq(UploadedFile::getProjectId, projectId)
                .eq(UploadedFile::getFileType, "SOURCE_CODE"));

        StringBuilder sb = new StringBuilder();

        // Collect SAMPLE files for AI style reference
        String sampleContent = collectSampleFiles(projectId);
        if (!sampleContent.isEmpty()) {
            sb.append("\n\n====== 参考样例报告（风格参考）======\n");
            sb.append(sampleContent);
            sb.append("\n====== 请参考以上样例的写作风格和内容深度 ======\n\n");
        }

        if (files.isEmpty() && sb.isEmpty()) return "（未上传代码文件和参考样例）";

        // 总代码文本上限设为40KB（约10000 token），超出部分截断
        final int MAX_CODE_CHARS = 40000;
        int totalCodeChars = 0;
        for (UploadedFile f : files) {
            try {
                String content = Files.readString(Path.of(f.getFilePath()));
                String lang = f.getLanguage() != null ? f.getLanguage() : "text";
                String header = "\n// ====== " + f.getOriginalName() + " (" + lang + ") ======\n";
                String limited = content;
                int available = MAX_CODE_CHARS - totalCodeChars;
                if (available <= 0) break;
                if (limited.length() > available) {
                    limited = limited.substring(0, available - 50) + "\n// ...（文件过长，后续内容已截断）";
                }
                sb.append(header).append(limited).append("\n");
                totalCodeChars += header.length() + limited.length() + 1;
            } catch (IOException e) {
                sb.append("\n// 文件无法读取: ").append(f.getOriginalName()).append("\n");
            }
        }
        return sb.toString();
    }

    /** 收集样例报告内容（给AI做风格参考） */
    private String collectSampleFiles(Long projectId) {
        List<UploadedFile> files = fileMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UploadedFile>()
                .eq(UploadedFile::getProjectId, projectId)
                .eq(UploadedFile::getFileType, "SAMPLE"));

        if (files.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (UploadedFile f : files) {
            try {
                String content = Files.readString(Path.of(f.getFilePath()));
                // 只取前3000字作为风格参考，避免超长
                String truncated = content.length() > 3000
                    ? content.substring(0, 3000) + "\n...（后续内容省略）"
                    : content;
                sb.append(truncated).append("\n");
            } catch (IOException ignored) {}
        }
        return sb.toString();
    }

    // ================================================================
    //  AI INTERACTION — 核心
    // ================================================================

    /**
     * 构建严格的结构化 Prompt，要求 AI 按照模板逐一生成内容。
     */
    private String callAIForContent(Long projectId, TemplateInfo templateInfo,
            String codeContext, boolean includeMeta) {
        AiProvider ai = aiFactory.getDefaultProvider();
        Project project = projectMapper.selectById(projectId);

        StringBuilder prompt = new StringBuilder();
        prompt.append("""
            你是一个计算机专业实验报告撰写AI。请严格按照以下要求生成报告内容。

            ====== 核心规则 ======
            1. 你必须严格按照下方「模板结构」中定义的章节顺序和标题来组织内容
            2. 每个章节的内容必须基于「代码文件」中的实际代码来撰写，禁止编造
            3. 代码分析部分必须引用具体的类名、方法名、变量名
            4. 如果没有代码文件，就基于实验主题进行合理的理论阐述
            5. 输出格式必须是严格的JSON（不要markdown代码块包裹）
            6. 禁止使用Markdown语法——不要用**、##、- 等标记，使用纯文本写作
            7. 子标题编号必须延续父标题编号：如果父标题是"3. 实验内容"，子标题必须从"3.1"开始，不能从"1."开始
            8. 段落之间用空行分隔，每个段落的文字用自然语言写，不要用列表符号（1. 2. 可以用于步骤编号但不要用 - 或 * 做列表）

            """);

        // 模板结构
        prompt.append("====== 模板结构（必须严格遵循）======\n");
        if (templateInfo.sectionHeadings.isEmpty()) {
            prompt.append("""
                默认章节顺序：
                1. 实验目的
                2. 实验原理
                3. 实验环境
                4. 实验步骤
                5. 实验结果
                6. 代码分析
                7. 实验总结
                """);
        } else {
            for (int i = 0; i < templateInfo.sectionHeadings.size(); i++) {
                prompt.append(i + 1).append(". ").append(templateInfo.sectionHeadings.get(i)).append("\n");
            }
        }

        // 占位符
        if (!templateInfo.placeholders.isEmpty()) {
            prompt.append("\n====== 模板中的占位符变量 ======\n");
            for (String ph : templateInfo.placeholders) {
                prompt.append("  ${").append(ph).append("}\n");
            }
        }

        // 实验信息
        prompt.append("\n====== 实验信息 ======\n");
        prompt.append("实验名称：").append(project.getName()).append("\n");
        if (project.getDescription() != null && !project.getDescription().isBlank()) {
            prompt.append("实验描述：").append(project.getDescription()).append("\n");
        }

        // 代码文件
        prompt.append("\n====== 代码文件 ======\n");
        prompt.append(codeContext);

        // 输出要求
        prompt.append("""

            ====== 输出JSON格式（严格） ======
            请输出如下JSON结构，不要包含markdown代码块标记(```)，只输出纯JSON：

            {
              "reportTitle": "实验报告标题",
              "sections": [
                {
                  "title": "章节标题（必须与模板结构中的标题一致）",
                  "content": "该章节的完整内容，可包含多个自然段。\\n\\n用于AI代码分析的内容要引用具体类名和方法。\\n\\n实验步骤要写得详细可操作。"
                }
              ],
              "placeholders": {
                "变量名1": "填充值1",
                "变量名2": "填充值2"
              },
              "umlSuggestion": "如果需要UML图，此处提供PlantUML DSL代码，否则留空字符串"
            }

            重要提醒：
            - sections数组中的title必须与「模板结构」中的章节一一对应
            - content字段中的每个章节至少写3-5段，内容充实
            - 代码分析章节要详细说明类的设计、核心方法的逻辑
            - 不要输出```json```标记，只输出纯JSON
            """);

        try {
            String systemPrompt = "你是一个严格按照模板撰写实验报告的AI。你只输出JSON，不输出任何其他内容。你分析的代码都是真实上传的，你必须基于这些代码撰写报告。";

            // 较长的等待时间，AI需要思考
            String response = ai.chat(systemPrompt, prompt.toString());

            // 清理可能的 markdown 包裹和非JSON前缀
            response = response.trim();
            if (response.startsWith("```")) {
                response = response.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "").trim();
            }
            // 如果JSON前面有解释文字，截取到第一个 {
            int firstBrace = response.indexOf('{');
            if (firstBrace > 0) {
                response = response.substring(firstBrace);
            }
            // 如果JSON后面有尾巴，截取到最后一个 }
            int lastBrace = response.lastIndexOf('}');
            if (lastBrace > 0 && lastBrace < response.length() - 1) {
                response = response.substring(0, lastBrace + 1);
            }

            return response;
        } catch (Exception e) {
            log.error("AI调用失败", e);
            throw new RuntimeException("AI生成内容失败: " + e.getMessage());
        }
    }

    /** 解析AI返回的JSON，带硬防护 */
    @SuppressWarnings("unchecked")
    private List<SectionContent> parseAiJson(String aiRaw, TemplateInfo templateInfo) {
        // 硬防护：如果AI返回的是纯文本（非JSON），绝对不能写入文档
        String trimmed = aiRaw.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("```json")) {
            log.error("AI未返回JSON，拒绝写入文档。原始内容前200字: {}", trimmed.substring(0, Math.min(200, trimmed.length())));
            return List.of(new SectionContent("错误", "AI返回格式异常，请重新生成。不要将AI原始输出写入文档。"));
        }

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            Map<String, Object> root = mapper.readValue(aiRaw, Map.class);

            List<Map<String, Object>> sections = (List<Map<String, Object>>) root.get("sections");
            List<SectionContent> result = new ArrayList<>();
            if (sections != null) {
                for (Map<String, Object> s : sections) {
                    result.add(new SectionContent(
                        String.valueOf(s.getOrDefault("title", "")),
                        String.valueOf(s.getOrDefault("content", ""))
                    ));
                }
            }

            Map<String, Object> placeholders = (Map<String, Object>) root.get("placeholders");
            if (placeholders != null) {
                templateInfo.placeholderValues = new LinkedHashMap<>();
                placeholders.forEach((k, v) ->
                    templateInfo.placeholderValues.put(k, String.valueOf(v)));
            }

            return result;
        } catch (Exception e) {
            log.error("AI JSON解析失败: {}，尝试修复截断的JSON", e.getMessage());
            // 尝试修复：补全缺失的 } ] " 等
            String repaired = trimmed;
            int openBraces = 0, closeBraces = 0;
            int openBrackets = 0, closeBrackets = 0;
            boolean inString = false;
            for (char c : trimmed.toCharArray()) {
                if (c == '"' && !trimmed.substring(0, Math.max(0, trimmed.indexOf(c)+1)).endsWith("\\\"")) inString = !inString;
                if (!inString) {
                    if (c == '{') openBraces++; else if (c == '}') closeBraces++;
                    if (c == '[') openBrackets++; else if (c == ']') closeBrackets++;
                }
            }
            // 补全未闭合的结构
            StringBuilder sb = new StringBuilder(trimmed);
            boolean endsInString = trimmed.length() > 0 && trimmed.charAt(trimmed.length()-1) == '"' && !trimmed.endsWith("\\\"");
            if (endsInString) sb.append('"');  // close unfinished string
            for (int i = 0; i < openBrackets - closeBrackets; i++) sb.append(']');
            for (int i = 0; i < openBraces - closeBraces; i++) sb.append('}');
            repaired = sb.toString();

            // 尝试再次解析修复后的JSON
            try {
                Map<String, Object> root = mapper.readValue(repaired, Map.class);
                List<Map<String, Object>> sections = (List<Map<String, Object>>) root.get("sections");
                List<SectionContent> result = new ArrayList<>();
                if (sections != null) {
                    for (Map<String, Object> s : sections) {
                        result.add(new SectionContent(
                            String.valueOf(s.getOrDefault("title", "")),
                            String.valueOf(s.getOrDefault("content", ""))));
                    }
                }
                log.info("JSON修复成功，恢复了{}个章节", result.size());
                // 标记最后一个章节内容被截断
                if (!result.isEmpty()) {
                    SectionContent last = result.get(result.size() - 1);
                    result.set(result.size() - 1, new SectionContent(last.title(),
                        last.content() + "\n（注意：因响应长度限制，本段后续内容被截断）"));
                }
                return result;
            } catch (Exception ex2) {
                log.error("JSON修复也失败，使用fallback: {}", ex2.getMessage());
                // 最终fallback：把模板章节列表作为基本填充
                List<SectionContent> result = new ArrayList<>();
                for (String h : templateInfo.sectionHeadings) {
                    result.add(new SectionContent(h, "（AI生成失败：响应被截断。请重新生成或减少上传的代码文件数量后重试。）"));
                }
                return result;
            }
        }
    }

    // ================================================================
    //  DOCX OUTPUT
    // ================================================================

    /** 方式1：调用 docx-form-filler Python skill 填充模板 */
    private Path fillTemplate(Project project, Path templatePath, TemplateInfo templateInfo,
            List<SectionContent> sections, List<String> umlImageNames) throws IOException {
        Path outDir = Path.of(outputDir);
        Files.createDirectories(outDir);
        String fileName = project.getName() + "_" +
            LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".docx";
        Path outputPath = outDir.resolve(fileName);

        Map<String, Object> content = new LinkedHashMap<>();

        // Placeholders
        Map<String, String> ph = new LinkedHashMap<>();
        if (templateInfo.placeholderValues != null) ph.putAll(templateInfo.placeholderValues);
        ph.put("date", LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日")));
        ph.put("title", project.getName());
        if (project.getPersonalInfoJson() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> personal = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(project.getPersonalInfoJson(), Map.class);
                ph.putAll(personal);
            } catch (Exception ignored) {}
        }
        content.put("placeholders", ph);

        // Sections
        List<Map<String, String>> secList = new ArrayList<>();
        for (SectionContent sc : sections) {
            Map<String, String> sm = new LinkedHashMap<>();
            sm.put("title", sc.title());
            sm.put("content", sc.content());
            secList.add(sm);
        }
        content.put("sections", secList);
        content.put("umlImages", umlImageNames != null ? umlImageNames : List.of());
        content.put("umlDir", new File(uploadDir, "uml").getAbsolutePath());

        // Write content.json to a temp dir with ASCII-safe path
        Path tempDir = Files.createTempDirectory("labreport_");
        Path tempTemplate = tempDir.resolve("_template.docx");
        Path tempJson = tempDir.resolve("_content.json");
        Path tempOutput = tempDir.resolve("_output.docx");

        // Copy template to temp dir (avoid Chinese path issues with Windows CMD)
        Files.copy(templatePath, tempTemplate, StandardCopyOption.REPLACE_EXISTING);

        // Write content.json
        new com.fasterxml.jackson.databind.ObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValue(tempJson.toFile(), content);

        // Build absolute script path
        Path scriptPath = Path.of("scripts/fill_report_template.py").toAbsolutePath();
        log.info("调用 Python skill: python {} {} {} {}",
            scriptPath, tempTemplate, tempJson, tempOutput);

        ProcessBuilder pb = new ProcessBuilder(
            "python", scriptPath.toString(),
            tempTemplate.toAbsolutePath().toString(),
            tempJson.toAbsolutePath().toString(),
            tempOutput.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);
        try {
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            int exitCode = proc.waitFor();
            log.info("Python skill exit={}, output: {}", exitCode, output);
            if (exitCode != 0) {
                throw new IOException("Python skill 返回非零: " + output);
            }
            // Check output file exists
            if (!Files.exists(tempOutput) || Files.size(tempOutput) < 1000) {
                throw new IOException("Python skill 输出文件为空: " + tempOutput);
            }
            // Move temp output to final path
            Files.move(tempOutput, outputPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Python skill 被中断", e);
        } finally {
            // Clean up temp dir
            try { Files.deleteIfExists(tempTemplate); } catch (Exception ignored) {}
            try { Files.deleteIfExists(tempJson); } catch (Exception ignored) {}
            try { Files.deleteIfExists(tempOutput); } catch (Exception ignored) {}
            try { Files.deleteIfExists(tempDir); } catch (Exception ignored) {}
        }

        log.info("DOCX报告(模板+Python skill)已生成: {}", outputPath);
        return outputPath;
    }

    /** ZIP级 XML 替换 —— 已移至 Python skill，保留仅作 fallback */
    private void xmlReplaceInDocx(Path docxPath, Map<String, String> replacements) throws IOException {
        Path tmp = Path.of(docxPath.toString() + ".tmp");
        try (java.util.zip.ZipFile zin = new java.util.zip.ZipFile(docxPath.toFile());
             java.util.zip.ZipOutputStream zout = new java.util.zip.ZipOutputStream(
                 new java.io.FileOutputStream(tmp.toFile()))) {

            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zin.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                byte[] data = zin.getInputStream(entry).readAllBytes();

                // 只对XML文件做替换
                if (entry.getName().endsWith(".xml") || entry.getName().endsWith(".rels")) {
                    String content = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                    for (Map.Entry<String, String> r : replacements.entrySet()) {
                        content = content.replace(r.getKey(),
                            r.getValue().replace("&", "&amp;").replace("<", "&lt;")
                                .replace(">", "&gt;").replace("\"", "&quot;"));
                    }
                    data = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }

                java.util.zip.ZipEntry outEntry = new java.util.zip.ZipEntry(entry.getName());
                outEntry.setMethod(java.util.zip.ZipEntry.DEFLATED);
                zout.putNextEntry(outEntry);
                zout.write(data);
                zout.closeEntry();
            }
        }
        Files.move(tmp, docxPath, StandardCopyOption.REPLACE_EXISTING);
    }

    /** 替换段落中所有 run 的文字（保留全部格式），只改内容 */
    private void replaceRunText(XWPFParagraph para, String newText) {
        java.util.List<XWPFRun> runs = para.getRuns();
        if (runs.isEmpty()) {
            XWPFRun r = para.createRun();
            r.setText(newText);
            return;
        }
        // 第一个 run 放全部文字（保留其格式）
        runs.get(0).setText(newText, 0);
        // 清空其余 run
        for (int i = 1; i < runs.size(); i++) {
            runs.get(i).setText("", 0);
        }
    }

    /** 方式2：无模板时，从零构建DOCX */
    private Path generateFromScratch(Project project, List<SectionContent> sections,
            List<String> umlImageNames) throws IOException {
        Path outDir = Path.of(outputDir);
        Files.createDirectories(outDir);
        String fileName = project.getName() + "_" +
            LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".docx";
        Path outputPath = outDir.resolve(fileName);

        XWPFDocument doc = new XWPFDocument();

        // 封面
        XWPFParagraph cover = doc.createParagraph();
        cover.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun cr = cover.createRun();
        cr.setText(project.getName());
        cr.setBold(true); cr.setFontSize(36); cr.setFontFamily("宋体");

        doc.createParagraph();
        XWPFParagraph coverSub = doc.createParagraph();
        coverSub.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun csr = coverSub.createRun();
        csr.setText("实验报告"); csr.setFontSize(28); csr.setFontFamily("宋体");

        doc.createParagraph();
        XWPFParagraph coverDate = doc.createParagraph();
        coverDate.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun cdr = coverDate.createRun();
        cdr.setText(LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日")));
        cdr.setFontSize(24); cdr.setFontFamily("宋体");

        // 分页
        XWPFParagraph pb = doc.createParagraph(); pb.setPageBreak(true);

        // 正文
        boolean umlInserted = false;
        for (int i = 0; i < sections.size(); i++) {
            SectionContent sc = sections.get(i);

            XWPFParagraph tp = doc.createParagraph();
            XWPFRun tr = tp.createRun();
            tr.setText((i + 1) + ". " + sc.title);
            tr.setBold(true); tr.setFontSize(28); tr.setFontFamily("宋体");

            String[] paragraphs = sc.content.split("\n\n");
            for (String paraText : paragraphs) {
                String trimmed = paraText.trim();
                if (trimmed.isEmpty()) continue;
                XWPFParagraph cp = doc.createParagraph();
                XWPFRun cpr = cp.createRun();
                cpr.setText(trimmed.replace("\n", " "));
                cpr.setFontSize(24); cpr.setFontFamily("宋体");
            }

            // 在"代码分析"/"代码说明"章节后插入UML
            if (!umlInserted && (sc.title.contains("代码") || sc.title.contains("实现"))
                    && umlImageNames != null && !umlImageNames.isEmpty()) {
                insertUmlImages(doc, umlImageNames);
                umlInserted = true;
            }

            doc.createParagraph();
        }

        // 如果还没插入UML，在最后插入
        if (!umlInserted && umlImageNames != null && !umlImageNames.isEmpty()) {
            insertUmlImages(doc, umlImageNames);
        }

        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
            doc.write(fos);
        }
        doc.close();

        log.info("DOCX报告(从零构建)已生成: {}", outputPath);
        return outputPath;
    }

    private void insertUmlImages(XWPFDocument doc, List<String> umlNames) {
        doc.createParagraph();
        XWPFParagraph t = doc.createParagraph();
        XWPFRun tr = t.createRun();
        tr.setText("系统UML类图："); tr.setBold(true); tr.setFontSize(24); tr.setFontFamily("宋体");

        for (String umlName : umlNames) {
            Path p = Path.of(uploadDir, "uml", umlName);
            if (Files.exists(p)) {
                try {
                    XWPFParagraph ip = doc.createParagraph();
                    ip.setAlignment(ParagraphAlignment.CENTER);
                    XWPFRun ir = ip.createRun();
                    try (FileInputStream fis = new FileInputStream(p.toFile())) {
                        ir.addPicture(fis, XWPFDocument.PICTURE_TYPE_PNG, umlName,
                            Units.toEMU(480), Units.toEMU(360));
                    }
                    XWPFParagraph cap = doc.createParagraph();
                    cap.setAlignment(ParagraphAlignment.CENTER);
                    XWPFRun car = cap.createRun();
                    car.setText("图 " + umlName.replace(".png", ""));
                    car.setFontSize(20); car.setFontFamily("宋体"); car.setItalic(true);
                } catch (Exception e) { log.warn("UML图片插入失败: {}", umlName, e); }
            }
        }
    }

    // ================================================================
    //  HELPERS
    // ================================================================

    private void updateProgress(ReportRecord record, int phase, String name, String detail) {
        record.setProgressPhase(phase);
        record.setPhaseName(name);
        record.setStatus("RUNNING");
        recordMapper.updateById(record);
    }

    // ================================================================
    //  INNER CLASSES
    // ================================================================

    static class TemplateInfo {
        List<String> sectionHeadings = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        Map<String, String> placeholderValues = new LinkedHashMap<>();
        String templateText = "";
    }

    public record SectionContent(String title, String content) {}
}
