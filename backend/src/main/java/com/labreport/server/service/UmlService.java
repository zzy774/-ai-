package com.labreport.server.service;

import com.labreport.server.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Slf4j
@Service
public class UmlService {

    @Value("${storage.upload-dir:./uploads}")
    private String uploadDir;

    /** 从PlantUML DSL生成PNG图片，返回可访问的图片路径 */
    public String generateImage(String plantUmlDsl) {
        String text = ensureAtStartEnd(plantUmlDsl);
        try {
            SourceStringReader reader = new SourceStringReader(text);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            reader.outputImage(baos, new FileFormatOption(FileFormat.PNG));
            byte[] pngBytes = baos.toByteArray();

            // 保存到 uml 目录
            Path umlDir = Path.of(uploadDir, "uml");
            Files.createDirectories(umlDir);
            String fileName = "uml_" + UUID.randomUUID().toString().substring(0, 8) + ".png";
            Path output = umlDir.resolve(fileName);
            Files.write(output, pngBytes);

            log.info("UML图已生成: {} ({} bytes)", fileName, pngBytes.length);
            return fileName;
        } catch (IOException e) {
            log.error("UML生成失败", e);
            throw new BusinessException("UML图生成失败: " + e.getMessage());
        }
    }

    /** 读取已生成的UML图片 */
    public byte[] getImageBytes(String fileName) {
        try {
            Path umlDir = Path.of(uploadDir, "uml");
            return Files.readAllBytes(umlDir.resolve(fileName));
        } catch (IOException e) {
            throw new BusinessException(404, "UML图片不存在: " + fileName);
        }
    }

    private String ensureAtStartEnd(String text) {
        String t = text.trim();
        if (!t.startsWith("@startuml")) t = "@startuml\n" + t;
        if (!t.endsWith("@enduml")) t = t + "\n@enduml";
        return t;
    }
}
