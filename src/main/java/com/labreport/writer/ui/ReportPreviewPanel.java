package com.labreport.writer.ui;

import com.labreport.writer.engine.EvidenceManager;
import com.labreport.writer.model.ExperimentTask;
import com.labreport.writer.model.Project;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Map;

/**
 * ReportPreviewPanel - 报告预览面板。
 * 在生成前展示报告结构和各章节的证据来源。
 */
public class ReportPreviewPanel extends JPanel {

    private final JEditorPane previewPane;
    private final JLabel statusLabel;

    public ReportPreviewPanel() {
        setLayout(new MigLayout("fill, insets 15, gap 10",
            "[grow]", "[][grow]"));

        // ---- 标题 ----
        JLabel titleLabel = new JLabel("报告结构预览");
        titleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        add(titleLabel, "wrap");

        // ---- HTML预览区 ----
        previewPane = new JEditorPane();
        previewPane.setContentType("text/html");
        previewPane.setEditable(false);
        previewPane.setText(buildDefaultHtml());
        JScrollPane scrollPane = new JScrollPane(previewPane);
        add(scrollPane, "wrap, grow");

        // ---- 状态 ----
        statusLabel = new JLabel("请先加载实验材料和任务书");
        add(statusLabel, "wrap, growx");
    }

    /** 根据项目更新预览 */
    public void updatePreview(Project project, EvidenceManager evidenceManager) {
        ExperimentTask task = project.getTask();
        if (task == null || !task.isValid()) {
            previewPane.setText(buildDefaultHtml());
            statusLabel.setText("⚠ 请先填写实验任务信息");
            return;
        }

        StringBuilder html = new StringBuilder();
        html.append("""
            <html><body style='font-family: Microsoft YaHei, SimSun, sans-serif; padding: 15px;'>
            <h2 style='color: #2196F3;'>""").append(escapeHtml(task.getTitle())).append("</h2>");

        // 基本信息
        html.append("<h3>📋 基本信息</h3><table style='width:100%; border-collapse:collapse;'>");
        Map<String, String> info = project.getPersonalInfo();
        for (Map.Entry<String, String> e : info.entrySet()) {
            String val = e.getValue().isBlank() ? "【" + e.getKey() + "】" : e.getValue();
            html.append("<tr><td style='padding:4px; border:1px solid #ddd; width:120px;'><b>")
                .append(escapeHtml(e.getKey())).append("</b></td>")
                .append("<td style='padding:4px; border:1px solid #ddd;'>")
                .append(escapeHtml(val)).append("</td></tr>");
        }
        html.append("</table>");

        // 实验内容
        html.append("<h3>🎯 实验目的</h3><p>")
            .append(escapeHtml(task.getObjectives().isBlank()
                ? "（待填写）" : task.getObjectives()))
            .append("</p>");

        html.append("<h3>📝 实验步骤</h3><ol>");
        if (task.getProcedureSteps().isEmpty()) {
            html.append("<li>（待填写）</li>");
        } else {
            for (String step : task.getProcedureSteps()) {
                html.append("<li>").append(escapeHtml(step)).append("</li>");
            }
        }
        html.append("</ol>");

        // 证据模式
        if (evidenceManager != null) {
            html.append("<h3>📊 证据来源计划</h3><table style='width:80%; border-collapse:collapse;'>");
            html.append("<tr style='background:#e3f2fd;'><th style='padding:6px; border:1px solid #ddd;'>章节</th>")
                .append("<th style='padding:6px; border:1px solid #ddd;'>数据来源</th>")
                .append("<th style='padding:6px; border:1px solid #ddd;'>原因</th></tr>");
            Map<String, EvidenceManager.SectionEvidence> plan =
                evidenceManager.planEvidence(task);
            for (EvidenceManager.SectionEvidence se : plan.values()) {
                String color = switch (se.mode()) {
                    case REAL_DATA -> "#4CAF50";
                    case LOCAL_REPRODUCE -> "#2196F3";
                    case SIMULATED -> "#FF9800";
                    case PLACEHOLDER -> "#F44336";
                    default -> "#9E9E9E";
                };
                html.append("<tr><td style='padding:4px; border:1px solid #ddd;'>")
                    .append(escapeHtml(se.sectionName()))
                    .append("</td><td style='padding:4px; border:1px solid #ddd; color:")
                    .append(color).append(";'>")
                    .append(escapeHtml(se.mode().displayName))
                    .append("</td><td style='padding:4px; border:1px solid #ddd; font-size:11px;'>")
                    .append(escapeHtml(se.reason())).append("</td></tr>");
            }
            html.append("</table>");
        }

        html.append("</body></html>");
        previewPane.setText(html.toString());
        previewPane.setCaretPosition(0);
        statusLabel.setText(String.format("✓ 报告包含 %d 个章节", 7));
    }

    private String buildDefaultHtml() {
        return """
            <html><body style='font-family: Microsoft YaHei, SimSun, sans-serif; padding: 20px; text-align: center; color: #999;'>
            <h2>📝 报告结构预览</h2>
            <p>请先在"项目管理"中加载实验材料，<br>
            在"材料输入"中配置输入文件夹，<br>
            然后点击"生成报告"即可在此预览。</p>
            <br>
            <p style='font-size:11px;'>默认报告结构：封面 → 目的 → 原理 → 环境 → 步骤 → 结果 → 分析 → 总结</p>
            </body></html>
            """;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;")
                   .replace(">", "&gt;").replace("\n", "<br>");
    }
}
