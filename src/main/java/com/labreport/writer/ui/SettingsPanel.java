package com.labreport.writer.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.labreport.writer.model.ReportConfig;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.prefs.Preferences;

/**
 * SettingsPanel - 设置面板。
 * 主题切换、字体配置、输出路径等。
 */
public class SettingsPanel extends JPanel {

    private static final Preferences PREFS = Preferences.userNodeForPackage(SettingsPanel.class);
    private static final String KEY_THEME = "theme";

    private final JComboBox<String> themeCombo;
    private final JComboBox<String> cnFontCombo;
    private final JComboBox<Integer> fontSizeCombo;
    private final JComboBox<String> lineSpacingCombo;
    private final JTextField outputDirField;
    private final JButton outputDirBtn;
    private final JLabel statusLabel;

    private ReportConfig currentConfig;

    public SettingsPanel() {
        this.currentConfig = ReportConfig.defaults();

        setLayout(new MigLayout("fillx, insets 20, gap 10", "[right][grow,fill]", "[]10[]10[]10[]10[]10[]20[]"));

        // ---- 主题 ----
        add(new JLabel("界面主题:", JLabel.RIGHT));
        themeCombo = new JComboBox<>(new String[]{"亮色 (FlatLight)", "暗色 (FlatDark)", "混合 (IntelliJ)"});
        add(themeCombo, "wrap");

        // ---- 中文字体 ----
        add(new JLabel("中文字体:", JLabel.RIGHT));
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] fonts = ge.getAvailableFontFamilyNames();
        cnFontCombo = new JComboBox<>(new String[]{"宋体", "微软雅黑", "黑体", "楷体", "仿宋"});
        cnFontCombo.setEditable(true);
        add(cnFontCombo, "wrap");

        // ---- 字号 ----
        add(new JLabel("正文字号:", JLabel.RIGHT));
        fontSizeCombo = new JComboBox<>(new Integer[]{9, 10, 11, 12, 14, 16});
        fontSizeCombo.setSelectedItem(12);
        add(fontSizeCombo, "wrap");

        // ---- 行间距 ----
        add(new JLabel("行间距:", JLabel.RIGHT));
        lineSpacingCombo = new JComboBox<>(new String[]{"单倍 (1.0)", "1.5倍", "双倍 (2.0)"});
        lineSpacingCombo.setSelectedIndex(1);
        add(lineSpacingCombo, "wrap");

        // ---- 输出目录 ----
        add(new JLabel("默认输出目录:", JLabel.RIGHT));
        JPanel dirPanel = new JPanel(new BorderLayout(5, 0));
        outputDirField = new JTextField(
            System.getProperty("user.home") + File.separator + "实验报告输出");
        dirPanel.add(outputDirField, BorderLayout.CENTER);
        outputDirBtn = new JButton("浏览...");
        dirPanel.add(outputDirBtn, BorderLayout.EAST);
        add(dirPanel, "wrap");

        // ---- 应用按钮 ----
        add(new JLabel()); // 占位
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton applyBtn = new JButton("应用设置");
        applyBtn.addActionListener(e -> applySettings());
        btnPanel.add(applyBtn);
        statusLabel = new JLabel("  ");
        btnPanel.add(statusLabel);
        add(btnPanel, "wrap");

        // 加载已保存的设置
        loadSavedSettings();
    }

    private void applySettings() {
        String theme = (String) themeCombo.getSelectedItem();
        try {
            if (theme.contains("Dark")) {
                FlatDarkLaf.setup();
            } else if (theme.contains("IntelliJ")) {
                FlatIntelliJLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            // 更新所有窗口的外观
            SwingUtilities.updateComponentTreeUI(
                SwingUtilities.getWindowAncestor(this));
            PREFS.put(KEY_THEME, theme);
            statusLabel.setText("✓ 设置已应用");
            statusLabel.setForeground(new Color(0, 150, 0));
        } catch (Exception ex) {
            statusLabel.setText("✗ 主题切换失败: " + ex.getMessage());
            statusLabel.setForeground(Color.RED);
        }
    }

    private void loadSavedSettings() {
        String savedTheme = PREFS.get(KEY_THEME, "亮色 (FlatLight)");
        themeCombo.setSelectedItem(savedTheme);
    }

    /** 获取当前配置 */
    public ReportConfig getConfig() {
        String cnFont = (String) cnFontCombo.getSelectedItem();
        int fontSize = (Integer) fontSizeCombo.getSelectedItem();
        int lineSpacing = lineSpacingCombo.getSelectedIndex() == 0 ? 240
            : lineSpacingCombo.getSelectedIndex() == 2 ? 480 : 360;

        return new ReportConfig(cnFont, "Times New Roman",
            fontSize * 2, lineSpacing,
            "docx", "auto", true, true,
            outputDirField.getText());
    }
}
