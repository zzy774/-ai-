package com.labreport.server.ai;

import com.labreport.server.common.BusinessException;
import com.labreport.server.model.entity.SystemConfig;
import com.labreport.server.model.mapper.SystemConfigMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiProviderFactory {

    private final Map<String, AiProvider> providerMap = new LinkedHashMap<>();
    private final SystemConfigMapper configMapper;

    public AiProviderFactory(DeepSeekProvider deepseek,
            QwenProvider qwen, SystemConfigMapper configMapper) {
        this.configMapper = configMapper;
        this.providerMap.put("DEEPSEEK", deepseek);
        this.providerMap.put("QWEN", qwen);
    }

    public AiProvider getProvider(String name) {
        AiProvider provider = providerMap.get(name.toUpperCase());
        if (provider == null) {
            throw new BusinessException(400, "不支持的AI供应商: " + name);
        }
        if (!provider.isAvailable()) {
            throw new BusinessException(400, "AI供应商 " + name + " 未配置API Key");
        }
        return provider;
    }

    public AiProvider getDefaultProvider() {
        SystemConfig c = configMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, "ai.default_provider"));
        String defaultName = c != null ? c.getConfigValue() : "DEEPSEEK";
        return getProvider(defaultName != null ? defaultName : "DEEPSEEK");
    }

    public List<String> getAvailableProviders() {
        return providerMap.values().stream()
            .filter(AiProvider::isAvailable)
            .map(AiProvider::getProviderName)
            .toList();
    }

    public List<String> getAllProviderNames() {
        return List.copyOf(providerMap.keySet());
    }

    public boolean isAnyAvailable() {
        return providerMap.values().stream().anyMatch(AiProvider::isAvailable);
    }
}
