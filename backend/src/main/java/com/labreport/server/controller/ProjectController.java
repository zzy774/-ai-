package com.labreport.server.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labreport.server.common.Result;
import com.labreport.server.model.dto.ProjectCreateRequest;
import com.labreport.server.model.entity.Project;
import com.labreport.server.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public Result<Page<Project>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        // 单用户场景，userId=1（admin）
        return Result.ok(projectService.listProjects(page, size, 1L));
    }

    @PostMapping
    public Result<Project> create(@RequestBody @Valid ProjectCreateRequest req) {
        return Result.ok(projectService.createProject(req.getName(), req.getDescription(), 1L));
    }

    @GetMapping("/{id}")
    public Result<Project> get(@PathVariable Long id) {
        return Result.ok(projectService.getProject(id));
    }

    @PutMapping("/{id}")
    public Result<Project> update(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        return Result.ok(projectService.updateProject(id, updates));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.deleteProject(id);
        return Result.ok(null);
    }
}
