package com.labreport.server.service;

import com.labreport.server.ai.AiProvider;
import com.labreport.server.ai.AiProviderFactory;
import com.labreport.server.common.Constants;
import com.labreport.server.model.entity.AiConversation;
import com.labreport.server.model.mapper.AiConversationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final AiProviderFactory providerFactory;
    private final AiConversationMapper conversationMapper;

    public String chat(Long projectId, String message, String providerName) {
        AiProvider provider = (providerName != null && !providerName.isBlank())
            ? providerFactory.getProvider(providerName)
            : providerFactory.getDefaultProvider();

        // 构建上下文
        String systemPrompt = buildSystemPrompt(projectId);
        String contextPrompt = buildContextPrompt(projectId, message);

        // 保存用户消息
        saveMessage(projectId, provider.getProviderName(), "user", message);

        // 调用AI
        String response = provider.chat(systemPrompt, contextPrompt);

        // 保存AI回复
        saveMessage(projectId, provider.getProviderName(), "assistant", response);

        return response;
    }

    public String analyzeCode(Long projectId, String code, String language, String providerName) {
        AiProvider provider = (providerName != null && !providerName.isBlank())
            ? providerFactory.getProvider(providerName)
            : providerFactory.getDefaultProvider();

        return provider.analyzeCode(code, language);
    }

    public List<AiConversation> getConversation(Long projectId) {
        return conversationMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getProjectId, projectId)
                .orderByAsc(AiConversation::getId)
                .last("LIMIT " + Constants.AI_MAX_CONTEXT_MESSAGES * 2));
    }

    public void clearConversation(Long projectId) {
        conversationMapper.delete(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getProjectId, projectId));
    }

    private void saveMessage(Long projectId, String provider, String role, String content) {
        AiConversation msg = new AiConversation();
        msg.setProjectId(projectId);
        msg.setProvider(provider);
        msg.setRole(role);
        msg.setContent(content);
        msg.setCreatedAt(LocalDateTime.now());
        conversationMapper.insert(msg);
    }

    private String buildSystemPrompt(Long projectId) {
        return """
            你是一个实验报告辅助编写助手。你的职责是：
            1. 帮助用户分析实验任务书，提取关键信息
            2. 分析上传的代码文件，解释代码结构和逻辑
            3. 帮助撰写实验报告的各个部分（原理、步骤、结果、分析、总结）
            4. 回答实验相关的技术问题
            5. 生成PlantUML类图DSL代码以可视化代码结构

            回答要求：
            - 使用中文回复
            - 内容准确、清晰、符合本科生实验报告水平
            - 涉及代码分析时，给出具体的类、方法、关系说明
            - 生成的PlantUML代码放在 @startuml/@enduml 标签内
            """;
    }

    private String buildContextPrompt(Long projectId, String currentMessage) {
        List<AiConversation> history = getConversation(projectId);
        if (history.isEmpty()) {
            return currentMessage;
        }
        // 添加最近的对话上下文
        List<AiConversation> recent = history;
        if (history.size() > Constants.AI_MAX_CONTEXT_MESSAGES) {
            recent = history.subList(history.size() - Constants.AI_MAX_CONTEXT_MESSAGES, history.size());
        }
        String context = recent.stream()
            .map(m -> m.getRole() + ": " + m.getContent())
            .collect(Collectors.joining("\n"));
        return "对话历史:\n" + context + "\n\n当前问题:\n" + currentMessage;
    }
}
