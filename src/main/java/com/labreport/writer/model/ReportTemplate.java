package com.labreport.writer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.nio.file.Path;
import java.util.*;

/**
 * ReportTemplate - DOCX模板元数据。
 * <p>
 * 存储模板文件路径、提取的变量列表、模板结构信息。
 * </p>
 */
@JsonSerialize
@JsonDeserialize
public class ReportTemplate {

    /** 模板文件路径 */
    @JsonProperty
    private String filePath = "";

    /** 模板中提取的变量名列表 */
    @JsonProperty
    private List<String> variables = new ArrayList<>();

    /** 变量 → 示例值（用户可编辑） */
    @JsonProperty
    private Map<String, String> variableValues = new LinkedHashMap<>();

    /** 模板包含的章节 */
    @JsonProperty
    private List<String> sections = new ArrayList<>();

    /** 报告封面字段 */
    @JsonProperty
    private Map<String, String> coverFields = new LinkedHashMap<>();

    /** 模板文件名（不含路径，用于显示） */
    @JsonProperty
    private String displayName = "";

    // ---- Getters ----

    public String getFilePath() { return filePath; }
    public List<String> getVariables() { return new ArrayList<>(variables); }
    public Map<String, String> getVariableValues() { return new LinkedHashMap<>(variableValues); }
    public List<String> getSections() { return new ArrayList<>(sections); }
    public Map<String, String> getCoverFields() { return new LinkedHashMap<>(coverFields); }
    public String getDisplayName() { return displayName; }

    /** 获取变量值（已配置的返回配置值，未配置的返回变量名作为占位符） */
    public String getValue(String variableName) {
        return variableValues.getOrDefault(variableName, "【" + variableName + "】");
    }

    /** 所有变量是否都已配置值 */
    public boolean isAllVariablesConfigured() {
        return variables.stream().allMatch(variableValues::containsKey);
    }

    /** 未配置的变量列表 */
    public List<String> getUnconfiguredVariables() {
        return variables.stream()
            .filter(v -> !variableValues.containsKey(v))
            .toList();
    }

    // ---- Setters (fluent API) ----

    public ReportTemplate setFilePath(String path) {
        this.filePath = path;
        if (this.displayName.isEmpty() && path != null) {
            this.displayName = Path.of(path).getFileName().toString();
        }
        return this;
    }

    public ReportTemplate setVariables(List<String> vars) {
        this.variables = new ArrayList<>(vars);
        // 初始化变量值映射（保留已有的值）
        Map<String, String> newValues = new LinkedHashMap<>();
        for (String v : vars) {
            newValues.put(v, variableValues.getOrDefault(v, ""));
        }
        this.variableValues = newValues;
        return this;
    }

    public ReportTemplate setVariableValue(String varName, String value) {
        this.variableValues.put(varName, value);
        return this;
    }

    public ReportTemplate setSections(List<String> sections) {
        this.sections = new ArrayList<>(sections);
        return this;
    }

    public ReportTemplate setCoverFields(Map<String, String> fields) {
        this.coverFields = new LinkedHashMap<>(fields);
        return this;
    }

    public ReportTemplate setCoverField(String key, String value) {
        this.coverFields.put(key, value);
        return this;
    }

    public ReportTemplate setDisplayName(String name) {
        this.displayName = name;
        return this;
    }

    /** 模板是否已加载 */
    public boolean isLoaded() {
        return filePath != null && !filePath.isBlank();
    }

    @Override
    public String toString() {
        return "ReportTemplate{" +
            "displayName='" + displayName + '\'' +
            ", variables=" + variables.size() +
            ", sections=" + sections.size() +
            ", configured=" + (variables.size() - getUnconfiguredVariables().size()) +
            "/" + variables.size() +
            '}';
    }
}
