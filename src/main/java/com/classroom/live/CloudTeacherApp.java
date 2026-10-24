package com.classroom.live;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class CloudTeacherApp extends javax.swing.JFrame {
    private final SupabaseClient client;
    private final ClassroomService service;
    private final JComboBox<ClassroomService.Classroom> rooms = new JComboBox<>();
    private final JTextField roomCode = new JTextField(10);
    private final JButton createRoomButton = new JButton("Create Classroom");
    private final JButton deleteRoomButton = new JButton("Delete Classroom");
    private final JButton copyCodeButton = new JButton("Copy code");
    private final JLabel status = new JLabel("Loading classrooms…");
    private final JLabel memberCount = new JLabel("0 connected");
    private final JTabbedPane tabs = new JTabbedPane();
    private final Map<String, CodeEditor> editors = new HashMap<>();
    private final Map<CodeEditor, String> paths = new HashMap<>();
    private final Map<CodeEditor, WorkspaceFile> savedFiles = new HashMap<>();
    private final Map<CodeEditor, Long> editGenerations = new HashMap<>();
    private final Set<CodeEditor> dirty = new HashSet<>();
    private final Set<CodeEditor> saving = new HashSet<>();
    private final Set<CodeEditor> suppressChanges = new HashSet<>();
    private final Timer autosave;
    private final Timer presenceTimer;
    private volatile ClassroomService.Classroom activeRoom;
    private RemoteWorkspaceExplorer explorer;
    private RealtimeSyncService realtime;
    private CodeEditor activeEditor;
    private JPanel explorerHolder;
    private boolean suppressRoomSelection;
    private boolean deletingClassroom;

    public CloudTeacherApp(SupabaseClient client) {
        super("Feric — Teacher");
        this.client = client;
        service = new ClassroomService(client);
        IdeTheme.install();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 820);
        setMinimumSize(new Dimension(900, 620));
        setLocationRelativeTo(null);
        autosave = new Timer(400, event -> saveDirtyEditors());
        autosave.setRepeats(false);
        presenceTimer = new Timer(30_000, event -> refreshPresence());
        buildInterface();
        installIcon();
        rooms.addActionListener(event -> {
            updateDeleteButton();
            if (suppressRoomSelection) {
                return;
            }
            Object selected = rooms.getSelectedItem();
            if (selected instanceof ClassroomService.Classroom room
                    && (activeRoom == null || !activeRoom.id().equals(room.id()))) {
                switchRoom(room);
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                autosave.stop();
                presenceTimer.stop();
                closeRealtime();
                if (explorer != null) {
                    explorer.close();
                }
                CompilerPanel.shutdown();
            }
        });
        setVisible(true);
        loadOwnedRooms();
    }

    private void buildInterface() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(IdeTheme.BACKGROUND);

        JPanel top = new JPanel(new BorderLayout());
        JPanel brand = new JPanel(new BorderLayout());
        brand.setBackground(IdeTheme.PANEL);
        brand.setBorder(BorderFactory.createEmptyBorder(9, 13, 9, 12));
        JLabel label = new JLabel("FERIC  /  TEACHER");
        label.setForeground(IdeTheme.MUTED);
        label.setFont(IdeTheme.UI_FONT.deriveFont(Font.BOLD, 11f));
        brand.add(label, BorderLayout.WEST);
        top.add(brand, BorderLayout.NORTH);

        JPanel roomBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 7));
        roomBar.setBackground(IdeTheme.SIDEBAR);
        roomBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, IdeTheme.BORDER));
        rooms.setPrototypeDisplayValue(new ClassroomService.Classroom(
            UUID.randomUUID(), "Select a classroom with a long name", "ABCDE", true));
        IdeTheme.styleComboBox(rooms);
        rooms.setRenderer((list, value, index, selected, focus) -> {
            JLabel item = new JLabel(value == null ? "" : value.name());
            item.setOpaque(true);
            item.setBackground(selected ? IdeTheme.SELECTION : IdeTheme.PANEL);
            item.setForeground(IdeTheme.TEXT);
            item.setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));
            return item;
        });
        IdeTheme.styleButton(createRoomButton);
        createRoomButton.addActionListener(event -> createClassroom());
        IdeTheme.styleButton(deleteRoomButton);
        deleteRoomButton.setForeground(new java.awt.Color(235, 105, 97));
        deleteRoomButton.setEnabled(false);
        deleteRoomButton.addActionListener(event -> deleteActiveClassroom());
        IdeTheme.styleButton(copyCodeButton);
        copyCodeButton.setEnabled(false);
        copyCodeButton.addActionListener(event -> copyRoomCode());
        JLabel codeCaption = new JLabel("JOIN CODE");
        codeCaption.setForeground(IdeTheme.MUTED);
        codeCaption.setFont(IdeTheme.UI_FONT.deriveFont(Font.BOLD, 9f));
        IdeTheme.styleTextField(roomCode);
        roomCode.setForeground(IdeTheme.TEXT);
        roomCode.setEditable(false);
        roomCode.setFocusable(true);
        TextEditingSupport.install(roomCode);
        roomCode.setHorizontalAlignment(JTextField.CENTER);
        roomCode.setToolTipText("Select and copy this classroom code");
        roomCode.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                javax.swing.SwingUtilities.invokeLater(roomCode::selectAll);
            }
        });
        roomCode.setFont(IdeTheme.UI_FONT.deriveFont(Font.BOLD, 16f));
        roomBar.add(new JLabel("Classroom"));
        roomBar.add(rooms);
        roomBar.add(createRoomButton);
        roomBar.add(deleteRoomButton);
        roomBar.add(codeCaption);
        roomBar.add(roomCode);
        roomBar.add(copyCodeButton);
        top.add(roomBar, BorderLayout.SOUTH);

        explorerHolder = new JPanel(new BorderLayout());
        explorerHolder.setBackground(IdeTheme.SIDEBAR);
        JLabel emptyExplorer = new JLabel("  Create or select a classroom", JLabel.LEFT);
        emptyExplorer.setForeground(IdeTheme.MUTED);
        explorerHolder.add(emptyExplorer, BorderLayout.NORTH);
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(IdeTheme.SIDEBAR);
        sidebar.setPreferredSize(new Dimension(270, 500));
        sidebar.add(explorerHolder, BorderLayout.CENTER);

        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.setBackground(IdeTheme.EDITOR);
        tabs.setBackground(IdeTheme.SIDEBAR);
        tabs.setForeground(IdeTheme.TEXT);
        tabs.setFont(IdeTheme.UI_FONT);
        tabs.addChangeListener(event -> {
            Component selected = tabs.getSelectedComponent();
            activeEditor = selected instanceof EditorTab tab ? tab.editor() : null;
        });
        JPanel editorToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 4));
        editorToolbar.setBackground(IdeTheme.SIDEBAR);
        JButton save = new JButton("Save now");
        IdeTheme.styleButton(save);
        save.addActionListener(event -> {
            if (activeEditor != null) {
                saveEditor(activeEditor);
            }
        });
        JButton format = new JButton("Format Java");
        IdeTheme.styleButton(format);
        format.addActionListener(event -> formatActive());
        editorToolbar.add(save);
        editorToolbar.add(format);
        editorPanel.add(editorToolbar, BorderLayout.NORTH);
        editorPanel.add(tabs, BorderLayout.CENTER);

        JPanel compiler = CompilerPanel.create(
            () -> activeEditor, this::javaProjectSources, this::teacherWorkspaceDirectory);
        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorPanel, compiler);
        IdeTheme.styleSplitPane(vertical);
        vertical.setResizeWeight(0.68);
        JSplitPane horizontal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, vertical);
        IdeTheme.styleSplitPane(horizontal);
        horizontal.setResizeWeight(0);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(IdeTheme.PANEL);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, IdeTheme.BORDER));
        footer.setPreferredSize(new Dimension(800, 27));
        status.setForeground(IdeTheme.MUTED);
        footer.add(status, BorderLayout.WEST);
        memberCount.setForeground(IdeTheme.MUTED);
        memberCount.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 12));
        footer.add(memberCount, BorderLayout.EAST);

        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setBackground(IdeTheme.BACKGROUND);
        workspace.add(top, BorderLayout.NORTH);
        workspace.add(horizontal, BorderLayout.CENTER);
        workspace.add(footer, BorderLayout.SOUTH);
        root.add(new AccountControls(this, client, "Teacher account"), BorderLayout.WEST);
        root.add(workspace, BorderLayout.CENTER);
        setContentPane(root);
        SwingUtilities.invokeLater(() -> {
            if (isDisplayable()) {
                vertical.setDividerLocation(0.68);
            }
        });
    }

    private void loadOwnedRooms() {
        status.setText("Loading classrooms…");
        new SwingWorker<List<ClassroomService.Classroom>, Void>() {
            @Override
            protected List<ClassroomService.Classroom> doInBackground() throws IOException {
                return service.listOwned();
            }

            @Override
            protected void done() {
                try {
                    for (ClassroomService.Classroom room : get()) {
                        rooms.addItem(room);
                    }
                    if (rooms.getItemCount() == 0) {
                        status.setText("Create a classroom to begin");
                    } else {
                        rooms.setSelectedIndex(0);
                    }
                } catch (Exception exception) {
                    status.setText("Could not load classrooms: " + rootMessage(exception));
                }
            }
        }.execute();
    }

    private void createClassroom() {
        String name = JOptionPane.showInputDialog(
            this, "Classroom name:", "Create Classroom", JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            return;
        }
        if (name.trim().isEmpty() || name.trim().length() > 100) {
            showError("Classroom name must contain 1–100 characters.");
            return;
        }
        status.setText("Creating classroom and initial project…");
        new SwingWorker<ClassroomService.Classroom, Void>() {
            @Override
            protected ClassroomService.Classroom doInBackground() throws IOException {
                return service.create(name.trim());
            }

            @Override
            protected void done() {
                try {
                    ClassroomService.Classroom created = get();
                    rooms.addItem(created);
                    rooms.setSelectedItem(created);
                    status.setText("Classroom created");
                } catch (Exception exception) {
                    status.setText("Classroom creation failed: " + rootMessage(exception));
                    showError("Could not create classroom:\n" + rootMessage(exception));
                }
            }
        }.execute();
    }

    private void deleteActiveClassroom() {
        ClassroomService.Classroom room = activeRoom;
        if (room == null || deletingClassroom) {
            return;
        }
        String message = "Permanently delete \"" + room.name() + "\"?\n\n"
            + "This removes its files, folders, classroom membership, and join code. "
            + "Students will lose access. This cannot be undone."
            + (dirty.isEmpty() ? "" : "\n\nUnsaved edits in this classroom will also be lost.");
        int answer = JOptionPane.showConfirmDialog(
            this,
            message,
            "Delete Classroom",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        deletingClassroom = true;
        autosave.stop();
        rooms.setEnabled(false);
        createRoomButton.setEnabled(false);
        deleteRoomButton.setEnabled(false);
        copyCodeButton.setEnabled(false);
        status.setText("Deleting classroom…");
        new SwingWorker<ClassroomService.Classroom, Void>() {
            @Override
            protected ClassroomService.Classroom doInBackground() throws IOException {
                service.deleteClassroom(room.id());
                return room;
            }

            @Override
            protected void done() {
                deletingClassroom = false;
                rooms.setEnabled(true);
                createRoomButton.setEnabled(true);
                try {
                    ClassroomService.Classroom deleted = get();
                    if (activeRoom == null || !activeRoom.id().equals(deleted.id())) {
                        return;
                    }
                    closeRealtime();
                    if (explorer != null) {
                        explorer.close();
                        explorer = null;
                    }
                    activeRoom = null;
                    clearEditors();
                    roomCode.setText("");
                    roomCode.setForeground(IdeTheme.TEXT);
                    memberCount.setText("0 connected");
                    explorerHolder.removeAll();
                    JLabel emptyExplorer =
                        new JLabel("  Create or select a classroom", JLabel.LEFT);
                    emptyExplorer.setForeground(IdeTheme.MUTED);
                    explorerHolder.add(emptyExplorer, BorderLayout.NORTH);
                    explorerHolder.revalidate();
                    explorerHolder.repaint();

                    suppressRoomSelection = true;
                    rooms.removeItem(deleted);
                    suppressRoomSelection = false;
                    copyCodeButton.setEnabled(false);
                    updateDeleteButton();
                    Object selected = rooms.getSelectedItem();
                    if (selected instanceof ClassroomService.Classroom nextRoom) {
                        switchRoom(nextRoom);
                    } else {
                        status.setText("Classroom deleted");
                    }
                } catch (Exception exception) {
                    if (!dirty.isEmpty()) {
                        autosave.restart();
                    }
                    updateDeleteButton();
                    copyCodeButton.setEnabled(activeRoom != null);
                    status.setText("Classroom deletion failed: " + rootMessage(exception));
                    showError("Could not delete classroom:\n" + rootMessage(exception));
                }
            }
        }.execute();
    }

    private void updateDeleteButton() {
        deleteRoomButton.setEnabled(!deletingClassroom && activeRoom != null);
    }

    private void switchRoom(ClassroomService.Classroom room) {
        if (!dirty.isEmpty() && activeRoom != null) {
            int answer = JOptionPane.showConfirmDialog(
                this, "There are unsaved edits. Save them before switching classrooms?",
                "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION);
            if (answer == JOptionPane.CANCEL_OPTION || answer == JOptionPane.CLOSED_OPTION) {
                restoreRoomSelection();
                return;
            }
            if (answer == JOptionPane.YES_OPTION) {
                for (CodeEditor editor : List.copyOf(dirty)) {
                    saveEditor(editor);
                }
                if (!dirty.isEmpty()) {
                    status.setText("Wait for pending saves before switching classrooms");
                    restoreRoomSelection();
                    return;
                }
            }
        }
        closeRealtime();
        if (explorer != null) {
            explorer.close();
        }
        activeRoom = room;
        updateDeleteButton();
        copyCodeButton.setEnabled(true);
        clearEditors();
        roomCode.setText(room.joinCode());
        boolean legacyCode = RoomCode.isLegacy(room.joinCode());
        roomCode.setForeground(legacyCode ? new java.awt.Color(225, 145, 87) : IdeTheme.TEXT);
        roomCode.setToolTipText(legacyCode
            ? "Old ten-character code. Select and copy it to join now, or run migration 002 "
                + "to rotate this room to a five-character code."
            : "Five-character code for students to join");
        if (legacyCode) {
            status.setText("This room has an old ten-character code. Migration 002 changes "
                + "it to five characters.");
        }
        explorer = new RemoteWorkspaceExplorer(
            service, room.id(), false, false, this::openFile, this::applyRemoteFile,
            this::remoteDelete, status::setText);
        explorerHolder.removeAll();
        explorerHolder.add(explorer, BorderLayout.CENTER);
        explorerHolder.revalidate();
        explorerHolder.repaint();
        realtime = new RealtimeSyncService(
            currentClient(), room.id(),
            change -> SwingUtilities.invokeLater(() -> {
                if (activeRoom != null && activeRoom.id().equals(room.id())
                        && explorer != null) {
                    explorer.applyRealtimeChange(change);
                }
            }),
            () -> SwingUtilities.invokeLater(() -> {
                if (activeRoom != null && activeRoom.id().equals(room.id())
                        && explorer != null) {
                    explorer.refresh();
                    status.setText("Realtime connected; workspace reconciled");
                }
            }),
            message -> SwingUtilities.invokeLater(() -> status.setText(message))
        );
        realtime.start();
        presenceTimer.start();
        refreshPresence();
    }

    private SupabaseClient currentClient() {
        return client;
    }

    private void restoreRoomSelection() {
        if (activeRoom != null) {
            rooms.setSelectedItem(activeRoom);
        }
    }

    private void refreshPresence() {
        if (activeRoom == null) {
            return;
        }
        UUID roomId = activeRoom.id();
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws IOException {
                return service.activeMemberCount(roomId);
            }

            @Override
            protected void done() {
                if (activeRoom == null || !activeRoom.id().equals(roomId)) {
                    return;
                }
                try {
                    int count = get();
                    memberCount.setText(count + (count == 1 ? " connected student" : " connected students"));
                } catch (Exception exception) {
                    memberCount.setText("Presence unavailable: " + rootMessage(exception));
                }
            }
        }.execute();
    }

    private void openFile(String path) {
        CodeEditor alreadyOpen = editors.get(path);
        if (alreadyOpen != null) {
            selectEditor(alreadyOpen);
            return;
        }
        WorkspaceFile file = explorer == null ? null : explorer.file(path);
        if (file != null) {
            addEditor(file);
            return;
        }
        status.setText("Loading " + path + "…");
        UUID roomId = activeRoom.id();
        new SwingWorker<ClassroomService.Snapshot, Void>() {
            @Override
            protected ClassroomService.Snapshot doInBackground() throws IOException {
                return service.load(roomId);
            }

            @Override
            protected void done() {
                if (activeRoom == null || !activeRoom.id().equals(roomId)) {
                    return;
                }
                try {
                    WorkspaceFile loaded = get().files().stream()
                        .filter(candidate -> candidate.path().equals(path))
                        .findFirst().orElseThrow(() -> new IOException("File no longer exists"));
                    addEditor(loaded);
                } catch (Exception exception) {
                    status.setText("Could not open file: " + rootMessage(exception));
                }
            }
        }.execute();
    }

    private void addEditor(WorkspaceFile file) {
        CodeEditor editor = new CodeEditor(file.path());
        suppressChanges.add(editor);
        editor.setText(file.content());
        suppressChanges.remove(editor);
        editor.setCaretPosition(0);
        editors.put(file.path(), editor);
        paths.put(editor, file.path());
        savedFiles.put(editor, file);
        editGenerations.put(editor, 0L);
        editor.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                if (suppressChanges.contains(editor)) {
                    return;
                }
                dirty.add(editor);
                editGenerations.merge(editor, 1L, Long::sum);
                status.setText("Unsaved changes — saving…");
                if (!deletingClassroom) {
                    autosave.restart();
                }
            }
            @Override public void insertUpdate(DocumentEvent event) { changed(); }
            @Override public void removeUpdate(DocumentEvent event) { changed(); }
            @Override public void changedUpdate(DocumentEvent event) { changed(); }
        });
        EditorTab tab = new EditorTab(editor);
        tabs.addTab(file.path(), tab);
        tabs.setSelectedComponent(tab);
    }

    private void saveDirtyEditors() {
        for (CodeEditor editor : List.copyOf(dirty)) {
            saveEditor(editor);
        }
    }

    private void saveEditor(CodeEditor editor) {
        if (deletingClassroom || activeRoom == null
                || saving.contains(editor) || !dirty.contains(editor)) {
            return;
        }
        String path = paths.get(editor);
        if (path == null) {
            status.setText("Create a classroom file in Explorer before editing");
            return;
        }
        long generation = editGenerations.getOrDefault(editor, 0L);
        UUID roomId = activeRoom.id();
        String content = editor.getText();
        long expectedVersion = savedFiles.containsKey(editor)
            ? savedFiles.get(editor).version() : 0;
        saving.add(editor);
        status.setText("Saving " + path + "…");
        new SwingWorker<WorkspaceFile, Void>() {
            @Override
            protected WorkspaceFile doInBackground() throws IOException {
                return service.save(
                    roomId, path, content, WorkspacePath.languageFor(path), expectedVersion);
            }

            @Override
            protected void done() {
                saving.remove(editor);
                if (activeRoom == null || !activeRoom.id().equals(roomId)) {
                    return;
                }
                try {
                    WorkspaceFile saved = get();
                    savedFiles.put(editor, saved);
                    boolean mirrored = mirrorTeacherFile(roomId, path, saved.content());
                    if (generation == editGenerations.getOrDefault(editor, 0L)) {
                        dirty.remove(editor);
                        if (mirrored) {
                            status.setText("Saved " + path + " · version " + saved.version());
                        }
                    } else {
                        autosave.restart();
                    }
                } catch (Exception exception) {
                    status.setText("Save failed for " + path + ": " + rootMessage(exception));
                    if (SupabaseClient.isTransient(exception.getCause() == null
                            ? exception : exception.getCause())) {
                        Timer retry = new Timer(2000, event -> saveEditor(editor));
                        retry.setRepeats(false);
                        retry.start();
                    }
                }
            }
        }.execute();
    }

    private void applyRemoteFile(WorkspaceFile incoming) {
        CodeEditor editor = editors.get(incoming.path());
        if (editor == null) {
            for (Map.Entry<CodeEditor, WorkspaceFile> entry : savedFiles.entrySet()) {
                if (entry.getValue().id().equals(incoming.id())) {
                    editor = entry.getKey();
                    String oldPath = paths.put(editor, incoming.path());
                    if (oldPath != null) {
                        editors.remove(oldPath);
                    }
                    editors.put(incoming.path(), editor);
                    editor.setLanguageForFile(incoming.path());
                    int index = tabIndex(editor);
                    if (index >= 0) {
                        tabs.setTitleAt(index, incoming.path());
                    }
                    break;
                }
            }
        }
        if (editor == null) {
            mirrorTeacherFile(
                incoming.classroomId(), incoming.path(), incoming.content());
            return;
        }
        WorkspaceFile current = savedFiles.get(editor);
        if (current != null && incoming.version() <= current.version()) {
            return;
        }
        if (dirty.contains(editor)) {
            if (incoming.content().equals(editor.getText())) {
                savedFiles.put(editor, incoming);
                dirty.remove(editor);
                boolean mirrored = mirrorTeacherFile(
                    incoming.classroomId(), incoming.path(), incoming.content());
                if (mirrored) {
                    status.setText(
                        "Saved " + incoming.path() + " · version " + incoming.version());
                }
                return;
            }
            Object[] options = {"Reload remote", "Keep my edits", "Later"};
            int choice = JOptionPane.showOptionDialog(
                this,
                "A newer classroom version of " + incoming.path()
                    + " conflicts with your unsaved edits.",
                "Remote Edit Conflict",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[2]
            );
            if (choice == 0) {
                suppressChanges.add(editor);
                editor.setText(incoming.content());
                suppressChanges.remove(editor);
                dirty.remove(editor);
                savedFiles.put(editor, incoming);
                boolean mirrored = mirrorTeacherFile(
                    incoming.classroomId(), incoming.path(), incoming.content());
                if (mirrored) {
                    status.setText("Reloaded remote version of " + incoming.path());
                }
            } else if (choice == 1) {
                savedFiles.put(editor, incoming);
                saveEditor(editor);
            } else {
                status.setText("Unsaved edits retained; resolve conflict before saving");
            }
            return;
        }
        suppressChanges.add(editor);
        editor.setText(incoming.content());
        suppressChanges.remove(editor);
        savedFiles.put(editor, incoming);
        if (mirrorTeacherFile(
                incoming.classroomId(), incoming.path(), incoming.content())) {
            status.setText("Updated from classroom: " + incoming.path());
        }
    }

    private void remoteDelete(String path) {
        ClassroomService.Classroom room = activeRoom;
        CodeEditor editor = editors.get(path);
        if (editor != null) {
            if (dirty.contains(editor)) {
                savedFiles.remove(editor);
                status.setText("File was deleted remotely; your unsaved draft is retained");
                return;
            }
            editors.remove(path);
            int index = tabIndex(editor);
            if (index >= 0) {
                tabs.removeTabAt(index);
            }
            paths.remove(editor);
            savedFiles.remove(editor);
            status.setText("File deleted remotely: " + path);
        }
        if (room != null) {
            removeMirroredTeacherFile(room.id(), path);
        }
    }

    private void formatActive() {
        CodeEditor editor = activeEditor;
        if (editor == null || !editor.getFileName().toLowerCase().endsWith(".java")) {
            status.setText("Open a Java file to format it");
            return;
        }
        String source = editor.getText();
        new SwingWorker<FormatService.FormatResult, Void>() {
            @Override
            protected FormatService.FormatResult doInBackground() {
                return FormatService.formatJava(source);
            }
            @Override
            protected void done() {
                try {
                    FormatService.FormatResult result = get();
                    if (!result.succeeded()) {
                        status.setText(result.getMessage());
                        return;
                    }
                    if (editor.getText().equals(source)) {
                        editor.setText(result.getFormatted());
                        status.setText(result.getMessage());
                    } else {
                        status.setText("File changed while formatting; result was discarded");
                    }
                } catch (Exception exception) {
                    status.setText("Formatting failed: " + rootMessage(exception));
                }
            }
        }.execute();
    }

    private CodeEditor activeEditor() {
        return activeEditor;
    }

    private Map<String, String> javaProjectSources() {
        Map<String, String> sources = explorer == null
            ? new HashMap<>() : new HashMap<>(explorer.textFiles());
        for (Map.Entry<CodeEditor, String> entry : paths.entrySet()) {
            if (entry.getKey().getText() != null) {
                sources.put(entry.getValue(), entry.getKey().getText());
            }
        }
        return sources;
    }

    private Path teacherWorkspaceDirectory() {
        ClassroomService.Classroom room = activeRoom;
        if (room == null) {
            return TerminalPanel.defaultWorkingDirectory();
        }
        try {
            return ensureTeacherWorkspaceDirectory(room.id());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Path ensureTeacherWorkspaceDirectory(UUID roomId) throws IOException {
        Path root = Path.of(System.getProperty("user.home"), ".feric", "workspaces",
            "teacher", roomId.toString()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) {
            throw new IOException("Teacher workspace cannot be a symbolic link");
        }
        return root;
    }

    private boolean mirrorTeacherFile(UUID roomId, String relativePath, String content) {
        try {
            Path root = ensureTeacherWorkspaceDirectory(roomId);
            Path target = root.resolve(WorkspacePath.normalize(relativePath)).normalize();
            if (!target.startsWith(root)) {
                throw new IOException("Classroom file path escapes the local workspace");
            }
            Path parent = root;
            for (Path segment : root.relativize(target.getParent())) {
                parent = parent.resolve(segment);
                if (Files.isSymbolicLink(parent)) {
                    throw new IOException("Teacher workspace contains a symbolic-link folder");
                }
                Files.createDirectories(parent);
            }
            if (Files.isSymbolicLink(target)) {
                throw new IOException("Teacher workspace file cannot be a symbolic link");
            }
            Files.writeString(target, content, StandardCharsets.UTF_8);
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            status.setText("Could not update terminal workspace: " + exception.getMessage());
            return false;
        }
    }

    private void removeMirroredTeacherFile(UUID roomId, String relativePath) {
        try {
            Path root = ensureTeacherWorkspaceDirectory(roomId);
            Path target = root.resolve(WorkspacePath.normalize(relativePath)).normalize();
            if (!target.startsWith(root) || Files.isSymbolicLink(target)) {
                throw new IOException("Invalid local classroom file path");
            }
            Files.deleteIfExists(target);
        } catch (IOException | IllegalArgumentException exception) {
            status.setText("Could not remove terminal workspace file: " + exception.getMessage());
        }
    }

    private void selectEditor(CodeEditor editor) {
        for (int index = 0; index < tabs.getTabCount(); index++) {
            Component tab = tabs.getComponentAt(index);
            if (tab instanceof EditorTab editorTab && editorTab.editor() == editor) {
                tabs.setSelectedIndex(index);
                return;
            }
        }
    }

    private int tabIndex(CodeEditor editor) {
        for (int index = 0; index < tabs.getTabCount(); index++) {
            Component component = tabs.getComponentAt(index);
            if (component instanceof EditorTab tab && tab.editor() == editor) {
                return index;
            }
        }
        return -1;
    }

    private void clearEditors() {
        tabs.removeAll();
        editors.clear();
        paths.clear();
        savedFiles.clear();
        editGenerations.clear();
        dirty.clear();
        saving.clear();
        activeEditor = null;
    }

    private void closeRealtime() {
        if (realtime != null) {
            realtime.close();
            realtime = null;
        }
        presenceTimer.stop();
    }

    private void copyRoomCode() {
        if (activeRoom == null) {
            return;
        }
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(activeRoom.joinCode()), null);
            roomCode.selectAll();
            status.setText("Room code copied; you can also select it and press Cmd/Ctrl+C");
        } catch (IllegalStateException exception) {
            roomCode.selectAll();
            status.setText("Clipboard is busy; select the room code and press Cmd/Ctrl+C");
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Feric", JOptionPane.ERROR_MESSAGE);
    }

    private void installIcon() {
        try (var stream = getClass().getResourceAsStream("/logos/feric-app-icon.png")) {
            if (stream != null) {
                setIconImage(ImageIO.read(stream));
            }
        } catch (IOException ignored) {
            // An app icon is optional.
        }
    }

    private static String rootMessage(Exception exception) {
        Throwable cause = exception.getCause();
        return cause != null && cause.getMessage() != null
            ? cause.getMessage() : exception.getMessage();
    }

    private static final class EditorTab extends JPanel {
        private final CodeEditor editor;

        private EditorTab(CodeEditor editor) {
            super(new BorderLayout());
            this.editor = editor;
            add(editor.createScrollPane(), BorderLayout.CENTER);
        }

        private CodeEditor editor() {
            return editor;
        }
    }
}
