package com.labreport.writer.ui;

import com.labreport.writer.util.FileClassifier;
import com.labreport.writer.util.FileClassifier.ClassificationResult;
import com.labreport.writer.util.FileClassifier.StandardFolder;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * InputPanel - 材料输入面板。
 * 支持文件浏览和拖拽放入，自动分类展示。
 */
public class InputPanel extends JPanel {

    private final JTextField dirPathField;
    private final JButton browseBtn;
    private final JButton classifyBtn;
    private final JTextArea resultArea;
    private final JLabel statusLabel;

    private Path currentDir;
    private ClassificationResult lastResult;

    public InputPanel() {
        setLayout(new MigLayout("fill, insets 15, gap 10",
            "[grow]", "[][grow][]"));

        // ---- 顶部: 文件夹选择 ----
        JPanel topPanel = new JPanel(new MigLayout("fillx, insets 0",
            "[][grow,fill][][]", "[]"));
        topPanel.add(new JLabel("输入文件夹:"));

        dirPathField = new JTextField();
        topPanel.add(dirPathField);

        browseBtn = new JButton("浏览...");
        topPanel.add(browseBtn);

        classifyBtn = new JButton("自动分类");
        classifyBtn.setEnabled(false);
        topPanel.add(classifyBtn);

        add(topPanel, "wrap, growx");

        // ---- 中间: 分类结果展示 ----
        JPanel resultPanel = new JPanel(new BorderLayout());
        resultPanel.setBorder(new TitledBorder("文件分类结果"));

        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        resultArea.setText("请选择输入文件夹并点击\"自动分类\"，\n或直接将文件夹拖拽到此区域。\n\n");
        JScrollPane scrollPane = new JScrollPane(resultArea);
        resultPanel.add(scrollPane, BorderLayout.CENTER);

        add(resultPanel, "wrap, grow");

        // ---- 底部: 状态 ----
        statusLabel = new JLabel("就绪");
        add(statusLabel, "wrap, growx");

        // ---- 事件绑定 ----
        browseBtn.addActionListener(e -> browseFolder());
        classifyBtn.addActionListener(e -> classifyFolder());

        // ---- 拖拽支持 ----
        setupDragAndDrop();
    }

    private void browseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择输入文件夹（标准模板文件夹布局）");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            dirPathField.setText(selected.getAbsolutePath());
            currentDir = selected.toPath();
            classifyBtn.setEnabled(true);
            classifyFolder();
        }
    }

    private void classifyFolder() {
        String path = dirPathField.getText().trim();
        if (path.isEmpty()) {
            statusLabel.setText("请先选择文件夹");
            return;
        }
        currentDir = Path.of(path);
        if (!currentDir.toFile().exists()) {
            statusLabel.setText("✗ 文件夹不存在: " + path);
            return;
        }

        try {
            statusLabel.setText("正在分类...");
            lastResult = FileClassifier.classify(currentDir);
            displayResult(lastResult);
            statusLabel.setText("✓ 分类完成: " + lastResult.totalFilesFound + " 个文件");
        } catch (IOException ex) {
            statusLabel.setText("✗ 分类失败: " + ex.getMessage());
            resultArea.setText("错误: " + ex.getMessage());
        }
    }

    private void displayResult(ClassificationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("========== 文件分类结果 ==========\n\n");

        for (StandardFolder sf : StandardFolder.values()) {
            List<Path> files = result.getFiles(sf);
            int count = files.size();
            String status = (sf.required && count == 0) ? " ⚠ 缺失(必需)" : " ✓";
            sb.append(String.format("  %-14s : %d 个文件 %s\n", sf.displayName, count, status));

            for (Path f : files) {
                sb.append(String.format("    · %s\n", f.getFileName()));
            }
            sb.append("\n");
        }

        if (!result.unclassified.isEmpty()) {
            sb.append("--- 未分类文件 ---\n");
            for (Path f : result.unclassified) {
                sb.append(String.format("  ? %s\n", f.getFileName()));
            }
            sb.append("\n");
        }

        sb.append(String.format("总计: %d 个文件\n", result.totalFilesFound));

        if (!result.isComplete()) {
            sb.append("\n⚠ 缺失以下必需内容:\n");
            for (StandardFolder sf : result.missing) {
                sb.append("  - ").append(sf.displayName).append("\n");
            }
        }

        sb.append("===================================\n");
        resultArea.setText(sb.toString());
        resultArea.setCaretPosition(0);
    }

    /** 设置拖拽支持 */
    private void setupDragAndDrop() {
        DropTarget target = new DropTarget(resultArea, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    java.util.List<File> files = (java.util.List<File>)
                        dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);

                    if (!files.isEmpty()) {
                        File file = files.get(0);
                        if (file.isFile()) file = file.getParentFile();
                        dirPathField.setText(file.getAbsolutePath());
                        currentDir = file.toPath();
                        classifyBtn.setEnabled(true);
                        classifyFolder();
                    }
                    dtde.dropComplete(true);
                } catch (Exception e) {
                    statusLabel.setText("✗ 拖拽失败: " + e.getMessage());
                }
            }
        });
        resultArea.setDropTarget(target);
    }

    // ---- 公共访问方法 ----

    public Path getCurrentDir() { return currentDir; }
    public ClassificationResult getLastResult() { return lastResult; }

    /** 将分类结果填充到项目中 */
    public void applyToProject(com.labreport.writer.model.Project project) {
        if (currentDir != null) {
            project.setInputDir(currentDir);
        }
        if (lastResult != null) {
            java.util.Map<String, String> classified = new java.util.LinkedHashMap<>();
            for (StandardFolder sf : StandardFolder.values()) {
                if (lastResult.hasFiles(sf)) {
                    Path p = lastResult.getFiles(sf).get(0);
                    classified.put(sf.displayName, p.toString());
                }
            }
            project.setClassifiedFiles(classified);
        }
    }
}
