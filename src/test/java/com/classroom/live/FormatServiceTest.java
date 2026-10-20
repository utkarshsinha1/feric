package com.classroom.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FormatServiceTest {
    @Test
    void formatsJavaWithoutExternalCommand() {
        FormatService.FormatResult result =
            FormatService.formatJava("class Example{void run(){System.out.println(\"ok\");}}");

        assertTrue(result.succeeded(), result.getMessage());
        assertEquals(
            "class Example {\n"
                + "  void run() {\n"
                + "    System.out.println(\"ok\");\n"
                + "  }\n"
                + "}\n",
            result.getFormatted()
        );
    }

    @Test
    void reportsInvalidJavaSource() {
        FormatService.FormatResult result = FormatService.formatJava("class {");

        assertFalse(result.succeeded());
        assertTrue(result.getMessage().startsWith("Java formatting failed:"));
    }
}
