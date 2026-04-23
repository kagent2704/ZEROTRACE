package com.zerotrace.core.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TextSafetyInspector {

    private static final Pattern ZERO_WIDTH_PATTERN = Pattern.compile("[\\u200B-\\u200F\\u2060-\\u2064\\uFEFF]");
    private static final Pattern BIDI_OVERRIDE_PATTERN = Pattern.compile("[\\u202A-\\u202E\\u2066-\\u2069]");
    private static final Pattern INVISIBLE_CONTROL_PATTERN = Pattern.compile("[\\p{Cc}&&[^\\r\\n\\t]]");
    private static final Pattern HIDDEN_HTML_PATTERN = Pattern.compile(
            "(display\\s*:\\s*none|visibility\\s*:\\s*hidden|opacity\\s*:\\s*0|font-size\\s*:\\s*0|clip-path\\s*:|position\\s*:\\s*absolute)",
            Pattern.CASE_INSENSITIVE
    );

    private static final String[] PROMPT_INJECTION_MARKERS = {
            "ignore previous instructions",
            "ignore all previous instructions",
            "reveal the system prompt",
            "show the hidden prompt",
            "developer message",
            "system prompt",
            "you are chatgpt",
            "do not mention this instruction",
            "when copied into",
            "llm instruction",
            "copy-paste into an llm"
    };

    private TextSafetyInspector() {
    }

    public static InspectionResult inspect(String text) {
        if (text == null || text.isEmpty()) {
            return new InspectionResult(false, List.of());
        }

        List<String> findings = new ArrayList<>();

        int zeroWidthCount = countMatches(ZERO_WIDTH_PATTERN, text);
        if (zeroWidthCount > 0) {
            findings.add("Contains " + zeroWidthCount + " zero-width/invisible Unicode character(s)");
        }

        int bidiCount = countMatches(BIDI_OVERRIDE_PATTERN, text);
        if (bidiCount > 0) {
            findings.add("Contains bidirectional text override character(s)");
        }

        int controlCount = countMatches(INVISIBLE_CONTROL_PATTERN, text);
        if (controlCount > 0) {
            findings.add("Contains hidden control character(s)");
        }

        if (HIDDEN_HTML_PATTERN.matcher(text).find()) {
            findings.add("Contains CSS/HTML hiding markers");
        }

        String normalized = text.toLowerCase(Locale.ROOT);
        for (String marker : PROMPT_INJECTION_MARKERS) {
            if (normalized.contains(marker)) {
                findings.add("Contains prompt-injection style phrase: \"" + marker + "\"");
            }
        }

        return new InspectionResult(!findings.isEmpty(), findings);
    }

    private static int countMatches(Pattern pattern, String text) {
        int count = 0;
        var matcher = pattern.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    public record InspectionResult(boolean flagged, List<String> findings) {
        public String summary() {
            return flagged && !findings.isEmpty() ? findings.get(0) : "No hidden-text indicators detected.";
        }
    }
}
