package com.labreport.server.model.dto;

import jakarta.validation.constraints.NotBlank;

public class AiChatRequest {
    private Long projectId;
    @NotBlank private String message;
    private String provider;
    private Boolean includeContext;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public Boolean getIncludeContext() { return includeContext; }
    public void setIncludeContext(Boolean includeContext) { this.includeContext = includeContext; }
}
