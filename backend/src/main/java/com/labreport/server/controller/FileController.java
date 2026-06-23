package com.labreport.server.controller;

import com.labreport.server.common.Result;
import com.labreport.server.model.entity.UploadedFile;
import com.labreport.server.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileService;

    @PostMapping("/upload")
    public Result<List<UploadedFile>> upload(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam Long projectId,
            @RequestParam(value = "paths", required = false) String pathsJson,
            @RequestParam(value = "fileType", required = false) String overrideFileType) {
        java.util.List<String> relativePaths = new java.util.ArrayList<>();
        if (pathsJson != null && !pathsJson.isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                relativePaths = mapper.readValue(pathsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
            } catch (Exception ignored) {}
        }
        while (relativePaths.size() < files.length) {
            relativePaths.add(null);
        }
        return Result.ok(fileService.uploadFiles(projectId, files, relativePaths, overrideFileType));
    }

    @GetMapping("/project/{projectId}")
    public Result<List<UploadedFile>> listByProject(@PathVariable Long projectId) {
        return Result.ok(fileService.getProjectFiles(projectId));
    }

    @GetMapping("/{id}")
    public Result<UploadedFile> getFile(@PathVariable Long id) {
        return Result.ok(fileService.getFile(id));
    }

    @GetMapping("/{id}/content")
    public Result<Map<String, String>> getContent(@PathVariable Long id) {
        UploadedFile f = fileService.getFile(id);
        return Result.ok(Map.of(
            "content", fileService.getFileContent(id),
            "language", f.getLanguage() != null ? f.getLanguage() : "text"
        ));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteFile(@PathVariable Long id) {
        fileService.deleteFile(id);
        return Result.ok(null);
    }
}
