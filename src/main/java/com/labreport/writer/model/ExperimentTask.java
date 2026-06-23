package com.labreport.writer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.ArrayList;
import java.util.List;

/**
 * ExperimentTask - 实验任务信息。
 * <p>
 * 存储从实验任务书中提取的结构化信息。
 * 支持JSON序列化以便项目保存/加载。
 * </p>
 */
@JsonSerialize
@JsonDeserialize
public class ExperimentTask {

    /** 实验标题 */
    @JsonProperty
    private String title = "";

    /** 实验目的/目标 */
    @JsonProperty
    private String objectives = "";

    /** 实验原理/背景 */
    @JsonProperty
    private String principles = "";

    /** 实验所需工具/软件/材料 */
    @JsonProperty
    private List<String> requiredTools = new ArrayList<>();

    /** 实验步骤清单 */
    @JsonProperty
    private List<String> procedureSteps = new ArrayList<>();

    /** 要求输出的内容（表格、图表、截图等） */
    @JsonProperty
    private List<String> requiredOutputs = new ArrayList<>();

    /** 要求的分析内容 */
    @JsonProperty
    private String requiredAnalysis = "";

    /** 提交格式要求 */
    @JsonProperty
    private String submissionFormat = "";

    /** 原始任务书文本（用户粘贴或文件读取的原始内容） */
    @JsonProperty
    private String rawTaskText = "";

    // ---- Getters ----

    public String getTitle() { return title; }
    public String getObjectives() { return objectives; }
    public String getPrinciples() { return principles; }
    public List<String> getRequiredTools() { return new ArrayList<>(requiredTools); }
    public List<String> getProcedureSteps() { return new ArrayList<>(procedureSteps); }
    public List<String> getRequiredOutputs() { return new ArrayList<>(requiredOutputs); }
    public String getRequiredAnalysis() { return requiredAnalysis; }
    public String getSubmissionFormat() { return submissionFormat; }
    public String getRawTaskText() { return rawTaskText; }

    // ---- Setters (fluent API) ----

    public ExperimentTask setTitle(String title) { this.title = title; return this; }
    public ExperimentTask setObjectives(String objectives) { this.objectives = objectives; return this; }
    public ExperimentTask setPrinciples(String principles) { this.principles = principles; return this; }
    public ExperimentTask setRequiredTools(List<String> tools) { this.requiredTools = new ArrayList<>(tools); return this; }
    public ExperimentTask addRequiredTool(String tool) { this.requiredTools.add(tool); return this; }
    public ExperimentTask setProcedureSteps(List<String> steps) { this.procedureSteps = new ArrayList<>(steps); return this; }
    public ExperimentTask addProcedureStep(String step) { this.procedureSteps.add(step); return this; }
    public ExperimentTask setRequiredOutputs(List<String> outputs) { this.requiredOutputs = new ArrayList<>(outputs); return this; }
    public ExperimentTask addRequiredOutput(String output) { this.requiredOutputs.add(output); return this; }
    public ExperimentTask setRequiredAnalysis(String analysis) { this.requiredAnalysis = analysis; return this; }
    public ExperimentTask setSubmissionFormat(String format) { this.submissionFormat = format; return this; }
    public ExperimentTask setRawTaskText(String text) { this.rawTaskText = text; return this; }

    /** 验证任务是否有效（至少要有标题或原始文本） */
    public boolean isValid() {
        return !title.isBlank() || !rawTaskText.isBlank();
    }

    @Override
    public String toString() {
        return "ExperimentTask{" +
            "title='" + title + '\'' +
            ", objectives='" + (objectives.length() > 50 ? objectives.substring(0, 50) + "..." : objectives) + '\'' +
            ", procedureSteps=" + procedureSteps.size() + " steps" +
            ", requiredOutputs=" + requiredOutputs.size() + " outputs" +
            '}';
    }
}
