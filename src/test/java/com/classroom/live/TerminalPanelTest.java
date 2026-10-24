package com.classroom.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jediterm.terminal.ProcessTtyConnector;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class TerminalPanelTest {
    @Test
    void printsMultilineMessagesWithoutSendingMultilineShellCommands() throws Exception {
        Assumptions.assumeFalse(
            System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win"));
        String message = "Java compilation timed out.\nPicked up JAVA_TOOL_OPTIONS\n";
        String command = TerminalPanel.posixPrintCommand(message);

        assertFalse(command.contains("\n"), "The shell command must stay on one line");
        Process process = new ProcessBuilder("/bin/sh", "-c", command).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertEquals(0, process.waitFor());
        assertEquals(message, output);
    }

    @Test
    void startsARealPtyShellAndAcceptsInteractiveProgramInput() throws Exception {
        Assumptions.assumeFalse(
            System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win"));
        Path directory = Files.createTempDirectory("feric-terminal-test-");
        TerminalPanel[] terminal = new TerminalPanel[1];
        try {
            SwingUtilities.invokeAndWait(() -> terminal[0] = new TerminalPanel(() -> directory));
            ProcessTtyConnector connector = awaitConnector(terminal[0], 10);
            connector.write("pwd\r");
            assertTrue(awaitOutput(terminal[0], directory.toString(), 5),
                "The shell should start in its supplied working directory");
            connector.write(
                "printf '__FERIC_TTY__%s\\n' \"$(test -t 0 && echo yes || echo no)\"; "
                    + "printf 'Enter value: '; read value; "
                    + "printf 'received=%s\\n' \"$value\"\r");
            assertTrue(awaitOutput(terminal[0], "__FERIC_TTY__yes", 5),
                "Shell input must be connected to a real pseudo-terminal");
            assertTrue(awaitOutput(terminal[0], "Enter value:", 5), "Shell command did not run");

            connector.write("interactive-input\r");
            assertTrue(awaitOutput(terminal[0], "received=interactive-input", 5),
                "The shell did not receive interactive terminal input");

            String javaProgram = """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) throws Exception {
                        System.out.print("Program input: ");
                        System.out.flush();
                        String answer = new Scanner(System.in).nextLine();
                        Thread.sleep(10_500);
                        System.out.println("Program completed after sleeping: " + answer);
                    }
                }
                """;
            SwingUtilities.invokeAndWait(() ->
                terminal[0].runJavaProject(Map.of("Main.java", javaProgram), "Main", null));
            assertTrue(awaitOutput(terminal[0], "Program input:", 15),
                "Java program did not start in the terminal");
            connector.write("typed-while-running\r");
            Thread.sleep(500);
            assertTrue(terminal[0].isProgramRunning(),
                "The echoed completion marker must not be mistaken for command completion");
            assertTrue(awaitOutput(
                terminal[0], "Program completed after sleeping: typed-while-running", 20),
                "The terminal must accept input and allow programs to run beyond ten seconds");
            assertTrue(awaitProgramStopped(terminal[0], 5),
                "The run command should finish after its program exits. Terminal:\n"
                    + terminal[0].terminalTranscript());

            String hangingProgram = """
                public class Main {
                    public static void main(String[] args) throws Exception {
                        System.out.println("Stop test ready");
                        while (true) {
                            Thread.sleep(1000);
                        }
                    }
                }
                """;
            SwingUtilities.invokeAndWait(() ->
                terminal[0].runJavaProject(Map.of("Main.java", hangingProgram), "Main", null));
            assertTrue(awaitOutput(terminal[0], "Stop test ready", 15),
                "The interrupt test program did not start");
            SwingUtilities.invokeAndWait(terminal[0]::stopProgram);
            assertTrue(awaitProgramStopped(terminal[0], 8),
                "Stop should interrupt a running program instead of hanging");
            assertTrue(connector.isConnected(),
                "Stopping a program should leave the terminal shell usable");
            connector.write("printf '__FERIC_AFTER_STOP__\\n'\r");
            assertTrue(awaitOutput(terminal[0], "__FERIC_AFTER_STOP__", 5),
                "The shell should accept commands after stopping a program");
        } finally {
            TerminalPanel.shutdownAll();
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    void closingTerminalStopsItsShellProcess() throws Exception {
        Assumptions.assumeFalse(
            System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win"));
        Path directory = Files.createTempDirectory("feric-terminal-close-test-");
        TerminalPanel[] terminal = new TerminalPanel[1];
        try {
            SwingUtilities.invokeAndWait(() ->
                terminal[0] = new TerminalPanel(() -> directory));
            ProcessTtyConnector connector = awaitConnector(terminal[0], 10);

            SwingUtilities.invokeAndWait(terminal[0]::closeTerminal);

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (connector.isConnected() && System.nanoTime() < deadline) {
                Thread.sleep(25);
            }
            assertFalse(connector.isConnected(), "Closing a terminal should stop its shell");
        } finally {
            TerminalPanel.shutdownAll();
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static ProcessTtyConnector awaitConnector(TerminalPanel panel, int seconds)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            ProcessTtyConnector[] connector = new ProcessTtyConnector[1];
            SwingUtilities.invokeAndWait(() -> {
                if (panel.terminalWidget().getTtyConnector() instanceof ProcessTtyConnector pty) {
                    connector[0] = pty;
                }
            });
            if (connector[0] != null && connector[0].isConnected()) {
                return connector[0];
            }
            Thread.sleep(25);
        }
        throw new AssertionError("PTY shell did not start");
    }

    private static boolean awaitOutput(TerminalPanel panel, String text, int seconds)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            boolean[] found = new boolean[1];
            SwingUtilities.invokeAndWait(() -> found[0] =
                panel.terminalWidget().getTerminalTextBuffer().getScreenLines().contains(text));
            if (found[0]) {
                return true;
            }
            Thread.sleep(25);
        }
        return false;
    }

    private static boolean awaitProgramStopped(TerminalPanel panel, int seconds)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if (!panel.isProgramRunning()) {
                return true;
            }
            Thread.sleep(25);
        }
        return false;
    }
}
