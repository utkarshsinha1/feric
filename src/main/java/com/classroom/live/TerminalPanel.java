package com.classroom.live;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public final class TerminalPanel extends JPanel {
    private static final ExecutorService WORKERS = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "feric-pty-terminal");
        thread.setDaemon(true);
        return thread;
    });
    private static final Set<TerminalPanel> OPEN_TERMINALS =
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final String RUN_FINISHED = "__FERIC_RUN_FINISHED_";
    private static final long STOP_GRACE_MILLIS = 2_000;

    private final Supplier<Path> workingDirectoryProvider;
    private volatile JediTermWidget terminal = createTerminalWidget();
    private final JLabel startupMessage = new JLabel("Starting terminal…");
    private final Object programLock = new Object();
    private volatile PtyProcess process;
    private volatile boolean shellStarting;
    private volatile boolean programRunning;
    private volatile boolean cancelRequested;
    private volatile String activeMarker;
    private volatile boolean closed;

    public TerminalPanel(Supplier<Path> workingDirectoryProvider) {
        this(workingDirectoryProvider, true);
    }

    TerminalPanel(Supplier<Path> workingDirectoryProvider, boolean startImmediately) {
        super(new BorderLayout());
        this.workingDirectoryProvider = workingDirectoryProvider;
        OPEN_TERMINALS.add(this);
        setBackground(new Color(24, 24, 24));
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        terminal.setBackground(new Color(24, 24, 24));
        terminal.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        terminal.setVisible(false);
        startupMessage.setForeground(IdeTheme.MUTED);
        startupMessage.setHorizontalAlignment(JLabel.CENTER);
        add(startupMessage, BorderLayout.CENTER);
        add(terminal, BorderLayout.CENTER);
        if (startImmediately) {
            startShell();
        }
    }

    public static Path defaultWorkingDirectory() {
        return Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
    }

    public static void shutdownAll() {
        for (TerminalPanel terminal : OPEN_TERMINALS) {
            terminal.closeTerminal();
        }
        OPEN_TERMINALS.clear();
    }

    public void startShell() {
        synchronized (this) {
            if (closed || shellStarting || process != null) {
                return;
            }
            shellStarting = true;
        }
        WORKERS.execute(() -> {
            PtyProcess started = null;
            try {
                Path directory = workingDirectoryProvider.get();
                if (directory == null || !Files.isDirectory(directory)) {
                    throw new IOException("Terminal working directory is unavailable: " + directory);
                }
                List<String> command = shellCommand();
                Map<String, String> environment = terminalEnvironment(System.getenv());
                environment.put("TERM", "xterm-256color");
                environment.put("COLORTERM", "truecolor");
                started = new PtyProcessBuilder(command.toArray(String[]::new))
                    .setDirectory(directory.toString())
                    .setEnvironment(environment)
                    .setInitialColumns(100)
                    .setInitialRows(30)
                    .start();
                PtyProcess shell = started;
                ProcessTtyConnector connector = connector(shell, command);
                SwingUtilities.invokeLater(() -> {
                    if (closed) {
                        destroy(shell);
                        return;
                    }
                    process = shell;
                    terminal.setTtyConnector(connector);
                    terminal.start();
                    terminal.getTerminalPanel().setCursorShape(CursorShape.STEADY_VERTICAL_BAR);
                    remove(startupMessage);
                    terminal.setVisible(true);
                    revalidate();
                    repaint();
                    terminal.requestFocusInWindow();
                });
                started.waitFor();
            } catch (IOException exception) {
                showStartupError("Could not start terminal: " + exception.getMessage());
            } catch (RuntimeException exception) {
                showStartupError("Could not prepare terminal workspace: " + exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                showStartupError("Terminal startup was interrupted.");
            } finally {
                if (started != null) {
                    PtyProcess ended = started;
                    SwingUtilities.invokeLater(() -> {
                        if (process == ended) {
                            process = null;
                            if (!closed) {
                                replaceTerminalWidget();
                                startShell();
                            }
                        }
                    });
                }
                shellStarting = false;
            }
        });
    }

    public void runJavaProject(
            Map<String, String> sourceFiles, String mainClass, Runnable onFinished) {
        if (!beginProgram()) {
            return;
        }
        WORKERS.execute(() -> {
            JavaRunner.PreparedProgram prepared = null;
            try {
                prepared = JavaRunner.prepareJavaProject(
                    sourceFiles, mainClass, workingDirectoryProvider.get());
                submitProgram(prepared, onFinished);
                prepared = null;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                submitError("\nJava program preparation was interrupted.\n");
                finishProgram(onFinished);
            } catch (IOException | RuntimeException exception) {
                submitError("\n" + exception.getMessage() + "\n");
                finishProgram(onFinished);
            } finally {
                if (prepared != null) {
                    prepared.close();
                }
            }
        });
    }

    public void runProgram(
            String source, String language, String className, Runnable onFinished) {
        if (!beginProgram()) {
            return;
        }
        WORKERS.execute(() -> {
            JavaRunner.PreparedProgram prepared = null;
            try {
                prepared = JavaRunner.prepareProgram(
                    source, language, className, workingDirectoryProvider.get());
                submitProgram(prepared, onFinished);
                prepared = null;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                submitError("\nProgram preparation was interrupted.\n");
                finishProgram(onFinished);
            } catch (IOException | RuntimeException exception) {
                submitError("\n" + exception.getMessage() + "\n");
                finishProgram(onFinished);
            } finally {
                if (prepared != null) {
                    prepared.close();
                }
            }
        });
    }

    JediTermWidget terminalWidget() {
        return terminal;
    }

    boolean isProgramRunning() {
        return programRunning;
    }

    String terminalTranscript() {
        TerminalTextBuffer buffer = terminal.getTerminalTextBuffer();
        return buffer.getHistoryBuffer().getLines() + "\n" + buffer.getScreenLines();
    }

    void scrollToLatestOutput() {
        terminal.getTerminalPanel().getVerticalScrollModel().setValue(0);
    }

    private boolean beginProgram() {
        synchronized (programLock) {
            if (closed || programRunning) {
                return false;
            }
            programRunning = true;
            cancelRequested = false;
            activeMarker = null;
        }
        startShell();
        return true;
    }

    private void submitProgram(
            JavaRunner.PreparedProgram prepared, Runnable onFinished) throws IOException {
        synchronized (programLock) {
            if (cancelRequested || closed) {
                prepared.close();
                finishProgram(onFinished);
                return;
            }
        }
        ProcessTtyConnector connector = awaitConnector();
        String marker = RUN_FINISHED + UUID.randomUUID().toString().replace("-", "");
        String script = programCommand(prepared, marker);
        synchronized (programLock) {
            if (cancelRequested || closed) {
                prepared.close();
                finishProgram(onFinished);
                return;
            }
            activeMarker = marker;
            connector.write(script + "\r");
        }
        boolean finished = waitForMarker(marker, 0);
        prepared.close();
        if (!finished && !closed) {
            throw new IOException("The terminal command ended before its completion marker appeared.");
        }
        finishProgram(onFinished);
    }

    private ProcessTtyConnector awaitConnector() throws IOException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        while (!closed && System.nanoTime() < deadline) {
            if (terminal.getTtyConnector() instanceof ProcessTtyConnector connector
                    && connector.isConnected()) {
                return connector;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for the terminal shell.", exception);
            }
        }
        throw new IOException(closed
            ? "The terminal was closed before the program could start."
            : "The terminal shell did not start in time.");
    }

    private String programCommand(JavaRunner.PreparedProgram prepared, String marker) {
        List<String> command = prepared.commandLine();
        Path directory = prepared.workingDirectory();
        if (isWindows()) {
            List<String> pieces = new ArrayList<>();
            if (!prepared.compilationOutput().isBlank()) {
                pieces.add(powershellWrite(prepared.compilationOutput()));
            }
            pieces.add(String.join(" ", command.stream().map(TerminalPanel::quoteWindows)
                .toList()));
            int split = marker.length() / 2;
            pieces.add("echo " + marker.substring(0, split) + "^" + marker.substring(split));
            return "cd /d " + quoteWindows(directory.toString()) + " & "
                + String.join(" & ", pieces);
        }
        StringBuilder script = new StringBuilder();
        if (!prepared.compilationOutput().isBlank()) {
            script.append(posixPrintCommand(prepared.compilationOutput()))
                .append("; ");
        }
        script.append("cd ")
            .append(quotePosix(directory.toString()))
            .append(" && ");
        script.append(String.join(" ", command.stream().map(TerminalPanel::quotePosix)
            .toList()));
        int split = marker.length() / 2;
        script.append("; printf '\\n%s%s\\n' ")
            .append(quotePosix(marker.substring(0, split)))
            .append(" ")
            .append(quotePosix(marker.substring(split)));
        return script.toString();
    }

    private static String powershellWrite(String text) {
        String encoded = java.util.Base64.getEncoder().encodeToString(
            text.getBytes(StandardCharsets.UTF_16LE));
        return "powershell -NoProfile -EncodedCommand " + encoded;
    }

    static String posixPrintCommand(String text) {
        String encoded = java.util.Base64.getEncoder().encodeToString(
            text.getBytes(StandardCharsets.UTF_8));
        return "printf '%s' '" + encoded + "' | base64 -d";
    }

    private static String quotePosix(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static String quoteWindows(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private boolean waitForMarker(String marker, long timeoutMillis) {
        long deadline = timeoutMillis == 0 ? Long.MAX_VALUE
            : System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!closed && programRunning && System.nanoTime() < deadline) {
            if (markerAppeared(marker)) {
                return true;
            }
            PtyProcess current = process;
            if (current == null || !current.isAlive()) {
                return false;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return markerAppeared(marker);
    }

    private boolean markerAppeared(String marker) {
        return terminalTranscript().lines()
            .map(String::strip)
            .anyMatch(marker::equals);
    }

    public void stopProgram() {
        String marker;
        synchronized (programLock) {
            if (!programRunning || cancelRequested) {
                return;
            }
            cancelRequested = true;
            marker = activeMarker;
        }
        if (marker == null) {
            return;
        }
        ProcessTtyConnector connector =
            terminal.getTtyConnector() instanceof ProcessTtyConnector current ? current : null;
        if (connector == null || !connector.isConnected()) {
            terminateShellAndRestart();
            return;
        }
        try {
            connector.write("\u0003");
        } catch (IOException exception) {
            terminateShellAndRestart();
            return;
        }
        WORKERS.execute(() -> {
            if (waitForMarker(marker, STOP_GRACE_MILLIS)) {
                return;
            }
            PtyProcess current = process;
            if (current != null) {
                killChildren(current);
            }
            if (!waitForMarker(marker, STOP_GRACE_MILLIS)) {
                terminateShellAndRestart();
            }
        });
    }

    private void submitError(String text) {
        WORKERS.execute(() -> {
            try {
                ProcessTtyConnector connector = awaitConnector();
                if (isWindows()) {
                    connector.write(powershellWrite(text) + "\r");
                } else {
                    connector.write(posixPrintCommand(text) + "\r");
                }
            } catch (IOException exception) {
                showStartupError("Could not write to terminal: " + exception.getMessage());
            }
        });
    }

    private void finishProgram(Runnable onFinished) {
        synchronized (programLock) {
            programRunning = false;
            activeMarker = null;
        }
        if (onFinished != null && !closed) {
            SwingUtilities.invokeLater(onFinished);
        }
    }

    private void terminateShellAndRestart() {
        PtyProcess current = process;
        if (current != null) {
            destroy(current);
        }
        process = null;
        synchronized (programLock) {
            programRunning = false;
            activeMarker = null;
        }
        SwingUtilities.invokeLater(() -> {
            replaceTerminalWidget();
            startShell();
        });
    }

    private void replaceTerminalWidget() {
        if (closed) {
            return;
        }
        JediTermWidget previous = terminal;
        remove(previous);
        previous.close();
        terminal = createTerminalWidget();
        add(terminal, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void showStartupError(String message) {
        SwingUtilities.invokeLater(() -> {
            if (!closed) {
                startupMessage.setText(message);
                terminal.setVisible(false);
            }
        });
    }

    private static ProcessTtyConnector connector(
            PtyProcess process, List<String> command) {
        return new ProcessTtyConnector(process, StandardCharsets.UTF_8, command) {
            @Override
            public String getName() {
                return command.get(0);
            }

            @Override
            public void resize(TermSize size) {
                process.setWinSize(new WinSize(size.getColumns(), size.getRows()));
            }

            @Override
            public void resize(java.awt.Dimension size) {
                process.setWinSize(new WinSize(size.width, size.height));
            }
        };
    }

    private static List<String> shellCommand() {
        if (isWindows()) {
            return List.of(System.getenv().getOrDefault("COMSPEC", "cmd.exe"), "/Q");
        }
        String shell = System.getenv("SHELL");
        if (shell == null || shell.isBlank() || !Files.isExecutable(Path.of(shell))) {
            shell = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")
                ? "/bin/zsh" : "/bin/bash";
        }
        return List.of(shell, "-l");
    }

    static Map<String, String> terminalEnvironment(Map<String, String> inherited) {
        Map<String, String> environment = new HashMap<>(inherited);
        String javaToolOptions = environment.get("JAVA_TOOL_OPTIONS");
        if (javaToolOptions != null) {
            String withoutDebugger = javaToolOptions
                .replaceAll("(?<!\\S)-agentlib:jdwp=\\S+", "")
                .trim();
            if (withoutDebugger.isEmpty()) {
                environment.remove("JAVA_TOOL_OPTIONS");
            } else {
                environment.put("JAVA_TOOL_OPTIONS", withoutDebugger);
            }
        }
        return environment;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void destroy(PtyProcess process) {
        killChildren(process);
        process.destroyForcibly();
    }

    private static void killChildren(PtyProcess process) {
        ProcessHandle.of(process.pid()).ifPresent(handle ->
            handle.descendants().forEach(ProcessHandle::destroyForcibly));
    }

    private static JediTermWidget createTerminalWidget() {
        JediTermWidget widget = new JediTermWidget(100, 30, new TerminalSettings());
        widget.setBackground(new Color(24, 24, 24));
        widget.getComponent().setBorder(BorderFactory.createEmptyBorder());
        widget.getTerminalPanel().setBorder(BorderFactory.createEmptyBorder());
        widget.getTerminalPanel().setBackground(new Color(24, 24, 24));
        widget.getTerminalPanel().setCursorShape(CursorShape.STEADY_VERTICAL_BAR);
        widget.getTerminalPanel().setCursorVisible(true);
        return widget;
    }

    void closeTerminal() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        synchronized (programLock) {
            cancelRequested = true;
            programRunning = false;
            activeMarker = null;
        }
        PtyProcess current = process;
        if (current != null) {
            destroy(current);
        }
        terminal.close();
        OPEN_TERMINALS.remove(this);
    }

    private static final class TerminalSettings extends DefaultSettingsProvider {
        @Override
        public Font getTerminalFont() {
            return new Font("Menlo", Font.PLAIN, 13);
        }

        @Override
        public TerminalColor getDefaultForeground() {
            return TerminalColor.rgb(220, 220, 220);
        }

        @Override
        public TerminalColor getDefaultBackground() {
            return TerminalColor.rgb(24, 24, 24);
        }

        @Override
        public TextStyle getSelectionColor() {
            return new TextStyle(
                TerminalColor.rgb(230, 230, 230), TerminalColor.rgb(55, 90, 130));
        }

        @Override
        public boolean useAntialiasing() {
            return true;
        }
    }
}
