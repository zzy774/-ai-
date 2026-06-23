package com.labreport.writer.util;

import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

/**
 * DocxFontUtil - DOCX中文字体处理工具。
 * <p>
 * <b>这是整个系统的关键技术难点。</b>
 * Apache POI 的高级 API（{@code run.setFontFamily("宋体")}）只设置
 * {@code w:ascii} 和 {@code w:hAnsi} 属性，<b>不会</b>设置 {@code w:eastAsia}。
 * Word 使用 {@code w:eastAsia} 来决定 CJK 字符的字体。不设置此属性，
 * 中文字符会渲染为"等线"（DengXian）等回退字体，而非指定的"宋体"。
 * </p>
 *
 * <pre>
 * 使用方式（两步法）：
 *   // 步骤1: poi-tl 填充模板变量
 *   XWPFTemplate.compile(template).render(variables).writeToFile(tempFile);
 *
 *   // 步骤2: 用 XWPF 重新打开，调用本类应用字体
 *   XWPFDocument doc = new XWPFDocument(new FileInputStream(tempFile));
 *   DocxFontUtil.applyDocumentDefaults(doc);
 *   doc.write(new FileOutputStream(outputFile));
 * </pre>
 *
 * @see <a href="https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.wordprocessing.runfonts?view=openxml-2.8.1">OOXML rFonts 规范</a>
 */
public class DocxFontUtil {

    private static final Logger log = LoggerFactory.getLogger(DocxFontUtil.class);

    /** 默认中文字体 */
    public static final String DEFAULT_CN_FONT = "宋体";
    /** 默认英文字体 */
    public static final String DEFAULT_EN_FONT = "Times New Roman";
    /** 默认字号（半磅单位），12pt = 24 */
    public static final int DEFAULT_FONT_SIZE = 24;
    /** 默认行间距（1/240行单位），1.5倍 = 360 */
    public static final int DEFAULT_LINE_SPACING = 360;
    /** 首行缩进（twips），2字符 ≈ 480 */
    public static final int DEFAULT_FIRST_LINE_INDENT = 480;
    /** 段后间距（1/20点），6pt = 120 */
    public static final int DEFAULT_AFTER_SPACING = 120;

    /**
     * 为单个 XWPFRun 设置完整字体属性。
     * <p>
     * 同时设置 ascii/hAnsi（西文）、eastAsia（CJK）、cs（复杂脚本）字体。
     * </p>
     *
     * @param run          XWPF 文本运行
     * @param cnFont       CJK 字体名（e.g. "宋体"）
     * @param enFont       西文字体名（e.g. "Times New Roman"）
     * @param fontSizeHalfPt 字号（半磅），12pt = 24
     */
    public static void setRunFont(XWPFRun run, String cnFont, String enFont, int fontSizeHalfPt) {
        // 使用高级 API 设置基本属性
        run.setFontFamily(enFont != null ? enFont : DEFAULT_EN_FONT);
        run.setFontSize(fontSizeHalfPt);

        // ★★★ 关键: 通过底层 CT 设置 east-Asian 字体 ★★★
        CTRPr rpr = run.getCTR().isSetRPr()
            ? run.getCTR().getRPr()
            : run.getCTR().addNewRPr();

        CTFonts fonts = rpr.sizeOfRFontsArray() > 0
            ? rpr.getRFontsArray(0)
            : rpr.addNewRFonts();

        fonts.setAscii(enFont != null ? enFont : DEFAULT_EN_FONT);
        fonts.setHAnsi(enFont != null ? enFont : DEFAULT_EN_FONT);
        fonts.setEastAsia(cnFont != null ? cnFont : DEFAULT_CN_FONT);
        fonts.setCs(cnFont != null ? cnFont : DEFAULT_CN_FONT);  // 复杂脚本也用中文字体
    }

    /**
     * 设置段落行间距（1.5倍行距）。
     *
     * @param para      XWPF段落
     * @param lineSpacing 行间距（1/240行单位），1.5 = 360
     */
    public static void setLineSpacing(XWPFParagraph para, int lineSpacing) {
        CTPPr ppr = para.getCTP().isSetPPr()
            ? para.getCTP().getPPr()
            : para.getCTP().addNewPPr();

        CTSpacing spacing = ppr.isSetSpacing()
            ? ppr.getSpacing()
            : ppr.addNewSpacing();

        spacing.setLine(new BigInteger(String.valueOf(lineSpacing)));
        spacing.setLineRule(STLineSpacingRule.AUTO);

        // 段落前后间距
        spacing.setBefore(BigInteger.ZERO);
        spacing.setAfter(new BigInteger(String.valueOf(DEFAULT_AFTER_SPACING)));
    }

    /**
     * 设置首行缩进（两个汉字宽度 ≈ 480 twips for 12pt text）。
     *
     * @param para       XWPF段落
     * @param indentTwips 缩进量（twips）
     */
    public static void setFirstLineIndent(XWPFParagraph para, int indentTwips) {
        CTPPr ppr = para.getCTP().isSetPPr()
            ? para.getCTP().getPPr()
            : para.getCTP().addNewPPr();

        CTInd ind = ppr.isSetInd()
            ? ppr.getInd()
            : ppr.addNewInd();

        ind.setFirstLine(new BigInteger(String.valueOf(indentTwips)));
    }

    /**
     * 对整个文档应用默认中文字体样式。
     * <p>
     * 遍历所有段落、表格、页眉页脚中的 run，确保每个
     * 包含 CJK 字符的 run 都设置了 eastAsia 字体。
     * </p>
     *
     * @param doc XWPF文档对象
     */
    public static void applyDocumentDefaults(XWPFDocument doc) {
        applyDocumentDefaults(doc, DEFAULT_CN_FONT, DEFAULT_EN_FONT,
            DEFAULT_FONT_SIZE, DEFAULT_LINE_SPACING);
    }

    /**
     * 对整个文档应用自定义字体样式。
     *
     * @param doc           XWPF文档对象
     * @param cnFont        CJK字体名
     * @param enFont        西文字体名
     * @param fontSizeHalfPt 字号（半磅）
     * @param lineSpacing   行间距
     */
    public static void applyDocumentDefaults(XWPFDocument doc,
            String cnFont, String enFont, int fontSizeHalfPt, int lineSpacing) {
        int runCount = 0;
        int paraCount = 0;

        // ---- 处理正文段落 ----
        for (XWPFParagraph para : doc.getParagraphs()) {
            paraCount++;
            setLineSpacing(para, lineSpacing);

            for (XWPFRun run : para.getRuns()) {
                setRunFont(run, cnFont, enFont, fontSizeHalfPt);
                runCount++;
            }
        }

        // ---- 处理表格 ----
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph para : cell.getParagraphs()) {
                        paraCount++;
                        setLineSpacing(para, lineSpacing);

                        for (XWPFRun run : para.getRuns()) {
                            setRunFont(run, cnFont, enFont, fontSizeHalfPt);
                            runCount++;
                        }
                    }
                }
            }
        }

        // ---- 处理页眉页脚 ----
        try {
            XWPFHeaderFooterPolicy hfPolicy = doc.getHeaderFooterPolicy();
            if (hfPolicy != null) {
                // 处理所有类型的页眉
                applyToHeaderFooter(hfPolicy.getDefaultHeader(), cnFont, enFont, fontSizeHalfPt);
                applyToHeaderFooter(hfPolicy.getFirstPageHeader(), cnFont, enFont, fontSizeHalfPt);
                applyToHeaderFooter(hfPolicy.getEvenPageHeader(), cnFont, enFont, fontSizeHalfPt);
                applyToHeaderFooter(hfPolicy.getOddPageHeader(), cnFont, enFont, fontSizeHalfPt);
                // 处理所有类型的页脚
                applyToHeaderFooter(hfPolicy.getDefaultFooter(), cnFont, enFont, fontSizeHalfPt);
                applyToHeaderFooter(hfPolicy.getFirstPageFooter(), cnFont, enFont, fontSizeHalfPt);
                applyToHeaderFooter(hfPolicy.getEvenPageFooter(), cnFont, enFont, fontSizeHalfPt);
                applyToHeaderFooter(hfPolicy.getOddPageFooter(), cnFont, enFont, fontSizeHalfPt);
            }
        } catch (Exception e) {
            log.debug("处理页眉页脚时出错（可能文档不含页眉页脚）: {}", e.getMessage());
        }

        log.info("文档字体已应用: {} 段落, {} 文本片段, 字体={}, 字号={}pt, 行距={}倍",
            paraCount, runCount, cnFont, fontSizeHalfPt / 2,
            String.format("%.1f", lineSpacing / 240.0));
    }

    /**
     * 对页眉/页脚应用字体设置。
     */
    private static void applyToHeaderFooter(XWPFHeaderFooter headerFooter,
            String cnFont, String enFont, int fontSizeHalfPt) {
        if (headerFooter == null) return;
        for (XWPFParagraph para : headerFooter.getParagraphs()) {
            for (XWPFRun run : para.getRuns()) {
                setRunFont(run, cnFont, enFont, fontSizeHalfPt);
            }
        }
        // 处理页眉页脚中的表格
        for (XWPFTable table : headerFooter.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph para : cell.getParagraphs()) {
                        for (XWPFRun run : para.getRuns()) {
                            setRunFont(run, cnFont, enFont, fontSizeHalfPt);
                        }
                    }
                }
            }
        }
    }

    /**
     * 检查文本是否包含CJK字符。
     */
    public static boolean containsCJK(String text) {
        if (text == null) return false;
        return text.codePoints().anyMatch(cp -> {
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            return script == Character.UnicodeScript.HAN;
        });
    }

    /**
     * 验证文档中所有包含中文的run是否都设置了eastAsia字体。
     *
     * @param doc XWPF文档
     * @return 验证结果（问题列表，空列表表示全部通过）
     */
    public static java.util.List<String> verifyFonts(XWPFDocument doc) {
        java.util.List<String> issues = new java.util.ArrayList<>();

        for (XWPFParagraph para : doc.getParagraphs()) {
            for (XWPFRun run : para.getRuns()) {
                String text = run.text();
                if (text != null && containsCJK(text)) {
                    CTRPr rpr = run.getCTR().getRPr();
                    if (rpr == null || rpr.sizeOfRFontsArray() == 0) {
                        issues.add("缺失字体属性: \"" + truncateText(text, 30) + "\"");
                        continue;
                    }
                    String eastAsia = rpr.getRFontsArray(0).getEastAsia();
                    if (eastAsia == null || eastAsia.isEmpty()) {
                        issues.add("缺失 w:eastAsia: \"" + truncateText(text, 30) + "\"");
                    }
                }
            }
        }

        return issues;
    }

    private static String truncateText(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    // ---- main() 用于独立测试 ----

    public static void main(String[] args) throws Exception {
        // 创建一个测试DOCX
        XWPFDocument doc = new XWPFDocument();

        // 添加标题
        XWPFParagraph titlePara = doc.createParagraph();
        XWPFRun titleRun = titlePara.createRun();
        titleRun.setText("测试文档 - 中文字体验证");
        titleRun.setBold(true);

        // 添加正文段落
        XWPFParagraph para = doc.createParagraph();
        XWPFRun run = para.createRun();
        run.setText("这是一段中文测试文字，用于验证宋体是否正确应用。"
            + "Apache POI 的高级 API 默认不会设置 w:eastAsia 属性。"
            + "通过 DocxFontUtil 可以解决这个问题。");

        // 应用字体
        applyDocumentDefaults(doc);

        // 验证
        java.util.List<String> issues = verifyFonts(doc);
        if (issues.isEmpty()) {
            System.out.println("✓ 字体验证通过！所有中文文本都已正确设置 eastAsia 字体。");
        } else {
            System.out.println("✗ 发现 " + issues.size() + " 个字体问题:");
            issues.forEach(i -> System.out.println("  - " + i));
        }

        // 保存测试文件
        java.nio.file.Path output = java.nio.file.Path.of(
            System.getProperty("user.home"), ".labreport", "test-font.docx");
        java.nio.file.Files.createDirectories(output.getParent());
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(output.toFile())) {
            doc.write(fos);
        }
        System.out.println("测试文件已保存: " + output);
        System.out.println("请用 Microsoft Word 打开检查中文字体是否为宋体。");
    }
}
