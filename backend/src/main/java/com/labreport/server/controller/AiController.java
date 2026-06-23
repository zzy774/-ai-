package com.labreport.server.controller;

import com.labreport.server.ai.AiProviderFactory;
import com.labreport.server.common.Result;
import com.labreport.server.model.dto.AiChatRequest;
import com.labreport.server.model.entity.AiConversation;
import com.labreport.server.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final AiProviderFactory providerFactory;

    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody @Valid AiChatRequest req) {
        String response = aiService.chat(req.getProjectId(), req.getMessage(), req.getProvider());
        return Result.ok(Map.of(
            "message", response,
            "provider", req.getProvider() != null ? req.getProvider() : "DEEPSEEK",
            "timestamp", System.currentTimeMillis()
        ));
    }

    @PostMapping("/analyze-code")
    public Result<Map<String, String>> analyzeCode(@RequestBody Map<String, Object> body) {
        Long projectId = Long.valueOf(body.get("projectId").toString());
        String code = body.get("code").toString();
        String language = body.getOrDefault("language", "java").toString();
        String provider = body.getOrDefault("provider", "").toString();

        String result = aiService.analyzeCode(projectId, code, language,
            provider.isBlank() ? null : provider);
        return Result.ok(Map.of("plantUml", result, "language", language));
    }

    @GetMapping("/providers")
    public Result<List<String>> getProviders() {
        return Result.ok(providerFactory.getAllProviderNames());
    }

    @GetMapping("/providers/available")
    public Result<List<String>> getAvailableProviders() {
        return Result.ok(providerFactory.getAvailableProviders());
    }

    @GetMapping("/conversations/{projectId}")
    public Result<List<AiConversation>> getConversation(@PathVariable Long projectId) {
        return Result.ok(aiService.getConversation(projectId));
    }

    @DeleteMapping("/conversations/{projectId}")
    public Result<Void> clearConversation(@PathVariable Long projectId) {
        aiService.clearConversation(projectId);
        return Result.ok(null);
    }
}
