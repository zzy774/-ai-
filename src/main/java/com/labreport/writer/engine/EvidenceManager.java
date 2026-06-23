package com.labreport.writer.engine;

import com.labreport.writer.model.ExperimentTask;
import com.labreport.writer.util.FileClassifier;
import com.labreport.writer.util.RuntimeProber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

/**
 * EvidenceManager - 证据模式管理器。
 * <p>
 * 实现5级证据回退链：
 * </p>
 * <pre>
 *   真实数据 > 本地重现 > 公开来源 > 模拟数据 > 占位符
 * </pre>
 *
 * <p>
 * 决定报告中每个部分（截图、数据、结果）应该使用哪种证据模式。
 * </p>
 */
public class EvidenceManager {

    private static final Logger log = LoggerFactory.getLogger(EvidenceManager.class);

    /** 证据模式枚举（优先级从高到低） */
    public enum EvidenceMode {
        /** 用户提供的真实数据 - 最高优先级 */
        REAL_DATA("真实数据", 1),
        /** 本地运行代码/程序重现 */
        LOCAL_REPRODUCE("本地重现", 2),
        /** 从公开来源获取（文档、标准值等） */
        PUBLIC_SOURCE("公开来源", 3),
        /** 生成物理/逻辑上合理的模拟数据 */
        SIMULATED("模拟数据", 4),
        /** 插入占位符，提示用户手动填写 */
        PLACEHOLDER("占位符", 5);

        public final String displayName;
        public final int priority;

        EvidenceMode(String displayName, int priority) {
            this.displayName = displayName;
            this.priority = priority;
        }
    }

    /** 每个章节的证据计划 */
    public record SectionEvidence(String sectionName, EvidenceMode mode, String reason) {}

    private final RuntimeProber runtimeProber;
    private final FileClassifier.ClassificationResult classification;

    public EvidenceManager(RuntimeProber runtimeProber,
            FileClassifier.ClassificationResult classification) {
        this.runtimeProber = runtimeProber;
        this.classification = classification;
    }

    /**
     * 为报告的每个章节决定证据模式。
     *
     * @param task 实验任务
     * @return 章节 → 证据模式映射
     */
    public Map<String, SectionEvidence> planEvidence(ExperimentTask task) {
        Map<String, SectionEvidence> plan = new LinkedHashMap<>();

        // 检查哪些资源可用
        boolean hasRealData = hasRealData();
        boolean hasRelatedFiles = classification.hasFiles(
            FileClassifier.StandardFolder.RELATED_FILES);
        boolean canRunLocally = evaluateLocalReproduction();

        // ---- 章节: 截图 ----
        EvidenceMode screenshotMode;
        if (hasRelatedFiles && canRunLocally) {
            screenshotMode = EvidenceMode.LOCAL_REPRODUCE;
        } else {
            screenshotMode = EvidenceMode.PLACEHOLDER;
        }
        plan.put("截图", new SectionEvidence("截图", screenshotMode,
            canRunLocally ? "有实验文件且本地环境可用，可尝试运行截图"
                          : "无法本地运行，将使用占位符"));

        // ---- 章节: 实验数据 ----
        EvidenceMode dataMode;
        if (hasRealData) {
            dataMode = EvidenceMode.REAL_DATA;
        } else {
            dataMode = EvidenceMode.SIMULATED;
        }
        plan.put("实验数据", new SectionEvidence("实验数据", dataMode,
            hasRealData ? "使用用户提供的真实数据"
                        : "无真实数据，生成合理的模拟数据"));

        // ---- 章节: 实验结果 ----
        plan.put("实验结果", new SectionEvidence("实验结果", dataMode,
            "与实验数据一致"));

        // ---- 章节: 分析 ----
        plan.put("分析", new SectionEvidence("分析", dataMode,
            "基于" + dataMode.displayName + "进行分析"));

        // ---- 章节: 公式 ----
        plan.put("公式", new SectionEvidence("公式",
            EvidenceMode.PUBLIC_SOURCE,
            "公式来自教材和公开文献"));

        // ---- 章节: 结论 ----
        plan.put("结论", new SectionEvidence("结论", dataMode,
            "基于上述证据得出结论"));

        log.info("证据计划: {} 个章节", plan.size());
        plan.forEach((k, v) ->
            log.debug("  {} → {} ({})", k, v.mode().displayName, v.reason()));

        return plan;
    }

    /** 判断是否有真实数据 */
    private boolean hasRealData() {
        return classification.hasFiles(FileClassifier.StandardFolder.RAW_DATA);
    }

    /** 评估是否可以本地重现 */
    private boolean evaluateLocalReproduction() {
        boolean hasFiles = classification.hasFiles(
            FileClassifier.StandardFolder.RELATED_FILES);

        // 检查是否有可用的运行环境
        boolean hasRuntime = runtimeProber.canCompileJava()
            || runtimeProber.canRunPython()
            || runtimeProber.canRunNode();

        return hasFiles && hasRuntime;
    }

    /**
     * 生成摘要文本（放在报告外部，告诉用户证据来源）。
     */
    public String generateProvenanceSummary(Map<String, SectionEvidence> plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("证据来源说明（仅供内部参考，不出现在报告中）:\n");
        for (SectionEvidence se : plan.values()) {
            sb.append(String.format("  - %s: %s (原因: %s)\n",
                se.sectionName(), se.mode().displayName, se.reason()));
        }
        return sb.toString();
    }

    // ---- main() 独立测试 ----

    public static void main(String[] args) throws Exception {
        RuntimeProber prober = new RuntimeProber();
        FileClassifier.ClassificationResult cr = new FileClassifier.ClassificationResult();
        EvidenceManager mgr = new EvidenceManager(prober, cr);

        ExperimentTask task = new ExperimentTask()
            .setTitle("软件测试实验")
            .addRequiredOutput("运行截图")
            .addRequiredOutput("测试结果表");

        Map<String, SectionEvidence> plan = mgr.planEvidence(task);
        System.out.println(mgr.generateProvenanceSummary(plan));
    }
}
