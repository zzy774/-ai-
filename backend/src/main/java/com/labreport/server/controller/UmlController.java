package com.labreport.server.controller;

import com.labreport.server.common.Result;
import com.labreport.server.service.AiService;
import com.labreport.server.service.FileStorageService;
import com.labreport.server.service.UmlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/uml")
@RequiredArgsConstructor
public class UmlController {

    private final UmlService umlService;
    private final AiService aiService;
    private final FileStorageService fileService;

    @PostMapping("/generate")
    public Result<Map<String, Object>> generateUml(@RequestBody Map<String, String> body) {
        String dsl = body.get("plantUmlDsl");
        String fileName = umlService.generateImage(dsl);
        return Result.ok(Map.of(
            "imageUrl", "/api/uml/render-image/" + fileName,
            "imageFileName", fileName
        ));
    }

    @PostMapping("/from-code")
    public Result<Map<String, Object>> fromCode(@RequestBody Map<String, Object> body) {
        Long fileId = Long.valueOf(body.get("fileId").toString());
        String language = body.getOrDefault("language", "java").toString();
        String provider = body.getOrDefault("provider", "").toString();

        String code = fileService.getFileContent(fileId);
        String plantUml = aiService.analyzeCode(fileId, code, language,
            provider.isBlank() ? null : provider);

        // 提取 @startuml...@enduml 部分
        String cleanDsl = extractPlantUml(plantUml);
        String fileName = umlService.generateImage(cleanDsl);

        return Result.ok(Map.of(
            "imageUrl", "/api/uml/render-image/" + fileName,
            "imageFileName", fileName,
            "plantUmlDsl", cleanDsl
        ));
    }

    @GetMapping(value = "/render-image/{fileName}", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] getImage(@PathVariable String fileName) {
        return umlService.getImageBytes(fileName);
    }

    private String extractPlantUml(String text) {
        int start = text.indexOf("@startuml");
        int end = text.indexOf("@enduml");
        if (start >= 0 && end > start) {
            return text.substring(start, end + "@enduml".length());
        }
        return text;
    }
}
