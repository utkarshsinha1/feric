package com.classroom.live;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rtextarea.RTextScrollPane;

public final class CodeEditor extends RSyntaxTextArea {
    private String fileName;
    private final List<Object> teacherChangeHighlights = new ArrayList<>();

    public CodeEditor(String fileName) {
        super(30, 100);
        this.fileName = fileName;
        setFont(IdeTheme.MONO_FONT);
        setTabSize(4);
        setTabsEmulated(true);
        setAutoIndentEnabled(true);
        setCodeFoldingEnabled(true);
        setHighlightCurrentLine(true);
        setCurrentLineHighlightColor(new Color(255, 255, 255, 12));
        setBracketMatchingEnabled(true);
        setAnimateBracketMatching(false);
        setAntiAliasingEnabled(true);
        setBackground(IdeTheme.EDITOR);
        setForeground(IdeTheme.TEXT);
        setCaretColor(IdeTheme.TEXT);
        setSelectionColor(IdeTheme.SELECTION);
        setSelectedTextColor(Color.WHITE);
        setWhitespaceVisible(false);
        setLanguageForFile(fileName);
        TextEditingSupport.install(this);

        try (InputStream themeStream = getClass().getResourceAsStream(
                "/org/fife/ui/rsyntaxtextarea/themes/dark.xml")) {
            if (themeStream != null) {
                Theme theme = Theme.load(themeStream);
                theme.apply(this);
                setBackground(IdeTheme.EDITOR);
                setForeground(IdeTheme.TEXT);
                setCaretColor(IdeTheme.TEXT);
                setSelectionColor(IdeTheme.SELECTION);
                setSelectedTextColor(Color.WHITE);
                setCurrentLineHighlightColor(new Color(255, 255, 255, 12));
            }
        } catch (Exception ignored) {
            // The editor remains usable with the colors configured above.
        }
    }

    public void setLanguageForFile(String fileName) {
        this.fileName = fileName == null ? "" : fileName;
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".java")) {
            setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        } else if (name.endsWith(".c") || name.endsWith(".h")) {
            setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_C);
        } else if (name.endsWith(".cc") || name.endsWith(".cpp")
                || name.endsWith(".cxx") || name.endsWith(".hpp")) {
            setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS);
        } else if (name.endsWith(".py")) {
            setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
        } else if (name.endsWith(".js") || name.endsWith(".jsx")
                || name.endsWith(".mjs")) {
            setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        } else if (name.endsWith(".ts") || name.endsWith(".tsx")) {
            setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT);
        } else if (name.endsWith(".cs")) {
            setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_CSHARP);
        } else if (name.endsWith(".html") || name.endsWith(".htm")) {
            setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_HTML);
        } else if (name.endsWith(".css")) {
            setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_CSS);
        } else if (name.endsWith(".xml")) {
            setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_XML);
        } else if (name.endsWith(".json")) {
            setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
        } else if (name.endsWith(".sh")) {
            setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
        } else if (name.endsWith(".sql")) {
            setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        } else {
            setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        }
    }

    public String getFileName() {
        return fileName;
    }

    void highlightTeacherChanges(String previousContent, String updatedContent) {
        for (Object highlight : teacherChangeHighlights) {
            removeLineHighlight(highlight);
        }
        teacherChangeHighlights.clear();

        int[] changedLines = changedLineRange(previousContent, updatedContent);
        try {
            for (int line = changedLines[0]; line <= changedLines[1]; line++) {
                teacherChangeHighlights.add(
                    addLineHighlight(line, IdeTheme.COLLABORATION_HIGHLIGHT));
            }
        } catch (BadLocationException exception) {
            teacherChangeHighlights.clear();
            throw new IllegalStateException("Could not highlight teacher changes", exception);
        }
    }

    static int[] changedLineRange(String previousContent, String updatedContent) {
        String[] previousLines = previousContent.split("\\R", -1);
        String[] updatedLines = updatedContent.split("\\R", -1);
        int firstChangedLine = 0;
        while (firstChangedLine < previousLines.length
                && firstChangedLine < updatedLines.length
                && previousLines[firstChangedLine].equals(updatedLines[firstChangedLine])) {
            firstChangedLine++;
        }

        int previousEnd = previousLines.length - 1;
        int updatedEnd = updatedLines.length - 1;
        while (previousEnd >= firstChangedLine && updatedEnd >= firstChangedLine
                && previousLines[previousEnd].equals(updatedLines[updatedEnd])) {
            previousEnd--;
            updatedEnd--;
        }
        if (updatedEnd < firstChangedLine) {
            firstChangedLine = Math.min(firstChangedLine, updatedLines.length - 1);
            updatedEnd = firstChangedLine;
        }
        return new int[] {firstChangedLine, updatedEnd};
    }

    public JPanel createScrollPane() {
        RTextScrollPane scrollPane = new RTextScrollPane(this, true);
        scrollPane.setFoldIndicatorEnabled(true);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.setBackground(IdeTheme.EDITOR);
        scrollPane.getGutter().setBackground(IdeTheme.EDITOR);
        scrollPane.getGutter().setBorderColor(IdeTheme.BORDER);
        scrollPane.getGutter().setLineNumberColor(IdeTheme.MUTED);
        scrollPane.getGutter().setCurrentLineNumberColor(IdeTheme.ACCENT);
        scrollPane.setBorder(BorderFactory.createLineBorder(IdeTheme.BORDER));
        scrollPane.getVerticalScrollBar().setUI(new IdeTheme.DarkScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new IdeTheme.DarkScrollBarUI());
        scrollPane.getViewport().setBackground(IdeTheme.EDITOR);

        JPanel editorView = new JPanel(new java.awt.BorderLayout());
        editorView.setBackground(IdeTheme.EDITOR);
        editorView.add(scrollPane, java.awt.BorderLayout.CENTER);
        editorView.add(new CodeMinimap(scrollPane), java.awt.BorderLayout.EAST);
        return editorView;
    }

    private final class CodeMinimap extends JComponent {
        private static final int SIDE_WIDTH = 96;
        private static final int HORIZONTAL_PADDING = 6;
        private static final float CHARACTER_WIDTH = 1.25f;
        private static final Font MINIMAP_FONT =
            new Font(Font.MONOSPACED, Font.PLAIN, 10);

        private final RTextScrollPane scrollPane;

        private CodeMinimap(RTextScrollPane scrollPane) {
            this.scrollPane = scrollPane;
            setOpaque(true);
            setBackground(IdeTheme.EDITOR);
            setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, IdeTheme.BORDER));
            setPreferredSize(new Dimension(SIDE_WIDTH, 1));
            setMinimumSize(new Dimension(64, 1));
            scrollPane.getVerticalScrollBar().addAdjustmentListener(event -> repaint());
            CodeEditor.this.addCaretListener(event -> repaint());
            CodeEditor.this.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent event) {
                    repaint();
                }

                @Override
                public void removeUpdate(DocumentEvent event) {
                    repaint();
                }

                @Override
                public void changedUpdate(DocumentEvent event) {
                    repaint();
                }
            });
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent event) {
                    scrollTo(event.getY());
                }
            });
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseDragged(java.awt.event.MouseEvent event) {
                    scrollTo(event.getY());
                }
            });
            setToolTipText("Click or drag to navigate the file");
        }

        @Override
        public String getToolTipText(java.awt.event.MouseEvent event) {
            int lineCount = CodeEditor.this.getLineCount();
            int contentHeight = Math.max(1, getHeight() - 8);
            int line = Math.max(0, Math.min(
                lineCount - 1,
                (int) ((long) Math.max(0, event.getY() - 4) * lineCount / contentHeight)));
            try {
                int start = CodeEditor.this.getLineStartOffset(line);
                int end = CodeEditor.this.getLineEndOffset(line);
                String text = CodeEditor.this.getText(start, end - start);
                return text.replaceAll("[\\r\\n]+$", "");
            } catch (BadLocationException exception) {
                throw new IllegalStateException("Could not read minimap line", exception);
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (CodeEditor.this.getLineCount() == 0 || getHeight() <= 0) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int lineCount = CodeEditor.this.getLineCount();
            int availableHeight = Math.max(1, getHeight() - 8);
            int contentWidth = Math.max(1, getWidth() - HORIZONTAL_PADDING * 2);
            float lineHeight = (float) availableHeight / lineCount;
            float pixelsPerCharacter = Math.min(
                contentWidth / (float) Math.max(1, getMaximumLineLength()), CHARACTER_WIDTH);

            for (int line = 0; line < lineCount; line++) {
                int y = 4 + Math.round(line * lineHeight);
                drawCodeLine(g, line, y, lineHeight, pixelsPerCharacter, contentWidth);
            }
            paintViewport(g, lineCount, availableHeight);
            paintCaretLine(g, lineCount, availableHeight);
            g.dispose();
        }

        private void drawCodeLine(
                Graphics2D graphics, int line, int y, float lineHeight,
                float pixelsPerCharacter, int contentWidth) {
            try {
                int lineStart = CodeEditor.this.getLineStartOffset(line);
                Token token = CodeEditor.this.getTokenListForLine(line);
                Font font = MINIMAP_FONT;
                float charWidth = font.getSize2D() * 0.6f;
                float scaleX = pixelsPerCharacter / charWidth;
                float scaleY = Math.min(0.35f, Math.max(0.12f, lineHeight / 12f));
                Graphics2D textGraphics = (Graphics2D) graphics.create();
                textGraphics.clipRect(
                    HORIZONTAL_PADDING, 0, contentWidth, getHeight());
                textGraphics.translate(HORIZONTAL_PADDING, y);
                textGraphics.scale(scaleX, scaleY);
                textGraphics.setFont(font);
                textGraphics.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int baseline = Math.round(
                    (lineHeight + font.getSize() * scaleY) / (2 * scaleY));
                while (token != null && token.isPaintable()) {
                    String text = token.getLexeme();
                    if (!text.isEmpty()) {
                        var style = CodeEditor.this.getSyntaxScheme().getStyle(token.getType());
                        Color color = style == null || style.foreground == null
                            ? CodeEditor.this.getForeground() : style.foreground;
                        textGraphics.setColor(color);
                        float x = (token.getOffset() - lineStart) * charWidth;
                        textGraphics.drawString(text, x, baseline);
                    }
                    token = token.getNextToken();
                }
                textGraphics.dispose();
            } catch (BadLocationException exception) {
                throw new IllegalStateException("Could not render minimap line", exception);
            }
        }

        private void paintViewport(Graphics2D graphics, int lineCount, int availableHeight) {
            Point viewPosition = scrollPane.getViewport().getViewPosition();
            int editorLineHeight = Math.max(1, CodeEditor.this.getLineHeight());
            int firstVisible = Math.max(0, viewPosition.y / editorLineHeight);
            int visibleLines = Math.max(
                1, scrollPane.getViewport().getExtentSize().height / editorLineHeight);
            int y = 4 + (int) ((long) firstVisible * availableHeight / lineCount);
            int height = Math.max(
                4, (int) ((long) visibleLines * availableHeight / lineCount));
            height = Math.min(height, getHeight() - y - 4);
            if (height <= 0) {
                return;
            }
            graphics.setColor(new Color(180, 185, 192, 12));
            graphics.fillRect(1, y, getWidth() - 2, height);
            graphics.setColor(new Color(180, 185, 192, 38));
            graphics.drawRect(1, y, getWidth() - 3, height);
        }

        private void paintCaretLine(Graphics2D graphics, int lineCount, int availableHeight) {
            try {
                int line = CodeEditor.this.getLineOfOffset(CodeEditor.this.getCaretPosition());
                int y = 4 + (int) ((long) line * availableHeight / lineCount);
                graphics.setColor(IdeTheme.ACCENT);
                graphics.fillRect(1, y, 2, Math.max(2, Math.round(
                    (float) availableHeight / lineCount)));
            } catch (BadLocationException exception) {
                return;
            }
        }

        private void scrollTo(int y) {
            int lineCount = CodeEditor.this.getLineCount();
            int contentHeight = Math.max(1, getHeight() - 8);
            int line = Math.max(0, Math.min(
                lineCount - 1, (int) ((long) Math.max(0, y - 4) * lineCount / contentHeight)));
            try {
                int offset = CodeEditor.this.getLineStartOffset(line);
                CodeEditor.this.setCaretPosition(offset);
                Rectangle lineBounds = CodeEditor.this.modelToView2D(offset).getBounds();
                CodeEditor.this.scrollRectToVisible(lineBounds);
            } catch (BadLocationException exception) {
                throw new IllegalStateException("Could not navigate to minimap line", exception);
            }
        }

        private int getMaximumLineLength() {
            int maxLength = 1;
            for (int line = 0; line < CodeEditor.this.getLineCount(); line++) {
                try {
                    maxLength = Math.max(maxLength,
                        CodeEditor.this.getLineEndOffset(line)
                            - CodeEditor.this.getLineStartOffset(line));
                } catch (BadLocationException exception) {
                    return maxLength;
                }
            }
            return maxLength;
        }
    }
}
