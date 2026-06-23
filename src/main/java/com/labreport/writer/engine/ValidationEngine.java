package com.labreport.writer.engine;

import com.labreport.writer.util.DocxFontUtil;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ValidationEngine - 报告后验证引擎。
 * <p>
 * 生成DOCX后进行4项检查：
 * </p>
 * <ol>
 *   <li>结构完整性：段落数、表格数、图片数、占位符数</li>
 *   <li>中文字体：所有CJK文本是否设置了w:eastAsia</li>
 *   <li>LaTeX残留：是否有未转换的$$或\begin等LaTeX语法</li>
 *   <li>基本信息：是否有未填的占位符</li>
 * </ol>
 */
public class ValidationEngine {

    private static final Logger log = LoggerFactory.getLogger(ValidationEngine.class);

    /** 验证报告 */
    public static class ValidationReport {
        public boolean passed;
        public final List<String> issues = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
        public final Map<String, Object> stats = new LinkedHashMap<>();

        /** 段落数 */
        public int paragraphCount;
        /** 表格数 */
        public int tableCount;
        /** 图片数 */
        public int imageCount;
        /** 占位符数 */
        public int placeholderCount;

        public void addIssue(String issue) { issues.add(issue); }
        public void addWarning(String warning) { warnings.add(warning); }

        public String formatReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("========== 验证报告 ==========\n");
            sb.append(String.format("状态: %s\n", passed ? "✓ 通过" : "✗ 未通过"));
            sb.append(String.format("段落: %d, 表格: %d, 图片: %d, 占位符: %d\n",
                paragraphCount, tableCount, imageCount, placeholderCount));
            stats.forEach((k, v) ->
                sb.append(String.format("  %s: %s\n", k, v)));

            if (!issues.isEmpty()) {
                sb.append("\n--- 问题 (").append(issues.size()).append(") ---\n");
                issues.forEach(i -> sb.append("  ✗ ").append(i).append("\n"));
            }
            if (!warnings.isEmpty()) {
                sb.append("\n--- 警告 (").append(warnings.size()).append(") ---\n");
                warnings.forEach(w -> sb.append("  ⚠ ").append(w).append("\n"));
            }
            sb.append("===============================\n");
            return sb.toString();
        }
    }

    /**
     * 完整验证生成的DOCX报告。
     *
     * @param reportPath 生成的DOCX文件路径
     * @return 验证报告
     */
    public ValidationReport validate(Path reportPath) throws IOException {
        log.info("开始验证报告: {}", reportPath);
        ValidationReport report = new ValidationReport();

        try (InputStream is = Files.newInputStream(reportPath)) {
            XWPFDocument doc = new XWPFDocument(is);

            // ---- 检查1: 结构完整性 ----
            checkStructure(doc, report);

            // ---- 检查2: 中文字体 ----
            checkChineseFont(doc, report);

            // ---- 检查3: LaTeX残留 ----
            checkLatexArtifacts(reportPath, report);

            // ---- 检查4: 基本信息 ----
            checkPlaceholders(doc, report);

            doc.close();
        }

        // 综合判定
        report.passed = report.issues.isEmpty();
        log.info("验证完成: {}", report.passed ? "通过" : "未通过");
        if (!report.passed || !report.warnings.isEmpty()) {
            log.info(report.formatReport());
        }

        return report;
    }

    /** 检查文档结构 */
    private void checkStructure(XWPFDocument doc, ValidationReport report) {
        report.paragraphCount = doc.getParagraphs().size();
        report.tableCount = doc.getTables().size();

        // 统计图片（通过检查zip内的word/media/）
        // 先粗略统计：检查XML中的图片引用
        int imgCount = 0;
        for (XWPFParagraph para : doc.getParagraphs()) {
            for (XWPFRun run : para.getRuns()) {
                if (run.getEmbeddedPictures() != null && !run.getEmbeddedPictures().isEmpty()) {
                    imgCount += run.getEmbeddedPictures().size();
                }
            }
        }
        report.imageCount = imgCount;

        // 基本检查
        if (report.paragraphCount < 5) {
            report.addWarning("文档段落数较少（" + report.paragraphCount + "），报告可能不完整");
        }
        if (report.tableCount == 0) {
            report.addWarning("文档中无表格，实验报告通常需要数据表格");
        }

        report.stats.put("总段落数", report.paragraphCount);
        report.stats.put("总表格数", report.tableCount);
        report.stats.put("嵌入图片数", report.imageCount);
    }

    /** 检查中文字体 */
    private void checkChineseFont(XWPFDocument doc, ValidationReport report) {
        List<String> fontIssues = DocxFontUtil.verifyFonts(doc);
        if (!fontIssues.isEmpty()) {
            report.addIssue("发现 " + fontIssues.size() + " 处中文字体未正确设置(w:eastAsia)");
            if (fontIssues.size() <= 5) {
                fontIssues.forEach(report::addIssue);
            }
        }
        report.stats.put("字体问题数", fontIssues.size());
    }

    /** 检查LaTeX残留（在zip文件级别检查） */
    private void checkLatexArtifacts(Path reportPath, ValidationReport report) {
        try (ZipFile zip = new ZipFile(reportPath.toFile())) {
            ZipEntry documentXml = zip.getEntry("word/document.xml");
            if (documentXml != null) {
                String content = new String(
                    zip.getInputStream(documentXml).readAllBytes(), "UTF-8");

                // 检查LaTeX标记
                String[] patterns = {
                    "$$", "\\[", "\\]", "\\(", "\\)",
                    "\\frac", "\\theta", "\\lambda", "\\sqrt",
                    "\\begin{equation}", "\\begin{align}",
                    "\\sum", "\\int", "\\alpha", "\\beta"
                };
                List<String> found = new ArrayList<>();
                for (String p : patterns) {
                    if (content.contains(p)) {
                        found.add(p);
                    }
                }
                if (!found.isEmpty()) {
                    report.addWarning("发现" + found.size() + "种LaTeX语法残留: "
                        + String.join(", ", found));
                }
                report.stats.put("LaTeX残留类型", found.size());
            }
        } catch (Exception e) {
            log.warn("无法检查LaTeX残留: {}", e.getMessage());
        }
    }

    /** 检查未填的占位符 */
    private void checkPlaceholders(XWPFDocument doc, ValidationReport report) {
        int placeholderCount = 0;
        List<String> placeholders = new ArrayList<>();

        for (XWPFParagraph para : doc.getParagraphs()) {
            String text = para.getText();
            // 匹配中文占位符 【...】
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("【[^】]+】").matcher(text);
            while (m.find()) {
                placeholderCount++;
                String ph = m.group();
                if (!placeholders.contains(ph) && placeholders.size() < 10) {
                    placeholders.add(ph);
                }
            }
            // 匹配 ${...} 未替换的变量
            java.util.regex.Matcher m2 = java.util.regex.Pattern
                .compile("\\$\\{[^}]+\\}").matcher(text);
            while (m2.find()) {
                placeholderCount++;
                String ph = m2.group();
                if (!placeholders.contains(ph) && placeholders.size() < 10) {
                    placeholders.add(ph);
                }
            }
        }

        report.placeholderCount = placeholderCount;
        report.stats.put("剩余占位符数", placeholderCount);

        if (!placeholders.isEmpty()) {
            report.addWarning("发现 " + placeholderCount + " 个未填充的占位符: "
                + String.join(", ", placeholders));
        }
    }

    // ---- main() 独立测试 ----

    public static void main(String[] args) throws Exception {
        // 测试验证之前生成的DOCX
        Path testDoc = Path.of(System.getProperty("user.home"),
            ".labreport", "test-output.docx");
        if (Files.exists(testDoc)) {
            ValidationEngine engine = new ValidationEngine();
            ValidationReport report = engine.validate(testDoc);
            System.out.println(report.formatReport());
        } else {
            System.out.println("请先运行TemplateEngine生成测试文档");
        }
    }
}
