package com.labreport.writer.engine;

import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.config.ConfigureBuilder;
import com.labreport.writer.util.DocxFontUtil;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * TemplateEngine - DOCX模板引擎。
 * <p>
 * 封装 poi-tl 进行模板变量填充，然后用 XWPF 后处理修复中文字体。
 * 采用两步法：poi-tl填充 → XWPF字体修复。
 * </p>
 *
 * <pre>
 * 使用方式:
 *   TemplateEngine engine = new TemplateEngine();
 *   Map<String, Object> vars = Map.of("name", "张三", "title", "实验一");
 *   engine.fillTemplate(Path.of("template.docx"), vars, Path.of("output.docx"));
 * </pre>
 */
public class TemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(TemplateEngine.class);

    /** poi-tl 配置 */
    private final String gramerPrefix;
    private final String gramerSuffix;

    public TemplateEngine() {
        this("${", "}");
    }

    public TemplateEngine(String gramerPrefix, String gramerSuffix) {
        this.gramerPrefix = gramerPrefix;
        this.gramerSuffix = gramerSuffix;
    }

    /**
     * 从模板文件提取变量列表。
     *
     * @param templatePath DOCX模板文件路径
     * @return 变量名列表
     * @throws IOException 读取失败
     */
    public List<String> extractVariables(Path templatePath) throws IOException {
        List<String> variables = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(templatePath))) {
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                extractPlaceholders(text, variables);
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph para : cell.getParagraphs()) {
                            extractPlaceholders(para.getText(), variables);
                        }
                    }
                }
            }
        }

        // 去重
        List<String> unique = new ArrayList<>(new LinkedHashSet<>(variables));
        log.info("从模板提取了 {} 个变量 (去重后 {})", variables.size(), unique.size());
        return unique;
    }

    /** 从文本中提取 ${...} 形式的占位符 */
    private void extractPlaceholders(String text, List<String> result) {
        if (text == null) return;
        int idx = 0;
        while (idx < text.length()) {
            int start = text.indexOf(gramerPrefix, idx);
            if (start < 0) break;
            int end = text.indexOf(gramerSuffix, start + gramerPrefix.length());
            if (end < 0) break;
            String var = text.substring(start + gramerPrefix.length(), end).trim();
            if (!var.isEmpty()) {
                result.add(var);
            }
            idx = end + gramerSuffix.length();
        }
    }

    /**
     * 填充模板并生成最终DOCX文档（两步法）。
     *
     * <pre>
     * 步骤1: poi-tl填入变量值
     * 步骤2: XWPF后处理修复中文字体（设置w:eastAsia）
     * </pre>
     *
     * @param templatePath 模板文件路径
     * @param variables    变量名→值映射
     * @param outputPath   输出文件路径
     * @return 输出文件路径
     * @throws IOException 处理失败
     */
    public Path fillTemplate(Path templatePath, Map<String, Object> variables,
            Path outputPath) throws IOException {
        Files.createDirectories(outputPath.getParent());

        // Step 1: poi-tl 渲染
        Path tempFile = Files.createTempFile("report_tmp_", ".docx");
        try {
            ConfigureBuilder builder = com.deepoove.poi.config.Configure.builder();
            builder.buildGramer(gramerPrefix, gramerSuffix);

            try (InputStream fis = Files.newInputStream(templatePath)) {
                XWPFTemplate xwpfTemplate = XWPFTemplate.compile(fis, builder.build());
                xwpfTemplate.render(variables);
                xwpfTemplate.writeToFile(tempFile.toAbsolutePath().toString());
                xwpfTemplate.close();
            }
            log.info("poi-tl 模板填充完成: {} 个变量", variables.size());

            // Step 2: XWPF 后处理修复中文字体
            applyChineseFontFix(tempFile, outputPath);

        } finally {
            Files.deleteIfExists(tempFile);
        }

        log.info("模板处理完成: {}", outputPath);
        return outputPath;
    }

    /**
     * 后处理：打开临时文件，修复中文字体，保存到输出路径。
     */
    private void applyChineseFontFix(Path inputPath, Path outputPath) throws IOException {
        try (FileInputStream fis = new FileInputStream(inputPath.toFile());
             FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {

            XWPFDocument doc = new XWPFDocument(fis);
            DocxFontUtil.applyDocumentDefaults(doc);
            doc.write(fos);
            doc.close();

            log.debug("中文字体后处理完成");
        }
    }

    /**
     * 创建空白DOCX文档，用于没有模板的情况。
     */
    public XWPFDocument createEmptyDocument() {
        return new XWPFDocument();
    }

    /**
     * 向文档添加带格式的段落。
     */
    public XWPFParagraph addParagraph(XWPFDocument doc, String text,
            String cnFont, String enFont, int fontSize, boolean bold) {
        XWPFParagraph para = doc.createParagraph();
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setBold(bold);
        DocxFontUtil.setRunFont(run, cnFont, enFont, fontSize);
        DocxFontUtil.setLineSpacing(para, DocxFontUtil.DEFAULT_LINE_SPACING);
        return para;
    }

    /**
     * 向文档插入图片（UML图等）。
     */
    public void insertImage(XWPFParagraph para, XWPFRun run,
            byte[] imageBytes, String format, int widthEmu, int heightEmu) throws Exception {
        int picType = switch (format.toLowerCase()) {
            case "png" -> XWPFDocument.PICTURE_TYPE_PNG;
            case "jpg", "jpeg" -> XWPFDocument.PICTURE_TYPE_JPEG;
            case "gif" -> XWPFDocument.PICTURE_TYPE_GIF;
            default -> XWPFDocument.PICTURE_TYPE_PNG;
        };

        run.addPicture(
            new ByteArrayInputStream(imageBytes),
            picType,
            format,
            widthEmu,
            heightEmu
        );
    }

    // ---- main() 独立测试 ----

    public static void main(String[] args) throws Exception {
        TemplateEngine engine = new TemplateEngine();

        // 创建测试模板
        XWPFDocument doc = new XWPFDocument();
        XWPFParagraph para = doc.createParagraph();
        XWPFRun run = para.createRun();
        run.setText("实验名称：${title}");
        para = doc.createParagraph();
        run = para.createRun();
        run.setText("姓名：${name}  学号：${studentId}");
        para = doc.createParagraph();
        run = para.createRun();
        run.setText("实验日期：${date}");

        Path templatePath = Path.of(System.getProperty("user.home"),
            ".labreport", "test-template.docx");
        Files.createDirectories(templatePath.getParent());
        try (FileOutputStream fos = new FileOutputStream(templatePath.toFile())) {
            doc.write(fos);
        }
        System.out.println("测试模板已创建: " + templatePath);

        // 提取变量
        List<String> vars = engine.extractVariables(templatePath);
        System.out.println("发现的变量: " + vars);

        // 填充
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", "软件测试实验一");
        values.put("name", "张三");
        values.put("studentId", "2024001");
        values.put("date", "2026年6月22日");

        Path output = Path.of(System.getProperty("user.home"),
            ".labreport", "test-output.docx");
        engine.fillTemplate(templatePath, values, output);
        System.out.println("报告已生成: " + output);
        System.out.println("请用Word打开检查变量是否正确填充，中文是否显示为宋体。");
    }
}
