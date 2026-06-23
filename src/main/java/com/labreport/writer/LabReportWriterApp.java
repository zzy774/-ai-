package com.labreport.writer;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

/**
 * LabReportWriterApp - 实验报告自动编写系统
 * <p>
 * 一个基于Java Swing + FlatLaf的现代化桌面应用程序，
 * 用于自动生成完整的实验报告（含UML图）。
 * </p>
 *
 * <pre>
 * 使用方式：
 *   1. 双击 JAR 文件启动
 *   2. 命令行: java -jar lab-report-writer-app.jar
 *   3. Maven:   mvn exec:java
 *
 * 技术栈：Swing + FlatLaf + PlantUML + poi-tl + ASM + FreeMarker
 * </pre>
 *
 * @author LabReportWriter
 * @version 1.0.0
 */
public class LabReportWriterApp {

    private static final Logger log = LoggerFactory.getLogger(LabReportWriterApp.class);

    public static void main(String[] args) {
        log.info("========================================");
        log.info("  实验报告自动编写系统 v1.0.0 启动中...");
        log.info("========================================");

        // ---- 1. 设置 FlatLaf 现代化界面 ----
        setupLookAndFeel();

        // ---- 2. 在EDT线程启动GUI ----
        SwingUtilities.invokeLater(() -> {
            try {
                // 预热 PlantUML（后台线程，不阻塞UI）
                prewarmPlantUml();

                // 创建并显示主窗口
                com.labreport.writer.ui.MainFrame mainFrame = new com.labreport.writer.ui.MainFrame();
                mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                mainFrame.setSize(1200, 800);
                mainFrame.setMinimumSize(new Dimension(900, 600));
                mainFrame.setLocationRelativeTo(null);
                mainFrame.setVisible(true);

                log.info("主窗口已显示，系统启动完成！");

            } catch (Exception e) {
                log.error("启动失败", e);
                JOptionPane.showMessageDialog(null,
                    "系统启动失败: " + e.getMessage(),
                    "启动错误",
                    JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    /**
     * 设置 FlatLaf 外观主题。
     * 默认使用亮色主题，用户可在设置中切换。
     */
    private static void setupLookAndFeel() {
        try {
            // 使用 FlatLaf 亮色主题
            FlatLightLaf.setup();
            log.info("FlatLaf 亮色主题加载成功");
        } catch (Exception e) {
            log.warn("FlatLaf 加载失败，回退到系统默认外观", e);
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                log.error("无法设置任何外观", ex);
            }
        }
    }

    /**
     * 后台预热 PlantUML，避免首次生成UML图时卡顿。
     */
    private static void prewarmPlantUml() {
        new Thread(() -> {
            try {
                log.info("正在预热 PlantUML 引擎...");
                // 简单触发 PlantUML 类加载
                Class.forName("net.sourceforge.plantuml.SourceStringReader");
                log.info("PlantUML 引擎预热完成");
            } catch (Exception e) {
                log.warn("PlantUML 预热失败（可能未安装依赖）: {}", e.getMessage());
            }
        }, "plantuml-prewarm").start();
    }
}
