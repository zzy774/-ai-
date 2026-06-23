package com.labreport.writer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * FileClassifier - 输入文件夹分类器。
 * <p>
 * 根据标准模板文件夹布局，自动识别和分类用户提供的文件。
 * 标准文件夹名称（中文）映射到内部角色枚举。
 * </p>
 *
 * <pre>
 * 标准输入文件夹布局:
 *   模板文件夹/
 *   ├─ 实验任务书/        → StandardFolder.TASK_BOOK
 *   ├─ 实验所需文件/      → StandardFolder.RELATED_FILES
 *   ├─ 实验报告模板/      → StandardFolder.REPORT_TEMPLATE
 *   ├─ 成品实验报告/      → StandardFolder.SAMPLE_REPORT
 *   ├─ 实验相关课件/      → StandardFolder.COURSEWARE
 *   ├─ 实验原始数据.md    → StandardFolder.RAW_DATA
 *   ├─ 个人信息.md        → StandardFolder.PERSONAL_INFO
 *   ├─ 评分标准.md        → StandardFolder.RUBRIC
 *   ├─ 输出文件要求.md    → StandardFolder.OUTPUT_REQUIREMENTS
 *   └─ 目标写作风格.md    → StandardFolder.WRITING_STYLE
 * </pre>
 */
public class FileClassifier {

    private static final Logger log = LoggerFactory.getLogger(FileClassifier.class);

    /**
     * 标准文件夹角色枚举。
     */
    public enum StandardFolder {
        TASK_BOOK("实验任务书", "实验任务书", true),
        RELATED_FILES("实验所需文件", "实验所需文件", false),
        REPORT_TEMPLATE("实验报告模板", "实验报告模板", false),
        SAMPLE_REPORT("成品实验报告", "成品实验报告", false),
        COURSEWARE("实验相关课件", "实验相关课件", false),
        RAW_DATA("实验原始数据", "实验原始数据.md", false),
        PERSONAL_INFO("个人信息", "个人信息.md", false),
        RUBRIC("评分标准", "评分标准.md", false),
        OUTPUT_REQUIREMENTS("输出文件要求", "输出文件要求.md", false),
        WRITING_STYLE("目标写作风格", "目标写作风格.md", false);

        /** 中文显示名 */
        public final String displayName;
        /** 标准文件/文件夹名 */
        public final String standardName;
        /** 是否必须有内容 */
        public final boolean required;

        StandardFolder(String displayName, String standardName, boolean required) {
            this.displayName = displayName;
            this.standardName = standardName;
            this.required = required;
        }

        /**
         * 根据文件名/目录名匹配标准文件夹。
         * 支持精确匹配和模糊匹配。
         */
        public static Optional<StandardFolder> match(String name) {
            if (name == null) return Optional.empty();

            // 去除扩展名（对于.md文件）
            String nameNoExt = name.replaceAll("\\.(md|MD|docx|DOCX|pdf|PDF)$", "").trim();

            for (StandardFolder sf : values()) {
                String standardNoExt = sf.standardName.replaceAll("\\.(md|MD)$", "").trim();
                if (name.equals(sf.standardName)          // 精确匹配
                    || name.equals(standardNoExt)         // 无扩展名精确匹配
                    || nameNoExt.equals(standardNoExt)    // 双方都去扩展名
                    || name.contains(sf.displayName)      // 包含显示名
                    || nameNoExt.contains(sf.displayName) // 去扩展名后包含显示名
                ) {
                    return Optional.of(sf);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * 分类结果：标准文件夹 → 实际路径 → 包含的文件列表
     */
    public static class ClassificationResult {
        /** 分类后的文件夹/文件映射 */
        public final Map<StandardFolder, Path> folderPaths = new LinkedHashMap<>();
        /** 每个标准文件夹下的文件 */
        public final Map<StandardFolder, List<Path>> files = new LinkedHashMap<>();
        /** 无法分类的文件 */
        public final List<Path> unclassified = new ArrayList<>();
        /** 缺失的必需文件夹 */
        public final List<StandardFolder> missing = new ArrayList<>();
        /** 发现的文件总数（含所有文件） */
        public int totalFilesFound = 0;

        /** 获取指定角色的文件列表 */
        public List<Path> getFiles(StandardFolder role) {
            return files.getOrDefault(role, Collections.emptyList());
        }

        /** 某角色是否有文件 */
        public boolean hasFiles(StandardFolder role) {
            List<Path> f = files.get(role);
            return f != null && !f.isEmpty();
        }

        /** 是否完整（所有必需的都有） */
        public boolean isComplete() {
            return missing.isEmpty();
        }

        /** 生成摘要 */
        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("========== 文件分类结果 ==========\n");
            for (StandardFolder sf : StandardFolder.values()) {
                List<Path> f = files.get(sf);
                int count = f != null ? f.size() : 0;
                String status = (sf.required && count == 0) ? " ⚠ 缺失(必需)" : "";
                sb.append(String.format("  %-16s : %d 个文件%s\n",
                    sf.displayName, count, status));
            }
            if (!unclassified.isEmpty()) {
                sb.append("\n  --- 未分类文件 ---\n");
                unclassified.forEach(p -> sb.append("    ").append(p.getFileName()).append("\n"));
            }
            sb.append(String.format("\n总计: %d 个文件\n", totalFilesFound));
            sb.append("===================================\n");
            return sb.toString();
        }
    }

    /**
     * 分类指定目录下的所有文件。
     *
     * @param rootDir 输入根目录（标准模板文件夹）
     * @return 分类结果
     * @throws IOException 读取失败
     */
    public static ClassificationResult classify(Path rootDir) throws IOException {
        log.info("开始分类输入文件夹: {}", rootDir);
        ClassificationResult result = new ClassificationResult();

        if (!Files.exists(rootDir) || !Files.isDirectory(rootDir)) {
            log.warn("输入目录不存在或不是目录: {}", rootDir);
            return result;
        }

        // 遍历根目录下的所有文件和文件夹
        try (var entries = Files.newDirectoryStream(rootDir)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();

                // 跳过隐藏文件和系统文件
                if (name.startsWith(".") || name.equals("__MACOSX")) continue;
                // 跳过 .gitkeep 占位文件
                if (name.equals(".gitkeep")) continue;

                // 尝试匹配标准文件夹
                Optional<StandardFolder> match = StandardFolder.match(name);

                if (match.isPresent()) {
                    StandardFolder sf = match.get();
                    result.folderPaths.put(sf, entry);

                    if (Files.isDirectory(entry)) {
                        // 收集该文件夹下的所有文件（非递归）
                        List<Path> fileList = new ArrayList<>();
                        collectFiles(entry, fileList, 1);
                        result.files.put(sf, fileList);
                        result.totalFilesFound += fileList.size();
                        log.debug("  {} → {} 个文件", sf.displayName, fileList.size());
                    } else {
                        // 单个文件（如 .md 文件）
                        result.files.put(sf, Collections.singletonList(entry));
                        result.totalFilesFound++;
                        log.debug("  {} → 1 个文件 ({})", sf.displayName, name);
                    }
                } else {
                    // 无法分类
                    result.unclassified.add(entry);
                    if (Files.isDirectory(entry)) {
                        List<Path> dirFiles = new ArrayList<>();
                        collectFiles(entry, dirFiles, 1);
                        result.totalFilesFound += dirFiles.size();
                        log.debug("  未分类目录: {} ({} 个文件)", name, dirFiles.size());
                    } else {
                        result.totalFilesFound++;
                        log.debug("  未分类文件: {}", name);
                    }
                }
            }
        }

        // 检查哪些必需的文件夹缺失
        for (StandardFolder sf : StandardFolder.values()) {
            if (sf.required && !result.hasFiles(sf)) {
                result.missing.add(sf);
            }
        }

        log.info("文件分类完成: {} 个文件, {} 个角色, {} 缺失",
            result.totalFilesFound,
            result.files.size(),
            result.missing.size());

        return result;
    }

    /** 收集目录下的所有文件（限制递归深度） */
    private static void collectFiles(Path dir, List<Path> result, int maxDepth) {
        if (maxDepth < 0) return;
        try (var entries = Files.newDirectoryStream(dir)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (name.startsWith(".") || name.equals(".gitkeep")) continue;
                if (Files.isDirectory(entry)) {
                    collectFiles(entry, result, maxDepth - 1);
                } else {
                    result.add(entry);
                }
            }
        } catch (IOException e) {
            log.warn("无法读取目录: {}", dir, e);
        }
    }

    /**
     * 快速检测目录是否匹配标准模板文件夹布局。
     * 只要至少匹配到"实验任务书"就返回true。
     */
    public static boolean isProbablyStandardLayout(Path dir) {
        try {
            ClassificationResult result = classify(dir);
            return result.hasFiles(StandardFolder.TASK_BOOK);
        } catch (Exception e) {
            return false;
        }
    }

    // ---- main() 用于独立测试 ----

    public static void main(String[] args) throws IOException {
        Path testDir = Path.of("E:\\12-15实训\\大三下\\!\\github-resources\\lab-report-writer\\模板文件夹");
        ClassificationResult result = classify(testDir);
        System.out.println(result.summary());
    }
}
