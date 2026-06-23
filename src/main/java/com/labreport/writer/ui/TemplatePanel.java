package com.labreport.writer.ui;

import com.labreport.writer.engine.TemplateEngine;
import com.labreport.writer.model.ReportTemplate;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * TemplatePanel - 模板管理面板。
 * 浏览模板文件、提取变量、配置变量值。
 */
public class TemplatePanel extends JPanel {

    private final TemplateEngine templateEngine;

    private final JTextField templatePathField;
    private final JButton browseBtn;
    private final JButton extractBtn;
    private final JTable variableTable;
    private final DefaultTableModel tableModel;
    private final JLabel statusLabel;

    private ReportTemplate currentTemplate;

    public TemplatePanel() {
        this.templateEngine = new TemplateEngine();
        this.currentTemplate = new ReportTemplate();

        setLayout(new MigLayout("fill, insets 15, gap 10",
            "[grow]", "[][grow][][]"));

        // ---- 顶部: 模板文件选择 ----
        JPanel topPanel = new JPanel(new MigLayout("fillx, insets 0",
            "[][grow,fill][][]", "[]"));
        topPanel.add(new JLabel("DOCX模板:"));

        templatePathField = new JTextField();
        topPanel.add(templatePathField);

        browseBtn = new JButton("浏览...");
        topPanel.add(browseBtn);

        extractBtn = new JButton("提取变量");
        extractBtn.setEnabled(false);
        topPanel.add(extractBtn);

        add(topPanel, "wrap, growx");

        // ---- 中间: 变量配置表格 ----
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(new TitledBorder("模板变量配置"));

        String[] columns = {"变量名", "变量值（可编辑）"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return col == 1; // 只有"值"列可编辑
            }
        };
        variableTable = new JTable(tableModel);
        variableTable.setRowHeight(28);
        variableTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        variableTable.getColumnModel().getColumn(1).setPreferredWidth(400);
        JScrollPane tableScroll = new JScrollPane(variableTable);
        tablePanel.add(tableScroll, BorderLayout.CENTER);

        add(tablePanel, "wrap, grow");

        // ---- 底部: 状态和操作 ----
        JPanel bottomPanel = new JPanel(new MigLayout("fillx, insets 0", "[grow][]", "[]"));
        statusLabel = new JLabel("请选择DOCX模板文件");
        bottomPanel.add(statusLabel, "growx");

        JButton applyBtn = new JButton("保存变量配置");
        applyBtn.addActionListener(e -> saveVariableValues());
        bottomPanel.add(applyBtn);

        add(bottomPanel, "wrap, growx");

        // ---- 事件 ----
        browseBtn.addActionListener(e -> browseTemplate());
        extractBtn.addActionListener(e -> extractVariables());
    }

    private void browseTemplate() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Word模板 (*.docx)", "docx"));
        chooser.setDialogTitle("选择DOCX报告模板");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            templatePathField.setText(f.getAbsolutePath());
            currentTemplate.setFilePath(f.getAbsolutePath());
            currentTemplate.setDisplayName(f.getName());
            extractBtn.setEnabled(true);
            extractVariables();
        }
    }

    private void extractVariables() {
        String path = templatePathField.getText().trim();
        if (path.isEmpty()) {
            statusLabel.setText("请先选择模板文件");
            return;
        }

        try {
            statusLabel.setText("正在提取变量...");
            List<String> vars = templateEngine.extractVariables(Path.of(path));
            currentTemplate.setVariables(vars);

            // 更新表格
            tableModel.setRowCount(0);
            for (String v : vars) {
                String existingValue = currentTemplate.getVariableValues().getOrDefault(v, "");
                tableModel.addRow(new Object[]{v, existingValue});
            }

            int configured = (int) vars.stream()
                .filter(v -> currentTemplate.getVariableValues().containsKey(v)
                    && !currentTemplate.getVariableValues().get(v).isBlank())
                .count();

            statusLabel.setText(String.format("✓ 发现 %d 个变量 (%d 已配置)", vars.size(), configured));
        } catch (IOException ex) {
            statusLabel.setText("✗ 提取失败: " + ex.getMessage());
            tableModel.setRowCount(0);
        }
    }

    private void saveVariableValues() {
        ReportTemplate template = getTemplate();
        statusLabel.setText("✓ 变量配置已保存 (" +
            template.getVariableValues().size() + " 个变量)");
    }

    /** 获取当前模板配置 */
    public ReportTemplate getTemplate() {
        // 从表格同步到模型
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String varName = (String) tableModel.getValueAt(i, 0);
            String value = (String) tableModel.getValueAt(i, 1);
            if (varName != null && value != null && !value.isBlank()) {
                currentTemplate.setVariableValue(varName, value);
            }
        }
        return currentTemplate;
    }

    public void setTemplate(ReportTemplate template) {
        this.currentTemplate = template;
        if (template.isLoaded()) {
            templatePathField.setText(template.getFilePath());
            extractBtn.setEnabled(true);
            // 更新表格
            tableModel.setRowCount(0);
            for (String v : template.getVariables()) {
                tableModel.addRow(new Object[]{v, template.getVariableValues().getOrDefault(v, "")});
            }
        }
    }
}
