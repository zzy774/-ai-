package com.labreport.server.service;

import com.labreport.server.common.BusinessException;
import com.labreport.server.model.entity.UploadedFile;
import com.labreport.server.model.mapper.UploadedFileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final UploadedFileMapper fileMapper;

    @Value("${storage.upload-dir:./uploads}")
    private String uploadDir;

    public List<UploadedFile> uploadFiles(Long projectId, MultipartFile[] files,
            List<String> relativePaths, String overrideFileType) {
        List<UploadedFile> results = new ArrayList<>();
        Path projectDir = Path.of(uploadDir, projectId.toString());
        try { Files.createDirectories(projectDir); } catch (IOException e) {
            throw new BusinessException("创建上传目录失败: " + e.getMessage());
        }

        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            if (file.isEmpty()) continue;
            try {
                String relativePath = i < relativePaths.size() ? relativePaths.get(i) : null;
                String originalName = file.getOriginalFilename();
                // 用文件夹中的相对路径作为显示名
                String displayName = (relativePath != null && !relativePath.isBlank())
                    ? relativePath.replace('\\', '/') : originalName;

                String uuid = UUID.randomUUID().toString().substring(0, 8);
                String storedName = uuid + "_" + (originalName != null ? originalName.replaceAll("[\\\\/:*?\"<>|]", "_") : "file");

                // 保留目录层级
                Path destDir = projectDir;
                if (relativePath != null && !relativePath.isBlank()) {
                    Path parentDir = Path.of(relativePath.replace('\\', '/')).getParent();
                    if (parentDir != null) {
                        destDir = projectDir.resolve(parentDir);
                        Files.createDirectories(destDir);
                    }
                }
                Path dest = destDir.resolve(storedName);
                file.transferTo(dest);

                UploadedFile entity = new UploadedFile();
                entity.setProjectId(projectId);
                entity.setOriginalName(displayName);
                entity.setStoredName(storedName);
                entity.setFilePath(dest.toAbsolutePath().toString());
                entity.setFileSize(file.getSize());
                entity.setMimeType(file.getContentType());
                entity.setMd5Hash(computeMd5(dest));
                entity.setLanguage(detectLanguage(displayName));
                entity.setFileType(overrideFileType != null ? overrideFileType : classifyFileType(displayName));
                entity.setFolderPath(relativePath);
                entity.setCreatedAt(java.time.LocalDateTime.now());
                fileMapper.insert(entity);
                results.add(entity);
            } catch (IOException e) {
                log.error("文件上传失败: {}", file.getOriginalFilename(), e);
                throw new BusinessException("文件上传失败: " + e.getMessage());
            }
        }
        return results;
    }

    public UploadedFile getFile(Long fileId) {
        UploadedFile f = fileMapper.selectById(fileId);
        if (f == null) throw new BusinessException(404, "文件不存在");
        return f;
    }

    public String getFileContent(Long fileId) {
        UploadedFile f = getFile(fileId);
        try {
            return Files.readString(Path.of(f.getFilePath()));
        } catch (IOException e) {
            throw new BusinessException("读取文件内容失败: " + e.getMessage());
        }
    }

    public void deleteFile(Long fileId) {
        UploadedFile f = getFile(fileId);
        try { Files.deleteIfExists(Path.of(f.getFilePath())); } catch (IOException ignored) {}
        fileMapper.deleteById(fileId);
    }

    public List<UploadedFile> getProjectFiles(Long projectId) {
        return fileMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UploadedFile>()
                .eq(UploadedFile::getProjectId, projectId)
                .orderByDesc(UploadedFile::getCreatedAt));
    }

    private String detectLanguage(String filename) {
        String name = filename.toLowerCase();
        if (name.endsWith(".java")) return "java";
        if (name.endsWith(".py")) return "python";
        if (name.endsWith(".cpp") || name.endsWith(".cc") || name.endsWith(".cxx")) return "cpp";
        if (name.endsWith(".c") || name.endsWith(".h")) return "c";
        if (name.endsWith(".js") || name.endsWith(".ts")) return "javascript";
        if (name.endsWith(".html") || name.endsWith(".htm")) return "html";
        if (name.endsWith(".css")) return "css";
        if (name.endsWith(".sql")) return "sql";
        if (name.endsWith(".xml")) return "xml";
        if (name.endsWith(".json")) return "json";
        if (name.endsWith(".go")) return "go";
        if (name.endsWith(".rs")) return "rust";
        if (name.endsWith(".md")) return "markdown";
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
            || name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".svg")
            || name.endsWith(".webp") || name.endsWith(".ico")) return "image";
        if (name.endsWith(".docx") || name.endsWith(".doc")) return "doc";
        if (name.endsWith(".pdf")) return "pdf";
        if (name.endsWith(".txt")) return "text";
        return null;
    }

    private String classifyFileType(String filename) {
        String name = filename.toLowerCase();
        if (name.matches(".*\\.(java|py|cpp|c|h|js|ts|html|css|sql|xml|json|go|rs|md)$"))
            return "SOURCE_CODE";
        if (name.matches(".*\\.(png|jpg|jpeg|gif|bmp|svg|webp|ico)$"))
            return "IMAGE";
        if (name.matches(".*\\.(docx|doc|pdf|txt)$"))
            return "TEMPLATE";
        return "OTHER";
    }

    private String computeMd5(Path file) {
        try {
            byte[] data = Files.readAllBytes(file);
            byte[] hash = MessageDigest.getInstance("MD5").digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return null; }
    }
}
