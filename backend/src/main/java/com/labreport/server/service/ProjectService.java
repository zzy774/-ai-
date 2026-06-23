package com.labreport.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labreport.server.common.BusinessException;
import com.labreport.server.model.entity.Project;
import com.labreport.server.model.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;

    public Page<Project> listProjects(int page, int size, Long userId) {
        Page<Project> p = new Page<>(page, size);
        return projectMapper.selectPage(p,
            new LambdaQueryWrapper<Project>()
                .eq(Project::getUserId, userId)
                .orderByDesc(Project::getUpdatedAt));
    }

    public Project getProject(Long id) {
        Project p = projectMapper.selectById(id);
        if (p == null) throw new BusinessException(404, "项目不存在");
        return p;
    }

    public Project createProject(String name, String description, Long userId) {
        Project p = new Project();
        p.setName(name);
        p.setDescription(description);
        p.setUserId(userId);
        p.setStatus("DRAFT");
        projectMapper.insert(p);
        return p;
    }

    public Project updateProject(Long id, Map<String, Object> updates) {
        Project p = getProject(id);
        if (updates.containsKey("name")) p.setName(updates.get("name").toString());
        if (updates.containsKey("description")) p.setDescription(updates.get("description").toString());
        if (updates.containsKey("configJson")) p.setConfigJson(updates.get("configJson").toString());
        if (updates.containsKey("personalInfoJson")) p.setPersonalInfoJson(updates.get("personalInfoJson").toString());
        if (updates.containsKey("status")) p.setStatus(updates.get("status").toString());
        projectMapper.updateById(p);
        return p;
    }

    public void deleteProject(Long id) {
        projectMapper.deleteById(id); // 逻辑删除
    }
}
