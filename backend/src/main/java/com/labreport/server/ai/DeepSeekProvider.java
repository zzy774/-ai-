package com.labreport.server.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labreport.server.model.entity.SystemConfig;
import com.labreport.server.model.mapper.SystemConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DeepSeekProvider implements AiProvider {

    private static final String BASE_URL = "https://api.deepseek.com/v1/chat/completions";
    private final SystemConfigMapper configMapper;
    private final ObjectMapper objectMapper;

    public DeepSeekProvider(SystemConfigMapper configMapper, ObjectMapper objectMapper) {
        this.configMapper = configMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() { return "DEEPSEEK"; }

    @Override
    public boolean isAvailable() {
        return getApiKey() != null && !getApiKey().isBlank();
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> body = Map.of(
                "model", getModel(),
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.7,
                "max_tokens", 8192
            );

            String response = RestClient.create().post()
                .uri(BASE_URL)
                .header("Authorization", "Bearer " + getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0)
                       .path("message").path("content")
                       .asText("DeepSeek未返回内容");
        } catch (Exception e) {
            log.error("DeepSeek API调用失败", e);
            return "[DeepSeek错误] " + e.getMessage();
        }
    }

    @Override
    public String analyzeCode(String code, String language) {
        String prompt = String.format("""
            Analyze this %s code and generate ONLY PlantUML class diagram DSL between @startuml and @enduml.

            ```%s
            %s
            ```
            """, language, language, code);

        return chat("You are a code analysis expert. Output only PlantUML DSL.", prompt);
    }

    private String getApiKey() {
        SystemConfig c = configMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, "ai.deepseek.api_key"));
        return c != null ? c.getConfigValue() : null;
    }

    private String getModel() {
        SystemConfig c = configMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, "ai.deepseek.model"));
        return c != null && !c.getConfigValue().isBlank() ? c.getConfigValue() : "deepseek-chat";
    }
}
