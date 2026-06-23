package com.labreport.writer.engine;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * UmlGenerator - PlantUML图生成器。
 * <p>
 * 将 PlantUML DSL 文本渲染为 PNG/SVG 图片。
 * PlantUML 嵌入在桌面应用中，无需外部服务。
 * </p>
 *
 * <pre>
 * 使用方式:
 *   UmlGenerator gen = new UmlGenerator();
 *   Path png = gen.generateToFile("""
 *     @startuml
 *     class User {
 *       -id: Long
 *       -name: String
 *       +getId(): Long
 *     }
 *     @enduml
 *     """, Path.of("output.png"));
 * </pre>
 */
public class UmlGenerator {

    private static final Logger log = LoggerFactory.getLogger(UmlGenerator.class);

    /** 是否已预热 */
    private static volatile boolean prewarmed = false;

    /**
     * 预热 PlantUML 引擎。
     * 在应用启动时调用，避免首次生成图时卡顿。
     */
    public static void prewarm() {
        if (prewarmed) return;
        new Thread(() -> {
            try {
                log.info("预热 PlantUML 引擎...");
                String testDiagram = "@startuml\nclass Test {}\n@enduml";
                SourceStringReader reader = new SourceStringReader(testDiagram);
                reader.outputImage(new ByteArrayOutputStream(),
                    new FileFormatOption(FileFormat.PNG));
                prewarmed = true;
                log.info("PlantUML 引擎预热完成");
            } catch (Exception e) {
                log.warn("PlantUML 预热失败: {}", e.getMessage());
            }
        }, "plantuml-prewarm").start();
    }

    /**
     * 从 PlantUML DSL 文本生成PNG图片文件。
     *
     * @param plantUmlText PlantUML DSL 源码（含 @startuml / @enduml）
     * @param outputFile   输出PNG文件路径
     * @return 输出文件路径
     * @throws IOException 渲染失败
     */
    public Path generateToFile(String plantUmlText, Path outputFile) throws IOException {
        byte[] pngBytes = generateToBytes(plantUmlText);
        Files.createDirectories(outputFile.getParent());
        Files.write(outputFile, pngBytes);
        log.info("UML图已生成: {} ({} bytes)", outputFile, pngBytes.length);
        return outputFile;
    }

    /**
     * 从 PlantUML DSL 文本生成PNG字节数组。
     *
     * @param plantUmlText PlantUML DSL 源码
     * @return PNG字节数据
     * @throws IOException 渲染失败
     */
    public byte[] generateToBytes(String plantUmlText) throws IOException {
        SourceStringReader reader = new SourceStringReader(ensureAtStartEnd(plantUmlText));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            net.sourceforge.plantuml.core.DiagramDescription desc = reader.outputImage(baos,
                new FileFormatOption(FileFormat.PNG));
            if (desc == null) {
                throw new IOException("PlantUML 渲染失败: 无输出描述");
            }
            return baos.toByteArray();
        }
    }

    /**
     * 从 PlantUML DSL 文本生成 BufferedImage。
     * 用于在 Swing 界面中直接显示。
     *
     * @param plantUmlText PlantUML DSL 源码
     * @return BufferedImage
     * @throws IOException 渲染失败
     */
    public BufferedImage generateToImage(String plantUmlText) throws IOException {
        byte[] pngBytes = generateToBytes(plantUmlText);
        return ImageIO.read(new ByteArrayInputStream(pngBytes));
    }

    /**
     * 异步生成UML图（不阻塞UI线程）。
     *
     * @param plantUmlText PlantUML DSL 源码
     * @param outputFile   输出文件路径
     * @return CompletableFuture
     */
    public CompletableFuture<Path> generateAsync(String plantUmlText, Path outputFile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return generateToFile(plantUmlText, outputFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 快速检查 PlantUML DSL 语法是否基本有效。
     * 不保证100%正确，仅做快速检查。
     */
    public static boolean isValidSyntax(String text) {
        if (text == null || text.isBlank()) return false;
        String trimmed = text.trim();
        boolean hasStart = trimmed.contains("@startuml");
        boolean hasEnd = trimmed.contains("@enduml");
        return hasStart && hasEnd;
    }

    /** 确保文本包含 @startuml/@enduml 标记 */
    private static String ensureAtStartEnd(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("@startuml")) {
            trimmed = "@startuml\n" + trimmed;
        }
        if (!trimmed.endsWith("@enduml")) {
            trimmed = trimmed + "\n@enduml";
        }
        return trimmed;
    }

    // ---- main() 用于独立测试 ----

    public static void main(String[] args) throws Exception {
        UmlGenerator gen = new UmlGenerator();

        // 测试类图
        String classDiagram = """
            @startuml
            skinparam backgroundColor #FEFEFE
            skinparam classBorderColor #2196F3
            skinparam classFontColor #333333

            class ExperimentTask {
                - title: String
                - objectives: String
                - principles: String
                + getTitle(): String
                + isValid(): boolean
            }

            class ReportTemplate {
                - filePath: String
                - variables: List<String>
                + getVariables(): List<String>
                + setFilePath(String): ReportTemplate
            }

            class Project {
                - name: String
                - inputDir: String
                - task: ExperimentTask
                - template: ReportTemplate
                + save(Path): void
                + load(Path): Project
            }

            Project *-- ExperimentTask
            Project *-- ReportTemplate
            @enduml
            """;

        Path output = Path.of(System.getProperty("user.home"),
            ".labreport", "test-class-diagram.png");
        gen.generateToFile(classDiagram, output);
        System.out.println("测试类图已生成: " + output);
    }
}
