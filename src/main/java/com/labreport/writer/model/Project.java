package com.labreport.writer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Project - 实验报告项目。
 * <p>
 * 项目的核心数据模型，包含输入材料路径、模板信息、任务信息和生成配置。
 * 支持 JSON 序列化，保存为 .lrp 文件格式。
 * </p>
 *
 * <pre>
 * 使用示例：
 *   Project p = new Project("软件测试实验报告");
 *   p.setInputDir(Path.of("D:/模板文件夹"));
 *   p.save(Path.of("D:/软件测试实验报告.lrp"));
 *
 *   Project loaded = Project.load(Path.of("D:/软件测试实验报告.lrp"));
 * </pre>
 */
public class Project {

    private static final Logger log = LoggerFactory.getLogger(Project.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .findAndRegisterModules();

    // ---- 基本属性 ----

    /** 项目名称 */
    @JsonProperty
    private String name;

    /** 项目创建时间 */
    @JsonProperty
    private String createdAt;

    /** 项目最后修改时间 */
    @JsonProperty
    private String lastModifiedAt;

    /** 项目文件路径（.lrp），保存后设置 */
    @JsonIgnore
    private Path projectPath;

    // ---- 输入材料 ----

    /** 输入文件夹路径（标准模板文件夹布局） */
    @JsonProperty
    private String inputDir = "";

    /** 标准文件夹 → 实际路径映射（由 FileClassifier 填充） */
    @JsonProperty
    private Map<String, String> classifiedFiles = new LinkedHashMap<>();

    // ---- 模板和任务 ----

    /** 报告模板 */
    @JsonProperty
    private ReportTemplate template = new ReportTemplate();

    /** 实验任务信息 */
    @JsonProperty
    private ExperimentTask task = new ExperimentTask();

    /** 报告配置 */
    @JsonProperty
    private ReportConfig config = ReportConfig.defaults();

    // ---- 个人信息 ----

    /** 封面个人信息（姓名、学号、班级等） */
    @JsonProperty
    private Map<String, String> personalInfo = new LinkedHashMap<>();

    // ---- 输出记录 ----

    /** 最近生成的输出文件路径 */
    @JsonProperty
    private List<String> recentOutputs = new ArrayList<>();

    // ---- 构造函数 ----

    /** 无参构造（供 Jackson 反序列化） */
    public Project() {
        this.name = "未命名项目";
        this.createdAt = now();
        this.lastModifiedAt = now();
        initDefaultPersonalInfo();
    }

    /** 带名称构造 */
    public Project(String name) {
        this.name = (name != null && !name.isBlank()) ? name : "未命名项目";
        this.createdAt = now();
        this.lastModifiedAt = now();
        initDefaultPersonalInfo();
    }

    /** 初始化默认个人信息字段（空值） */
    private void initDefaultPersonalInfo() {
        String[] fields = {"姓名", "学号", "班级", "课程名", "教师名", "日期", "小组成员"};
        for (String f : fields) {
            personalInfo.putIfAbsent(f, "");
        }
    }

    // ---- 序列化 ----

    /** 保存项目到 .lrp 文件（JSON格式） */
    public void save(Path path) throws IOException {
        this.projectPath = path;
        this.lastModifiedAt = now();
        MAPPER.writeValue(path.toFile(), this);
        log.info("项目已保存: {}", path);
    }

    /** 从 .lrp 文件加载项目 */
    public static Project load(Path path) throws IOException {
        Project project = MAPPER.readValue(path.toFile(), Project.class);
        project.projectPath = path;
        log.info("项目已加载: {}", path);
        return project;
    }

    /** 验证项目是否具备基本生成条件 */
    public ValidationResult validate() {
        List<String> issues = new ArrayList<>();

        if (inputDir == null || inputDir.isBlank()) {
            issues.add("未设置输入文件夹路径");
        } else if (!Files.exists(Path.of(inputDir))) {
            issues.add("输入文件夹不存在: " + inputDir);
        }

        if (task == null || !task.isValid()) {
            issues.add("未提供实验任务书内容");
        }

        if (config == null) {
            issues.add("报告配置为空");
        }

        return new ValidationResult(issues.isEmpty(), issues);
    }

    // ---- 便捷方法 ----

    /** 设置输入文件夹 */
    public Project setInputDir(Path dir) {
        this.inputDir = dir != null ? dir.toAbsolutePath().toString() : "";
        this.lastModifiedAt = now();
        return this;
    }

    /** 获取输入文件夹路径 */
    @JsonIgnore
    public Path getInputDirPath() {
        return inputDir != null && !inputDir.isBlank() ? Path.of(inputDir) : null;
    }

    /** 添加生成的输出文件路径 */
    public Project addOutput(String outputPath) {
        this.recentOutputs.add(0, outputPath); // 最新在前
        if (this.recentOutputs.size() > 10) {
            this.recentOutputs = this.recentOutputs.subList(0, 10);
        }
        return this;
    }

    /** 获取最新输出路径 */
    @JsonIgnore
    public Optional<String> getLatestOutput() {
        return recentOutputs.isEmpty()
            ? Optional.empty()
            : Optional.of(recentOutputs.get(0));
    }

    // ---- Getter/Setter ----

    public String getName() { return name; }
    public void setName(String name) {
        this.name = name;
        this.lastModifiedAt = now();
    }

    public String getCreatedAt() { return createdAt; }
    public String getLastModifiedAt() { return lastModifiedAt; }

    public String getInputDir() { return inputDir; }
    public Map<String, String> getClassifiedFiles() { return classifiedFiles; }
    public void setClassifiedFiles(Map<String, String> files) { this.classifiedFiles = files; }

    public ReportTemplate getTemplate() { return template; }
    public void setTemplate(ReportTemplate template) { this.template = template; }

    public ExperimentTask getTask() { return task; }
    public void setTask(ExperimentTask task) { this.task = task; }

    public ReportConfig getConfig() { return config; }
    public void setConfig(ReportConfig config) { this.config = config; }

    public Map<String, String> getPersonalInfo() { return personalInfo; }
    public void setPersonalInfo(Map<String, String> info) {
        this.personalInfo = info;
        initDefaultPersonalInfo(); // 确保基本字段存在
    }

    public List<String> getRecentOutputs() { return recentOutputs; }

    @JsonIgnore
    public Path getProjectPath() { return projectPath; }

    @Override
    public String toString() {
        return "Project{name='" + name + "', task=" + task.getTitle() + '}';
    }

    // ---- 工具方法 ----

    private static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 项目验证结果。
     */
    public record ValidationResult(boolean valid, List<String> issues) {
        public String formatIssues() {
            return issues.isEmpty() ? "项目配置完整，可以生成报告"
                : "发现 " + issues.size() + " 个问题:\n  - " + String.join("\n  - ", issues);
        }
    }
}
