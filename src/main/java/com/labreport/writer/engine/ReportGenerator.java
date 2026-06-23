package com.labreport.writer.engine;

import com.labreport.writer.model.*;
import com.labreport.writer.util.DocxFontUtil;
import com.labreport.writer.util.FileClassifier;
import com.labreport.writer.util.RuntimeProber;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ReportGenerator - 报告生成核心编排器。
 * <p>
 * 实现完整的7阶段报告生成流水线：
 * </p>
 * <pre>
 *   [1.文件分类] → [2.需求提取] → [3.模板/样例分析] → [4.证据模式决策]
 *   → [5.生成草稿计划] → [6.生成报告] → [7.验证检查] → 输出DOCX
 * </pre>
 *
 * <p>
 * 所有耗时操作应在后台线程中执行，通过 {@link ProgressListener} 回调报告进度。
 * </p>
 */
public class ReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerator.class);

    // ---- 引擎组件 ----
    private final TemplateEngine templateEngine;
    private final UmlGenerator umlGenerator;
    private final CodeAnalyzer codeAnalyzer;
    private final RuntimeProber runtimeProber;

    // ---- 监听器 ----
    private ProgressListener listener;

    /** 进度回调接口 */
    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int phase, int totalPhases, String phaseName, String detail);
    }

    public ReportGenerator() {
        this.templateEngine = new TemplateEngine();
        this.umlGenerator = new UmlGenerator();
        this.codeAnalyzer = new CodeAnalyzer();
        this.runtimeProber = new RuntimeProber();
        UmlGenerator.prewarm(); // 后台预热PlantUML
    }

    public void setProgressListener(ProgressListener listener) {
        this.listener = listener;
    }

    // ---- 主流程 ----

    /**
     * 执行完整的7阶段报告生成流程。
     *
     * @param project 项目（含所有输入材料、模板、配置）
     * @return 生成的DOCX文件路径
     * @throws Exception 生成失败
     */
    public Path generate(Project project) throws Exception {
        log.info("========================================");
        log.info("  开始生成实验报告: {}", project.getName());
        log.info("========================================");

        // 验证项目基本条件
        Project.ValidationResult validation = project.validate();
        if (!validation.valid()) {
            throw new IllegalStateException("项目配置不完整:\n" + validation.formatIssues());
        }

        // ====== 阶段1: 文件分类 ======
        reportProgress(1, "文件分类", "正在扫描输入文件夹...");
        Path inputDir = project.getInputDirPath();
        FileClassifier.ClassificationResult classification =
            FileClassifier.classify(inputDir);
        log.info("阶段1完成: {}", classification.summary());

        // ====== 阶段2: 需求提取 ======
        reportProgress(2, "需求提取", "正在分析实验任务书...");
        ExperimentTask task = project.getTask();
        if (task == null || !task.isValid()) {
            throw new IllegalStateException("未提供有效的实验任务书");
        }

        // 从分类结果中读取任务书内容
        List<Path> taskFiles = classification.getFiles(
            FileClassifier.StandardFolder.TASK_BOOK);
        for (Path f : taskFiles) {
            if (!taskFiles.isEmpty()) {
                try {
                    String content = Files.readString(f);
                    // 如果任务内容为空，用文件内容填充
                    if (task.getRawTaskText().isBlank()) {
                        task.setRawTaskText(content);
                    }
                } catch (IOException e) {
                    log.warn("无法读取任务书文件: {}", f, e);
                }
            }
        }
        log.info("阶段2完成: 标题={}, 步骤数={}",
            task.getTitle(), task.getProcedureSteps().size());

        // ====== 阶段3: 模板/样例分析 ======
        reportProgress(3, "模板分析", "正在分析模板和样例...");
        ReportTemplate template = project.getTemplate();

        // 如果有模板文件，提取变量
        if (template.isLoaded()) {
            Path templatePath = Path.of(template.getFilePath());
            if (Files.exists(templatePath)) {
                List<String> vars = templateEngine.extractVariables(templatePath);
                template.setVariables(vars);
                log.info("模板变量: {}", vars);
            }
        }

        // 读取个人信息
        if (classification.hasFiles(FileClassifier.StandardFolder.PERSONAL_INFO)) {
            Path personalInfoFile = classification.getFiles(
                FileClassifier.StandardFolder.PERSONAL_INFO).get(0);
            // 可以在这里解析个人信息
            log.info("个人信息文件: {}", personalInfoFile.getFileName());
        }

        log.info("阶段3完成: 模板变量数={}", template.getVariables().size());

        // ====== 阶段4: 证据模式决策 ======
        reportProgress(4, "证据决策", "正在决定各章节数据来源...");
        EvidenceManager evidenceMgr = new EvidenceManager(runtimeProber, classification);
        Map<String, EvidenceManager.SectionEvidence> evidencePlan =
            evidenceMgr.planEvidence(task);
        log.info("阶段4完成: {}", evidenceMgr.generateProvenanceSummary(evidencePlan));

        // ====== 阶段5: 生成草稿计划 ======
        reportProgress(5, "草稿计划", "正在规划报告结构...");
        ReportPlan plan = buildReportPlan(task, template, evidencePlan, project.getConfig());
        log.info("阶段5完成: {} 个章节", plan.sections().size());

        // ====== 阶段6: 生成报告 ======
        reportProgress(6, "生成报告", "正在生成DOCX文档...");
        Path outputPath = generateReportDoc(project, plan, classification);
        project.addOutput(outputPath.toString());
        log.info("阶段6完成: {}", outputPath);

        // ====== 阶段7: 验证检查 ======
        if (project.getConfig().enableValidation()) {
            reportProgress(7, "验证检查", "正在验证报告质量...");
            ValidationEngine validationEngine = new ValidationEngine();
            ValidationEngine.ValidationReport vr =
                validationEngine.validate(outputPath);
            log.info("阶段7完成: {}", vr.passed ? "✓ 全部通过" : "✗ 发现问题");

            if (!vr.passed) {
                log.warn(vr.formatReport());
            }
        }

        reportProgress(7, "完成", "报告生成完毕！");
        log.info("========================================");
        log.info("  报告生成完成: {}", outputPath);
        log.info("========================================");

        return outputPath;
    }

    // ---- 阶段5: 构建报告计划 ----

    private record ReportPlan(
        List<String> sections,
        Map<String, EvidenceManager.SectionEvidence> evidence,
        ReportConfig config
    ) {}

    private ReportPlan buildReportPlan(ExperimentTask task, ReportTemplate template,
            Map<String, EvidenceManager.SectionEvidence> evidencePlan,
            ReportConfig config) {
        List<String> sections = new ArrayList<>();

        // 优先使用模板的章节结构
        if (!template.getSections().isEmpty()) {
            sections.addAll(template.getSections());
        } else {
            // 默认结构
            sections.add("封面");
            sections.add("实验目的");
            sections.add("实验原理");
            sections.add("实验环境");
            sections.add("实验步骤");
            sections.add("实验结果");
            sections.add("数据分析");
            sections.add("实验总结");
        }

        return new ReportPlan(sections, evidencePlan, config);
    }

    // ---- 阶段6: 生成DOCX文档 ----

    private Path generateReportDoc(Project project, ReportPlan plan,
            FileClassifier.ClassificationResult classification)
            throws Exception {

        ReportConfig config = project.getConfig();
        ExperimentTask task = project.getTask();
        ReportTemplate template = project.getTemplate();

        // 生成默认输出路径
        String outputDirStr = config.outputDir();
        if (outputDirStr == null || outputDirStr.isBlank()) {
            outputDirStr = System.getProperty("user.home") + "\\实验报告输出";
        }
        Path outputDir = Path.of(outputDirStr);
        Files.createDirectories(outputDir);

        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = project.getName() + "_" + timestamp + ".docx";
        Path outputPath = outputDir.resolve(fileName);

        // ---- 如果有模板，使用模板生成 ----
        if (template.isLoaded() && Files.exists(Path.of(template.getFilePath()))) {
            // 准备变量值
            Map<String, Object> vars = prepareTemplateVariables(
                project, plan, classification);

            templateEngine.fillTemplate(
                Path.of(template.getFilePath()), vars, outputPath);
        } else {
            // ---- 没有模板，从零构建 ----
            generateFromScratch(project, plan, outputPath);
        }

        return outputPath;
    }

    /** 准备模板变量值 */
    private Map<String, Object> prepareTemplateVariables(Project project,
            ReportPlan plan, FileClassifier.ClassificationResult classification) {
        Map<String, Object> vars = new LinkedHashMap<>();
        ReportConfig config = project.getConfig();
        ExperimentTask task = project.getTask();
        ReportTemplate template = project.getTemplate();

        // 个人信息
        Map<String, String> personalInfo = project.getPersonalInfo();
        personalInfo.forEach(vars::put);

        // 实验基本信息
        vars.put("title", task.getTitle());
        vars.put("objectives", task.getObjectives());
        vars.put("principles", task.getPrinciples());
        vars.put("steps", String.join("\n", task.getProcedureSteps()));
        vars.put("requiredAnalysis", task.getRequiredAnalysis());
        vars.put("date", LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy年M月d日")));

        // 模板中已配置的变量值
        template.getVariableValues().forEach(vars::put);

        return vars;
    }

    /** 从零构建DOCX */
    private void generateFromScratch(Project project, ReportPlan plan,
            Path outputPath) throws IOException {
        ReportConfig config = project.getConfig();
        ExperimentTask task = project.getTask();

        XWPFDocument doc = new XWPFDocument();

        // ---- 封面 ----
        XWPFParagraph titlePara = doc.createParagraph();
        titlePara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        XWPFRun titleRun = titlePara.createRun();
        titleRun.setText(task.getTitle());
        titleRun.setBold(true);
        titleRun.setFontSize(36); // 18pt 半磅

        // 空行
        doc.createParagraph();

        // 个人信息
        Map<String, String> info = project.getPersonalInfo();
        for (Map.Entry<String, String> entry : info.entrySet()) {
            XWPFParagraph p = doc.createParagraph();
            p.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
            XWPFRun r = p.createRun();
            r.setText(entry.getKey() + ": " +
                (entry.getValue().isBlank() ? "【" + entry.getKey() + "】" : entry.getValue()));
            r.setFontSize(28); // 14pt
        }

        // 分页
        XWPFParagraph pageBreak = doc.createParagraph();
        pageBreak.setPageBreak(true);

        // ---- 各章节 ----
        for (String section : plan.sections()) {
            // 章节标题
            XWPFParagraph sectionPara = doc.createParagraph();
            XWPFRun sectionRun = sectionPara.createRun();
            sectionRun.setText(section);
            sectionRun.setBold(true);
            sectionRun.setFontSize(32); // 16pt

            // 章节内容（占位）
            XWPFParagraph contentPara = doc.createParagraph();
            XWPFRun contentRun = contentPara.createRun();
            contentRun.setText("（此处将由系统自动生成" + section + "内容）");
            contentRun.setFontSize(24); // 12pt

            doc.createParagraph(); // 空行
        }

        // 应用中文字体
        DocxFontUtil.applyDocumentDefaults(doc, config.cnFont(), config.enFont(),
            config.fontSizeHalfPt(), config.lineSpacing());

        // 保存
        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
            doc.write(fos);
        }
        doc.close();
    }

    // ---- 进度报告 ----

    private void reportProgress(int phase, String phaseName, String detail) {
        if (listener != null) {
            listener.onProgress(phase, 7, phaseName, detail);
        }
    }

    // ---- main() 独立测试 ----

    public static void main(String[] args) throws Exception {
        // 构建测试项目
        Project project = new Project("软件测试实验");
        project.getTask()
            .setTitle("软件测试实验一：JUnit单元测试")
            .setObjectives("熟悉JUnit框架的基本使用方法")
            .setPrinciples("JUnit是Java最流行的单元测试框架...")
            .addProcedureStep("1. 安装JUnit依赖")
            .addProcedureStep("2. 编写测试类")
            .addProcedureStep("3. 运行测试并查看结果")
            .addRequiredOutput("测试代码截图")
            .addRequiredOutput("测试运行结果表格");

        project.getPersonalInfo().put("姓名", "测试用户");
        project.getPersonalInfo().put("学号", "2024001");
        project.getPersonalInfo().put("班级", "软件工程1班");

        project.setConfig(ReportConfig.defaults());

        // 设置输出目录
        Path outputDir = Path.of(System.getProperty("user.home"), ".labreport", "test-output");
        project.setConfig(new ReportConfig("宋体", "Times New Roman", 24, 360,
            "docx", "auto", true, true, outputDir.toString()));

        // 运行生成
        ReportGenerator generator = new ReportGenerator();
        generator.setProgressListener((phase, total, name, detail) ->
            System.out.printf("[%d/%d] %s: %s%n", phase, total, name, detail));

        Path output = generator.generate(project);
        System.out.println("报告已生成: " + output);
        System.out.println("请用Word打开查看！");
    }
}
