package com.labreport.server.controller;

import com.labreport.server.common.Result;
import com.labreport.server.model.entity.ReportRecord;
import com.labreport.server.model.mapper.ProjectMapper;
import com.labreport.server.model.mapper.ReportRecordMapper;
import com.labreport.server.service.ReportGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportGeneratorService generatorService;
    private final ReportRecordMapper recordMapper;
    private final ProjectMapper projectMapper;

    /** 预览AI将要生成的内容（不产出DOCX） */
    @PostMapping("/preview")
    public Result<Map<String, Object>> preview(@RequestBody Map<String, Object> body) {
        Long projectId = Long.valueOf(body.get("projectId").toString());
        return Result.ok(generatorService.preview(projectId));
    }

    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        Long projectId = Long.valueOf(body.get("projectId").toString());
        String taskId = UUID.randomUUID().toString();

        ReportRecord record = new ReportRecord();
        record.setProjectId(projectId);
        record.setTaskId(taskId);
        record.setStatus("PENDING");
        record.setCreatedAt(java.time.LocalDateTime.now());
        recordMapper.insert(record);

        // 获取UML图片列表（可选）
        @SuppressWarnings("unchecked")
        List<String> umlImages = (List<String>) body.getOrDefault("umlImages", List.of());

        // 更新项目状态
        var project = projectMapper.selectById(projectId);
        if (project != null) {
            project.setStatus("GENERATING");
            projectMapper.updateById(project);
        }

        // 异步启动
        generatorService.generateAsync(projectId, record.getId(), umlImages);

        return Result.ok(Map.of("taskId", taskId, "recordId", record.getId(), "status", "PENDING"));
    }

    @GetMapping("/status/{taskId}")
    public Result<ReportRecord> getStatus(@PathVariable String taskId) {
        ReportRecord record = recordMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReportRecord>()
                .eq(ReportRecord::getTaskId, taskId));
        if (record == null) {
            return Result.error("任务不存在");
        }
        return Result.ok(record);
    }

    @GetMapping("/{id}")
    public Result<ReportRecord> getReport(@PathVariable Long id) {
        return Result.ok(recordMapper.selectById(id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        ReportRecord record = recordMapper.selectById(id);
        if (record == null || record.getOutputFilePath() == null) {
            return ResponseEntity.notFound().build();
        }
        FileSystemResource resource = new FileSystemResource(record.getOutputFilePath());
        String filename = URLEncoder.encode(
            java.nio.file.Path.of(record.getOutputFilePath()).getFileName().toString(),
            StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + filename)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(resource);
    }

    @GetMapping("/history")
    public Result<java.util.List<ReportRecord>> history(@RequestParam Long projectId) {
        return Result.ok(recordMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReportRecord>()
                .eq(ReportRecord::getProjectId, projectId)
                .orderByDesc(ReportRecord::getCreatedAt)));
    }
}
