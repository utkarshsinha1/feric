package com.classroom.live;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

public final class TerminalWorkspace extends JPanel {
    private final Supplier<Path> workingDirectoryProvider;
    private final JTabbedPane sessions = new JTabbedPane();
    private int nextSession = 1;
    private TerminalPanel runningTerminal;

    public TerminalWorkspace(Supplier<Path> workingDirectoryProvider) {
        super(new BorderLayout());
        this.workingDirectoryProvider = workingDirectoryProvider;
        setBackground(IdeTheme.PANEL);
        setPreferredSize(new Dimension(700, 270));
        setMinimumSize(new Dimension(350, 120));

        sessions.setBackground(IdeTheme.PANEL);
        sessions.setForeground(IdeTheme.TEXT);
        sessions.setOpaque(true);
        sessions.setBorder(BorderFactory.createEmptyBorder());
        sessions.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        sessions.setTabPlacement(javax.swing.JTabbedPane.TOP);

        JButton newTerminal = new JButton("+ New Terminal");
        IdeTheme.styleButton(newTerminal);
        JButton latestOutput = new JButton("↓");
        IdeTheme.styleButton(latestOutput);
        latestOutput.setToolTipText("Scroll to latest output");
        latestOutput.getAccessibleContext().setAccessibleName("Scroll to latest output");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setBackground(IdeTheme.PANEL);
        actions.add(latestOutput);
        actions.add(newTerminal);
        JPanel sessionBar = new JPanel(new BorderLayout());
        sessionBar.setBackground(IdeTheme.PANEL);
        sessionBar.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
        sessionBar.add(actions, BorderLayout.EAST);
        add(sessionBar, BorderLayout.NORTH);
        add(sessions, BorderLayout.CENTER);
        addNewSession(false);
        newTerminal.addActionListener(event -> addNewSession());
        latestOutput.addActionListener(event -> {
            TerminalPanel terminal = activeTerminal();
            if (terminal != null) {
                terminal.scrollToLatestOutput();
            }
        });
    }

    public TerminalPanel activeTerminal() {
        return (TerminalPanel) sessions.getSelectedComponent();
    }

    public void runJavaProject(Map<String, String> sourceFiles, String mainClass,
                               Runnable onFinished) {
        TerminalPanel terminal = activeTerminal();
        if (terminal != null) {
            runningTerminal = terminal;
            terminal.runJavaProject(sourceFiles, mainClass, () -> {
                if (runningTerminal == terminal) {
                    runningTerminal = null;
                }
                if (onFinished != null) {
                    onFinished.run();
                }
            });
        }
    }

    public void runProgram(
            String source, String language, String className, Runnable onFinished) {
        TerminalPanel terminal = activeTerminal();
        if (terminal != null) {
            runningTerminal = terminal;
            terminal.runProgram(source, language, className, () -> {
                if (runningTerminal == terminal) {
                    runningTerminal = null;
                }
                if (onFinished != null) {
                    onFinished.run();
                }
            });
        }
    }

    public void startActiveShell() {
        TerminalPanel terminal = activeTerminal();
        if (terminal != null) {
            terminal.startShell();
        }
    }

    public void stopActiveProgram() {
        TerminalPanel terminal = runningTerminal;
        if (terminal != null) {
            terminal.stopProgram();
        }
    }

    private void closeSession(TerminalPanel terminal) {
        int index = sessions.indexOfComponent(terminal);
        if (index < 0) {
            return;
        }
        sessions.removeTabAt(index);
        if (runningTerminal == terminal) {
            runningTerminal = null;
        }
        terminal.closeTerminal();
    }

    private void addNewSession() {
        addNewSession(true);
    }

    private void addNewSession(boolean startImmediately) {
        TerminalPanel terminal = new TerminalPanel(workingDirectoryProvider, startImmediately);
        String title = "Terminal " + nextSession++;
        sessions.addTab(title, terminal);
        sessions.setTabComponentAt(
            sessions.indexOfComponent(terminal), createTabHeader(title, terminal));
        sessions.setSelectedComponent(terminal);
    }

    private JPanel createTabHeader(String title, TerminalPanel terminal) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        header.setOpaque(false);
        JLabel label = new JLabel(title);
        label.setForeground(IdeTheme.TEXT);
        header.add(label);

        JButton close = new JButton("×");
        close.setFont(IdeTheme.UI_FONT.deriveFont(15f));
        close.setForeground(IdeTheme.MUTED);
        close.setContentAreaFilled(false);
        close.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3));
        close.setFocusPainted(false);
        close.setToolTipText("Close " + title);
        close.getAccessibleContext().setAccessibleName("Close " + title);
        close.addActionListener(event -> closeSession(terminal));
        header.add(close);
        return header;
    }
}
