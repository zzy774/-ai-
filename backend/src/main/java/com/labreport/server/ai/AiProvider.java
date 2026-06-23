package com.labreport.server.ai;

public interface AiProvider {
    String getProviderName();
    String chat(String systemPrompt, String userPrompt);
    String analyzeCode(String code, String language);
    boolean isAvailable();
}
