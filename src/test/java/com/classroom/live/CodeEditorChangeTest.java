package com.classroom.live;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class CodeEditorChangeTest {
    @Test
    void findsChangedLinesWithoutIncludingUnchangedSurroundingLines() {
        assertArrayEquals(new int[] {1, 1}, CodeEditor.changedLineRange(
            "first\nbefore\nlast", "first\nafter\nlast"));
    }

    @Test
    void findsInsertedAndDeletedLineRanges() {
        assertArrayEquals(new int[] {1, 2}, CodeEditor.changedLineRange(
            "first\nlast", "first\nnew one\nnew two\nlast"));
        assertArrayEquals(new int[] {1, 1}, CodeEditor.changedLineRange(
            "first\nremoved\nlast", "first\nlast"));
    }
}
