package com.labreport.writer.ui;

import com.labreport.writer.model.ExperimentTask;
import com.labreport.writer.model.Project;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * ProjectPanel - 项目管理面板。
 * 新建/打开/保存项目，编辑实验任务信息。
 */
public class ProjectPanel extends JPanel {

    private final JTextField projectNameField;
    private final JTextArea taskArea;
    private final JTextArea objectivesArea;
    private final JTextArea principlesArea;
    private final JTextArea stepsArea;
    private final JTextArea analysisArea;

    private final JLabel pathLabel;
    private final JLabel statusLabel;

    private Project currentProject;

    public ProjectPanel() {
        this.currentProject = new Project("未命名项目");

        setLayout(new MigLayout("fill, insets 15, gap 10",
            "[150][grow,fill]", "[]10[]10[grow]10[]"));

        // ---- 第一行: 项目名称 + 操作按钮 ----
        add(new JLabel("项目名称:", JLabel.RIGHT));
        projectNameField = new JTextField("未命名项目");
        add(projectNameField, "split 4");
        JButton newBtn = new JButton("新建");
        add(newBtn);
        JButton openBtn = new JButton("打开");
        add(openBtn);
        JButton saveBtn = new JButton("保存");
        add(saveBtn, "wrap");

        // ---- 第二行: 项目路径 ----
        add(new JLabel("项目文件:", JLabel.RIGHT));
        pathLabel = new JLabel("（未保存）");
        pathLabel.setForeground(Color.GRAY);
        add(pathLabel, "wrap");

        // ---- 第三行: 实验任务表单 ----
        JPanel taskPanel = new JPanel(new MigLayout("fill, insets 10, gap 8",
            "[][grow,fill]", "[]10[]10[]10[]10[]10[]"));
        taskPanel.setBorder(new TitledBorder("实验任务信息"));

        taskPanel.add(new JLabel("实验标题:", JLabel.RIGHT));
        JTextField titleField = new JTextField();
        taskPanel.add(titleField, "wrap");

        taskPanel.add(new JLabel("实验任务书:", JLabel.RIGHT));
        taskArea = new JTextArea(4, 30);
        taskArea.setLineWrap(true);
        taskPanel.add(new JScrollPane(taskArea), "wrap, grow");

        taskPanel.add(new JLabel("实验目的:", JLabel.RIGHT));
        objectivesArea = new JTextArea(3, 30);
        objectivesArea.setLineWrap(true);
        taskPanel.add(new JScrollPane(objectivesArea), "wrap, grow");

        taskPanel.add(new JLabel("实验原理:", JLabel.RIGHT));
        principlesArea = new JTextArea(4, 30);
        principlesArea.setLineWrap(true);
        taskPanel.add(new JScrollPane(principlesArea), "wrap, grow");

        taskPanel.add(new JLabel("实验步骤（每行一步）:", JLabel.RIGHT));
        stepsArea = new JTextArea(5, 30);
        stepsArea.setLineWrap(true);
        taskPanel.add(new JScrollPane(stepsArea), "wrap, grow");

        taskPanel.add(new JLabel("分析要求:", JLabel.RIGHT));
        analysisArea = new JTextArea(3, 30);
        analysisArea.setLineWrap(true);
        taskPanel.add(new JScrollPane(analysisArea), "wrap, grow");

        add(taskPanel, "span 2, grow");

        // ---- 第四行: 状态 ----
        add(new JLabel());
        statusLabel = new JLabel("就绪");
        add(statusLabel, "wrap, growx");

        // ---- 事件 ----
        newBtn.addActionListener(e -> newProject());
        openBtn.addActionListener(e -> openProject());
        saveBtn.addActionListener(e -> saveProject());

        // 将项目名同步到模型
        projectNameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { syncProjectName(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { syncProjectName(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { syncProjectName(); }
        });

        // 同步任务信息到模型
        titleField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { syncTask(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { syncTask(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { syncTask(); }
        });
    }

    private void syncProjectName() {
        currentProject.setName(projectNameField.getText());
    }

    private ExperimentTask syncTask() {
        ExperimentTask task = currentProject.getTask();
        task.setRawTaskText(taskArea.getText());
        task.setObjectives(objectivesArea.getText());
        task.setPrinciples(principlesArea.getText());
        // 步骤解析
        String[] lines = stepsArea.getText().split("\n");
        task.setProcedureSteps(java.util.Arrays.asList(lines));
        task.setRequiredAnalysis(analysisArea.getText());
        return task;
    }

    private void newProject() {
        currentProject = new Project("未命名项目");
        projectNameField.setText("未命名项目");
        taskArea.setText("");
        objectivesArea.setText("");
        principlesArea.setText("");
        stepsArea.setText("");
        analysisArea.setText("");
        pathLabel.setText("（未保存）");
        statusLabel.setText("✓ 新项目已创建");
    }

    private void openProject() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "实验报告项目 (*.lrp)", "lrp"));
        chooser.setDialogTitle("打开项目");

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                currentProject = Project.load(chooser.getSelectedFile().toPath());
                loadProjectToUI();
                statusLabel.setText("✓ 项目已加载: " + currentProject.getName());
            } catch (IOException ex) {
                statusLabel.setText("✗ 加载失败: " + ex.getMessage());
                JOptionPane.showMessageDialog(this, "无法打开项目文件:\n"
                    + ex.getMessage(), "加载错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveProject() {
        if (currentProject.getProjectPath() == null) {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "实验报告项目 (*.lrp)", "lrp"));
            chooser.setDialogTitle("保存项目");
            chooser.setSelectedFile(new File(projectNameField.getText() + ".lrp"));

            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

            try {
                syncTask();
                currentProject.save(chooser.getSelectedFile().toPath());
                pathLabel.setText(currentProject.getProjectPath().toString());
                statusLabel.setText("✓ 项目已保存");
            } catch (IOException ex) {
                statusLabel.setText("✗ 保存失败: " + ex.getMessage());
            }
        } else {
            try {
                syncTask();
                currentProject.save(currentProject.getProjectPath());
                pathLabel.setText(currentProject.getProjectPath().toString());
                statusLabel.setText("✓ 项目已保存");
            } catch (IOException ex) {
                statusLabel.setText("✗ 保存失败: " + ex.getMessage());
            }
        }
    }

    private void loadProjectToUI() {
        projectNameField.setText(currentProject.getName());
        ExperimentTask task = currentProject.getTask();
        taskArea.setText(task.getRawTaskText());
        objectivesArea.setText(task.getObjectives());
        principlesArea.setText(task.getPrinciples());
        stepsArea.setText(String.join("\n", task.getProcedureSteps()));
        analysisArea.setText(task.getRequiredAnalysis());

        if (currentProject.getProjectPath() != null) {
            pathLabel.setText(currentProject.getProjectPath().toString());
        }
    }

    /** 获取当前项目 */
    public Project getProject() {
        syncTask();
        syncProjectName();
        return currentProject;
    }

    /** 设置项目（从外部加载） */
    public void setProject(Project project) {
        this.currentProject = project;
        loadProjectToUI();
    }
}
