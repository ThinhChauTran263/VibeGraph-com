package com.vibegraph.diagram.service.impl;

import org.springframework.stereotype.Service;

import com.vibegraph.diagram.service.MermaidGeneratorService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MermaidGeneratorServiceImpl implements MermaidGeneratorService {

    private static final String ID_FALLBACK = "n";

    @Override
    public String sanitizeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return ID_FALLBACK;
        }
        StringBuilder sb = new StringBuilder(raw.length());
        boolean lastUnderscore = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
                lastUnderscore = false;
            } else if (!lastUnderscore) {
                sb.append('_');
                lastUnderscore = true;
            }
        }
        // Trim leading/trailing underscores for a tidy id.
        int start = 0;
        int end = sb.length();
        while (start < end && sb.charAt(start) == '_') {
            start++;
        }
        while (end > start && sb.charAt(end - 1) == '_') {
            end--;
        }
        String cleaned = sb.substring(start, end);
        if (cleaned.isEmpty()) {
            return ID_FALLBACK;
        }
        // Mermaid ids must not start with a digit.
        char first = cleaned.charAt(0);
        if (first >= '0' && first <= '9') {
            return ID_FALLBACK + "_" + cleaned;
        }
        return cleaned;
    }

    @Override
    public String escapeLabel(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("#quot;");
                case '\r', '\n', '\t' -> sb.append(' ');
                default -> {
                    if (c < 0x20) {
                        sb.append(' ');
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString().trim();
    }
}
