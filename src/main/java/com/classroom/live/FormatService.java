package com.classroom.live;

import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;

public final class FormatService {
    public static final class FormatResult {
        private final String formatted;
        private final String message;

        private FormatResult(String formatted, String message) {
            this.formatted = formatted;
            this.message = message;
        }

        public boolean succeeded() {
            return formatted != null;
        }

        public String getFormatted() {
            return formatted;
        }

        public String getMessage() {
            return message;
        }
    }

    private FormatService() {}

    public static FormatResult formatJava(String source) {
        try {
            return new FormatResult(
                new Formatter().formatSource(source),
                "Formatted with google-java-format."
            );
        } catch (FormatterException e) {
            return new FormatResult(null, "Java formatting failed: " + e.getMessage());
        }
    }
}
