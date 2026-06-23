package com.labreport.writer.ui;

import com.labreport.writer.engine.CodeAnalyzer;
import com.labreport.writer.engine.UmlGenerator;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UmlPanel - UML图编辑器面板。
 * 左侧：PlantUML DSL代码编辑
 * 右侧：渲染后的UML图预览
 * 支持实时预览（防抖500ms）和从.class文件自动生成类图。
 */
public class UmlPanel extends JPanel {

    private final UmlGenerator umlGenerator;
    private final CodeAnalyzer codeAnalyzer;

    private final JTextArea editorArea;
    private final JLabel previewLabel;
    private final JScrollPane previewScroll;
    private final JLabel statusLabel;
    private final JButton renderBtn;
    private final JButton importClassBtn;
    private final JButton saveBtn;

    /** 防抖定时器 */
    private final Timer debounceTimer;

    /** 生成计数器（用于追踪异步请求） */
    private final AtomicInteger generationId = new AtomicInteger(0);

    /** 默认示例DSL */
    private static final String DEFAULT_DSL = """
        @startuml
        skinparam backgroundColor #FEFEFE
        skinparam classBorderColor #2196F3

        class Student {
            - id: Long
            - name: String
            - grade: Integer
            + getId(): Long
            + getName(): String
            + getGrade(): Integer
        }

        class Course {
            - courseId: String
            - courseName: String
            - credits: Integer
            + getCourseId(): String
            + getCourseName(): String
        }

        Student "n" -- "m" Course : enrolls >
        @enduml
        """;

    public UmlPanel() {
        this.umlGenerator = new UmlGenerator();
        this.codeAnalyzer = new CodeAnalyzer();

        setLayout(new MigLayout("fill, insets 15, gap 10",
            "[grow][grow]", "[][grow][]"));

        // ---- 工具栏 ----
        JPanel toolbar = new JPanel(new MigLayout("insets 0", "[][][][][grow]", "[]"));

        renderBtn = new JButton("▶ 渲染预览");
        toolbar.add(renderBtn);

        importClassBtn = new JButton("📂 导入.class");
        toolbar.add(importClassBtn);

        saveBtn = new JButton("💾 保存PNG");
        toolbar.add(saveBtn);

        statusLabel = new JLabel("就绪");
        toolbar.add(statusLabel, "gap 20, growx");

        add(toolbar, "span 2, wrap, growx");

        // ---- 左侧: PlantUML代码编辑 ----
        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.setBorder(new TitledBorder("PlantUML 代码"));

        editorArea = new JTextArea();
        editorArea.setText(DEFAULT_DSL);
        editorArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        editorArea.setTabSize(4);
        JScrollPane editorScroll = new JScrollPane(editorArea);
        editorPanel.add(editorScroll, BorderLayout.CENTER);

        add(editorPanel, "grow");

        // ---- 右侧: UML图预览 ----
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(new TitledBorder("UML 图预览"));

        previewLabel = new JLabel("点击\"渲染预览\"查看UML图", SwingConstants.CENTER);
        previewLabel.setBackground(Color.WHITE);
        previewLabel.setOpaque(true);
        previewScroll = new JScrollPane(previewLabel);
        previewScroll.setBackground(Color.WHITE);
        previewPanel.add(previewScroll, BorderLayout.CENTER);

        add(previewPanel, "grow");

        // ---- 事件 ----
        renderBtn.addActionListener(e -> renderDiagram());
        importClassBtn.addActionListener(e -> importClassFile());
        saveBtn.addActionListener(e -> saveDiagram());

        // 实时预览：防抖500ms
        debounceTimer = new Timer(500, e -> renderDiagram());
        debounceTimer.setRepeats(false);

        editorArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { debounceTimer.restart(); }
            @Override public void removeUpdate(DocumentEvent e) { debounceTimer.restart(); }
            @Override public void changedUpdate(DocumentEvent e) { debounceTimer.restart(); }
        });
    }

    /** 渲染PlantUML图为PNG预览 */
    private void renderDiagram() {
        String text = editorArea.getText();
        if (!UmlGenerator.isValidSyntax(text)) {
            statusLabel.setText("⚠ DSL语法不完整（缺少@startuml/@enduml）");
            return;
        }

        int currentId = generationId.incrementAndGet();
        statusLabel.setText("渲染中...");
        renderBtn.setEnabled(false);

        // 在SwingWorker中异步渲染
        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() throws Exception {
                return umlGenerator.generateToImage(text);
            }

            @Override
            protected void done() {
                renderBtn.setEnabled(true);
                if (generationId.get() != currentId) return; // 过期请求

                try {
                    BufferedImage img = get();
                    if (img != null) {
                        ImageIcon icon = new ImageIcon(img);
                        previewLabel.setIcon(icon);
                        previewLabel.setText(null);
                        statusLabel.setText(String.format("✓ 渲染完成 (%dx%d)",
                            img.getWidth(), img.getHeight()));
                    }
                } catch (Exception e) {
                    previewLabel.setIcon(null);
                    previewLabel.setText("✗ 渲染失败: " + e.getMessage());
                    statusLabel.setText("✗ 渲染失败");
                }
            }
        }.execute();
    }

    /** 导入.class文件并自动生成类图 */
    private void importClassFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Java Class/JAR (*.class, *.jar)", "class", "jar"));
        chooser.setDialogTitle("选择.class文件、目录或JAR包");

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            statusLabel.setText("分析中...");

            new SwingWorker<List<CodeAnalyzer.ClassInfo>, Void>() {
                @Override
                protected List<CodeAnalyzer.ClassInfo> doInBackground() throws Exception {
                    Path path = f.toPath();
                    if (f.isDirectory()) {
                        return codeAnalyzer.analyzeDirectory(path);
                    } else if (f.getName().endsWith(".jar")) {
                        return codeAnalyzer.analyzeJar(path);
                    } else if (f.getName().endsWith(".class")) {
                        CodeAnalyzer.ClassInfo ci = codeAnalyzer.analyzeClassFile(path);
                        return List.of(ci);
                    }
                    return List.of();
                }

                @Override
                protected void done() {
                    try {
                        List<CodeAnalyzer.ClassInfo> classes = get();
                        if (classes.isEmpty()) {
                            statusLabel.setText("未发现.class文件");
                            return;
                        }
                        String plantUml = codeAnalyzer.generatePlantUml(classes);
                        editorArea.setText(plantUml);
                        statusLabel.setText(String.format("✓ 分析了 %d 个类", classes.size()));
                        renderDiagram();
                    } catch (Exception e) {
                        statusLabel.setText("✗ 分析失败: " + e.getMessage());
                    }
                }
            }.execute();
        }
    }

    /** 保存UML图为PNG */
    private void saveDiagram() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "PNG图片 (*.png)", "png"));
        chooser.setDialogTitle("保存UML图");
        chooser.setSelectedFile(new File("uml-diagram.png"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                umlGenerator.generateToFile(editorArea.getText(),
                    chooser.getSelectedFile().toPath());
                statusLabel.setText("✓ 已保存: " + chooser.getSelectedFile().getName());
            } catch (IOException ex) {
                statusLabel.setText("✗ 保存失败: " + ex.getMessage());
            }
        }
    }

    /** 获取当前的PlantUML DSL文本 */
    public String getPlantUmlText() {
        return editorArea.getText();
    }
}
