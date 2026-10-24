package com.classroom.live;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTabbedPane;
import javax.swing.plaf.basic.BasicButtonUI;
import java.util.function.Supplier;
import java.util.Map;
import java.nio.file.Path;

public class CompilerPanel {
    private CompilerPanel() {}

    public static void shutdown() {
        TerminalPanel.shutdownAll();
    }

    public static JPanel create(JTextArea editor) {
        return create(() -> editor);
    }

    public static JPanel create(Supplier<JTextArea> editorProvider) {
        return create(editorProvider, null, TerminalPanel::defaultWorkingDirectory);
    }

    public static JPanel create(
            Supplier<JTextArea> editorProvider,
            Supplier<Map<String, String>> javaWorkspaceProvider) {
        return create(editorProvider, javaWorkspaceProvider,
            TerminalPanel::defaultWorkingDirectory);
    }

    public static JPanel create(
            Supplier<JTextArea> editorProvider,
            Supplier<Map<String, String>> javaWorkspaceProvider,
            Supplier<Path> workingDirectoryProvider) {
        JTextField className = new JTextField("Main", 8);
        JComboBox<String> language = new JComboBox<>(
            new String[] {"Java", "C", "C++", "C#", "Python", "JavaScript"}
        );
        JButton runButton = new JButton("▶  Run in Terminal");
        JButton stopButton = new JButton("Stop");

        className.setPreferredSize(new Dimension(110, 29));
        styleField(className);
        styleCombo(language);
        runButton.setUI(new BasicButtonUI());
        runButton.setBackground(IdeTheme.ACCENT);
        runButton.setForeground(IdeTheme.BACKGROUND);
        runButton.setOpaque(true);
        runButton.setContentAreaFilled(true);
        runButton.setBorderPainted(false);
        runButton.setFocusPainted(false);
        runButton.setFont(IdeTheme.UI_FONT.deriveFont(Font.BOLD));
        runButton.setBorder(BorderFactory.createEmptyBorder(7, 13, 7, 13));
        IdeTheme.styleButton(stopButton);
        stopButton.setEnabled(false);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 7));
        toolbar.setBackground(IdeTheme.PANEL);
        toolbar.setBorder(BorderFactory.createMatteBorder(
            0, 0, 1, 0, IdeTheme.BORDER
        ));
        JLabel languageLabel = new JLabel("Language");
        languageLabel.setForeground(IdeTheme.MUTED);
        JLabel classNameLabel = new JLabel("Main class");
        classNameLabel.setForeground(IdeTheme.MUTED);
        toolbar.add(languageLabel);
        toolbar.add(language);
        toolbar.add(classNameLabel);
        toolbar.add(className);
        toolbar.add(runButton);
        toolbar.add(stopButton);

        JLabel runHint = new JLabel(
            "Run code in the integrated terminal. Type directly there; use Ctrl+C or Stop to "
                + "interrupt a running program.");
        runHint.setForeground(IdeTheme.MUTED);
        runHint.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JPanel runBody = new JPanel(new BorderLayout());
        runBody.setBackground(IdeTheme.PANEL);
        runBody.add(runHint, BorderLayout.NORTH);
        JLabel emptySpace = new JLabel("Select a language and run the current editor contents.");
        emptySpace.setForeground(IdeTheme.MUTED);
        emptySpace.setHorizontalAlignment(JLabel.CENTER);
        runBody.add(emptySpace, BorderLayout.CENTER);
        language.addActionListener(event -> {
            boolean isJava = "Java".equals(language.getSelectedItem());
            className.setEnabled(isJava);
            className.setToolTipText(isJava ? "Java main class name" : "Only used for Java");
        });

        JPanel runPanel = new JPanel(new BorderLayout());
        runPanel.setBackground(IdeTheme.PANEL);
        runPanel.setBorder(BorderFactory.createMatteBorder(
            1, 0, 0, 0, IdeTheme.BORDER
        ));
        runPanel.add(toolbar, BorderLayout.NORTH);
        runPanel.add(runBody, BorderLayout.CENTER);

        TerminalWorkspace terminal = new TerminalWorkspace(workingDirectoryProvider);
        JTabbedPane panels = new JTabbedPane();
        panels.setBackground(IdeTheme.PANEL);
        panels.setForeground(IdeTheme.TEXT);
        panels.setOpaque(true);
        panels.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, IdeTheme.BORDER));
        panels.addTab("Run", runPanel);
        panels.addTab("Terminal", terminal);
        panels.addChangeListener(event -> {
            if (panels.getSelectedComponent() == terminal) {
                terminal.startActiveShell();
            }
        });

        runButton.addActionListener(event -> {
            String selectedLanguage = (String) language.getSelectedItem();
            JTextArea editor = editorProvider.get();
            String code = editor.getText();
            String name = className.getText().trim();
            if ("Java".equals(selectedLanguage) && "Main".equals(name)
                    && editor instanceof CodeEditor codeEditor) {
                String fileName = codeEditor.getFileName();
                if (fileName != null && fileName.toLowerCase().endsWith(".java")) {
                    int separator = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
                    String baseName = fileName.substring(separator + 1);
                    name = baseName.substring(0, baseName.length() - ".java".length());
                }
            }
            String classNameToRun = name;
            Map<String, String> javaWorkspace = "Java".equals(selectedLanguage)
                    && javaWorkspaceProvider != null ? javaWorkspaceProvider.get() : null;

            runButton.setEnabled(false);
            runButton.setText("Running…");
            stopButton.setEnabled(true);

            if ("Java".equals(selectedLanguage)
                    && javaWorkspace != null && !javaWorkspace.isEmpty()) {
                Map<String, String> javaSources = new java.util.LinkedHashMap<>(javaWorkspace);
                String sourcePath = editor instanceof CodeEditor codeEditor
                        ? codeEditor.getFileName() : null;
                if (sourcePath == null || sourcePath.isBlank()) {
                    sourcePath = classNameToRun + ".java";
                }
                javaSources.put(sourcePath, code);
                panels.setSelectedComponent(terminal);
                terminal.runJavaProject(javaSources, classNameToRun, () -> {
                    runButton.setText("▶  Run in Terminal");
                    runButton.setEnabled(true);
                    stopButton.setEnabled(false);
                });
                return;
            }

            panels.setSelectedComponent(terminal);
            terminal.runProgram(code, selectedLanguage, classNameToRun, () -> {
                runButton.setText("▶  Run in Terminal");
                runButton.setEnabled(true);
                stopButton.setEnabled(false);
            });
        });
        stopButton.addActionListener(event -> terminal.stopActiveProgram());
        JPanel container = new JPanel(new BorderLayout());
        container.add(panels, BorderLayout.CENTER);
        return container;
    }

    private static void styleField(JTextField field) {
        field.setBackground(IdeTheme.EDITOR);
        field.setForeground(IdeTheme.TEXT);
        field.setCaretColor(IdeTheme.TEXT);
        field.setSelectionColor(IdeTheme.SELECTION);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(IdeTheme.BORDER),
            BorderFactory.createEmptyBorder(4, 7, 4, 7)
        ));
    }

    private static void styleCombo(JComboBox<String> combo) {
        IdeTheme.styleComboBox(combo);
    }
}
