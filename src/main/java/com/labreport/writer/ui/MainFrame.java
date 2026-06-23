package com.labreport.writer.ui;

import com.labreport.writer.engine.EvidenceManager;
import com.labreport.writer.engine.ReportGenerator;
import com.labreport.writer.model.Project;
import com.labreport.writer.model.ReportConfig;
import com.labreport.writer.util.FileClassifier;
import com.labreport.writer.util.RuntimeProber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;

/**
 * MainFrame - 主窗口。
 * 使用JTabbedPane组织所有功能面板，含菜单栏、工具栏、进度条。
 */
public class MainFrame extends JFrame {

    private static final Logger log = LoggerFactory.getLogger(MainFrame.class);

    // ---- 面板 ----
    private final JTabbedPane tabbedPane;
    private final ProjectPanel projectPanel;
    private final InputPanel inputPanel;
    private final TemplatePanel templatePanel;
    private final UmlPanel umlPanel;
    private final ReportPreviewPanel previewPanel;
    private final SettingsPanel settingsPanel;

    // ---- 工具栏 ----
    private final JProgressBar progressBar;
    private final JLabel progressLabel;
    private final JButton generateBtn;
    private final JButton stopBtn;

    // ---- 核心引擎 ----
    private final ReportGenerator reportGenerator;
    private final RuntimeProber runtimeProber;

    /** 当前正在运行的生成线程（用于取消） */
    private SwingWorker<Path, Void> currentWorker;

    public MainFrame() {
        super("实验报告自动编写系统 v1.0");
        this.reportGenerator = new ReportGenerator();
        this.runtimeProber = new RuntimeProber();

        // ============ 创建面板 ============
        projectPanel = new ProjectPanel();
        inputPanel = new InputPanel();
        templatePanel = new TemplatePanel();
        umlPanel = new UmlPanel();
        previewPanel = new ReportPreviewPanel();
        settingsPanel = new SettingsPanel();

        // ============ 标签页 ============
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("📁 项目管理", projectPanel);
        tabbedPane.addTab("📂 材料输入", inputPanel);
        tabbedPane.addTab("📄 模板配置", templatePanel);
        tabbedPane.addTab("📐 UML图", umlPanel);
        tabbedPane.addTab("👁 报告预览", previewPanel);
        tabbedPane.addTab("⚙ 设置", settingsPanel);

        // ============ 菜单栏 ============
        setupMenuBar();

        // ============ 底部状态栏 ============
        JPanel statusBar = new JPanel(new BorderLayout(10, 0));
        statusBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        progressLabel = new JLabel("就绪");
        statusBar.add(progressLabel, BorderLayout.WEST);

        progressBar = new JProgressBar(0, 7);
        progressBar.setStringPainted(true);
        progressBar.setString("");
        progressBar.setPreferredSize(new Dimension(300, 22));
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        centerPanel.add(progressBar);
        statusBar.add(centerPanel, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        stopBtn = new JButton("停止");
        stopBtn.setEnabled(false);
        rightPanel.add(stopBtn);

        generateBtn = new JButton("🚀 生成报告");
        generateBtn.setBackground(new Color(33, 150, 243));
        generateBtn.setForeground(Color.WHITE);
        generateBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        rightPanel.add(generateBtn);
        statusBar.add(rightPanel, BorderLayout.EAST);

        // ============ 布局 ============
        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        // ============ 事件 ============
        setupActions();
    }

    /** 设置菜单栏 */
    private void setupMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // ---- 文件菜单 ----
        JMenu fileMenu = new JMenu("文件(F)");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        addMenuItem(fileMenu, "新建项目", KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK),
            e -> projectPanel.setProject(new Project("未命名项目")));

        addMenuItem(fileMenu, "打开项目", KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK),
            e -> {}); // 由ProjectPanel处理

        addMenuItem(fileMenu, "保存项目", KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK),
            e -> {}); // 由ProjectPanel处理

        fileMenu.addSeparator();

        addMenuItem(fileMenu, "退出", KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK),
            e -> { dispose(); System.exit(0); });

        menuBar.add(fileMenu);

        // ---- 生成菜单 ----
        JMenu generateMenu = new JMenu("生成(G)");
        generateMenu.setMnemonic(KeyEvent.VK_G);

        addMenuItem(generateMenu, "生成报告", KeyStroke.getKeyStroke(KeyEvent.VK_G, KeyEvent.CTRL_DOWN_MASK),
            e -> startGeneration());

        addMenuItem(generateMenu, "停止生成", KeyStroke.getKeyStroke("ESCAPE"),
            e -> stopGeneration());

        menuBar.add(generateMenu);

        // ---- 帮助菜单 ----
        JMenu helpMenu = new JMenu("帮助(H)");
        helpMenu.setMnemonic(KeyEvent.VK_H);

        addMenuItem(helpMenu, "关于", null, e -> showAbout());

        addMenuItem(helpMenu, "运行环境信息", null, e ->
            JOptionPane.showMessageDialog(this,
                runtimeProber.generateReport(), "运行环境",
                JOptionPane.INFORMATION_MESSAGE));

        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }

    private void addMenuItem(JMenu menu, String text, KeyStroke shortcut,
            java.awt.event.ActionListener action) {
        JMenuItem item = new JMenuItem(text);
        if (shortcut != null) item.setAccelerator(shortcut);
        item.addActionListener(action);
        menu.add(item);
    }

    /** 设置按钮事件 */
    private void setupActions() {
        generateBtn.addActionListener(e -> startGeneration());
        stopBtn.addActionListener(e -> stopGeneration());
    }

    /** 开始生成报告 */
    private void startGeneration() {
        // 收集所有面板的数据
        Project project = projectPanel.getProject();
        inputPanel.applyToProject(project);
        project.setTemplate(templatePanel.getTemplate());
        project.setConfig(settingsPanel.getConfig());

        // 验证
        Project.ValidationResult validation = project.validate();
        if (!validation.valid()) {
            JOptionPane.showMessageDialog(this,
                "项目配置不完整，无法生成报告:\n\n" + validation.formatIssues(),
                "配置不完整", JOptionPane.WARNING_MESSAGE);
            // 跳转到问题面板
            tabbedPane.setSelectedIndex(0);
            return;
        }

        // 更新UI状态
        generateBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        progressBar.setValue(0);
        progressBar.setString("准备中...");
        progressLabel.setText("正在生成报告...");

        // 切换到预览标签页
        tabbedPane.setSelectedIndex(4);

        // 设置进度回调
        reportGenerator.setProgressListener((phase, total, phaseName, detail) -> {
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(phase);
                progressBar.setString(String.format("阶段 %d/%d: %s", phase, total, phaseName));
                progressLabel.setText(detail);
            });
        });

        // 后台线程执行
        currentWorker = new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return reportGenerator.generate(project);
            }

            @Override
            protected void done() {
                try {
                    Path output = get();
                    progressBar.setValue(7);
                    progressBar.setString("完成!");
                    progressLabel.setText("✓ 报告已生成: " + output.toString());

                    JOptionPane.showMessageDialog(MainFrame.this,
                        "报告生成成功！\n\n文件位置:\n" + output.toString(),
                        "生成完成", JOptionPane.INFORMATION_MESSAGE);

                    // 更新预览
                    try {
                        EvidenceManager evidenceMgr = new EvidenceManager(runtimeProber,
                            FileClassifier.classify(project.getInputDirPath()));
                        previewPanel.updatePreview(project, evidenceMgr);
                    } catch (Exception ex) {
                        log.warn("预览更新失败", ex);
                    }

                } catch (Exception e) {
                    progressBar.setValue(0);
                    progressBar.setString("失败");
                    progressLabel.setText("✗ 生成失败: " + e.getMessage());
                    log.error("报告生成失败", e);

                    JOptionPane.showMessageDialog(MainFrame.this,
                        "报告生成失败:\n" + e.getMessage(),
                        "生成失败", JOptionPane.ERROR_MESSAGE);
                } finally {
                    generateBtn.setEnabled(true);
                    stopBtn.setEnabled(false);
                }
            }
        };
        currentWorker.execute();
    }

    /** 停止生成 */
    private void stopGeneration() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
            progressLabel.setText("⚠ 生成已取消");
            progressBar.setString("已取消");
            generateBtn.setEnabled(true);
            stopBtn.setEnabled(false);
        }
    }

    /** 显示关于对话框 */
    private void showAbout() {
        JOptionPane.showMessageDialog(this,
            """
            实验报告自动编写系统 v1.0

            一个基于Java Swing的现代化桌面应用程序，
            用于自动生成完整的实验报告（含UML图）。

            技术栈:
              🎨 FlatLaf - 现代化界面
              📐 PlantUML - UML图生成
              📝 poi-tl + Apache POI - Word报告生成
              🔍 ASM - Java代码分析
              📊 FreeMarker - 模板引擎

            © 2026 LabReportWriter
            """,
            "关于", JOptionPane.INFORMATION_MESSAGE);
    }

    /** 获取当前所有面板的项目汇总 */
    public Project collectProject() {
        Project project = projectPanel.getProject();
        inputPanel.applyToProject(project);
        project.setTemplate(templatePanel.getTemplate());
        project.setConfig(settingsPanel.getConfig());
        return project;
    }
}
