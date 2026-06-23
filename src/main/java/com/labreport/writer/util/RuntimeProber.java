package com.labreport.writer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * RuntimeProber - 运行环境探测器。
 * <p>
 * 检测当前系统可用的运行时和工具，用于判断是否可以进行
 * 本地代码运行、截图生成等操作。
 * </p>
 *
 * <pre>
 * 检测项目:
 *   - Java 版本和编译器
 *   - Python 及其常用库
 *   - Node.js / npm
 *   - Maven / Gradle
 *   - Git
 *   - 数据库工具
 *   - 可用字体
 * </pre>
 */
public class RuntimeProber {

    private static final Logger log = LoggerFactory.getLogger(RuntimeProber.class);

    /** 需要检测的命令行工具 */
    private static final String[] COMMANDS = {
        "java", "javac", "mvn", "gradle", "git",
        "python", "python3", "py",
        "node", "npm",
        "mysql", "sqlite3"
    };

    /** Java版本 */
    private final String javaVersion;

    /** Java主目录 */
    private final String javaHome;

    /** 可用命令 → 完整路径 */
    private final Map<String, String> availableCommands;

    /** 不可用的命令 */
    private final Set<String> unavailableCommands;

    /** 操作系统名称 */
    private final String osName;

    /** 操作系统架构 */
    private final String osArch;

    public RuntimeProber() {
        this.javaVersion = System.getProperty("java.version", "unknown");
        this.javaHome = System.getProperty("java.home", "unknown");
        this.osName = System.getProperty("os.name", "unknown");
        this.osArch = System.getProperty("os.arch", "unknown");
        this.availableCommands = new LinkedHashMap<>();
        this.unavailableCommands = new LinkedHashSet<>();

        probe();
    }

    /**
     * 探测所有命令的可用性。
     */
    private void probe() {
        log.info("开始探测运行环境...");

        for (String cmd : COMMANDS) {
            // Windows 上命令通常带 .exe 后缀
            String cmdWithExt = osName.toLowerCase().contains("win") ? cmd + ".exe" : cmd;

            if (findCommand(cmdWithExt) || findCommand(cmd)) {
                log.debug("  ✓ {} 可用", cmd);
            } else {
                unavailableCommands.add(cmd);
                log.debug("  ✗ {} 不可用", cmd);
            }
        }

        log.info("运行环境探测完成: {}/{} 工具可用",
            availableCommands.size(), COMMANDS.length);
    }

    /**
     * 在PATH中查找命令。
     */
    private boolean findCommand(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            if (osName.toLowerCase().contains("win")) {
                pb.command("where", cmd);
            } else {
                pb.command("which", cmd);
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            int exitCode = process.waitFor();

            if (exitCode == 0 && !output.isEmpty()) {
                // 取第一行作为路径
                String path = output.lines().findFirst().orElse(cmd);
                availableCommands.put(cmd, path);
                return true;
            }
        } catch (Exception e) {
            // 命令不存在
        }
        return false;
    }

    // ---- 公共查询方法 ----

    /** Java版本号 */
    public String getJavaVersion() { return javaVersion; }

    /** Java主目录 */
    public String getJavaHome() { return javaHome; }

    /** 操作系统 */
    public String getOsName() { return osName; }

    /** 系统架构 */
    public String getOsArch() { return osArch; }

    /** 检查某命令是否可用 */
    public boolean isCommandAvailable(String command) {
        return availableCommands.containsKey(command);
    }

    /** 获取命令的完整路径 */
    public Optional<String> getCommandPath(String command) {
        return Optional.ofNullable(availableCommands.get(command));
    }

    /** 获取所有可用命令 */
    public Map<String, String> getAvailableCommands() {
        return Collections.unmodifiableMap(availableCommands);
    }

    /** 获取不可用的命令 */
    public Set<String> getUnavailableCommands() {
        return Collections.unmodifiableSet(unavailableCommands);
    }

    /** 是否可运行Java编译 */
    public boolean canCompileJava() {
        return isCommandAvailable("javac") || isCommandAvailable("javac.exe");
    }

    /** 是否可运行Node.js */
    public boolean canRunNode() {
        return isCommandAvailable("node") || isCommandAvailable("node.exe");
    }

    /** 是否可运行Python */
    public boolean canRunPython() {
        return isCommandAvailable("python") || isCommandAvailable("python3")
            || isCommandAvailable("py");
    }

    // ---- 报告生成 ----

    /**
     * 生成环境报告（多行文本，用于调试和日志）。
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("========== 运行环境报告 ==========\n");

        // 基本环境
        sb.append(String.format("%-20s: %s\n", "操作系统", osName + " (" + osArch + ")"));
        sb.append(String.format("%-20s: %s\n", "Java版本", javaVersion));
        sb.append(String.format("%-20s: %s\n", "Java主目录", javaHome));

        // JVM 参数
        sb.append(String.format("%-20s: %s\n", "最大内存",
            Runtime.getRuntime().maxMemory() / 1024 / 1024 + " MB"));
        sb.append(String.format("%-20s: %s\n", "处理器核心",
            Runtime.getRuntime().availableProcessors()));

        // 命令可用性
        sb.append("\n--- 命令行工具 ---\n");
        for (String cmd : COMMANDS) {
            String path = availableCommands.get(cmd);
            if (path != null) {
                sb.append(String.format("  ✓ %-15s → %s\n", cmd, path));
            } else {
                sb.append(String.format("  ✗ %-15s (不可用)\n", cmd));
            }
        }

        sb.append("===================================\n");
        return sb.toString();
    }

    // ---- main() 用于独立测试 ----

    public static void main(String[] args) {
        RuntimeProber prober = new RuntimeProber();
        System.out.println(prober.generateReport());
    }
}
