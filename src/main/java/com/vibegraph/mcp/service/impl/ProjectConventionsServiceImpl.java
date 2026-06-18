package com.vibegraph.mcp.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.vibegraph.mcp.dto.response.ProjectConventionsResponse;
import com.vibegraph.mcp.dto.response.ProjectConventionsResponse.Section;
import com.vibegraph.mcp.service.ProjectConventionsService;

/**
 * Reads curated repo conventions from a markdown memory file (config-controlled path, not user
 * input) and parses {@code ## Section} headings into structured, bounded sections.
 */
@Service
public class ProjectConventionsServiceImpl implements ProjectConventionsService {

    private static final long MAX_FILE_BYTES = 256 * 1024;
    private static final int MAX_SECTIONS = 50;
    private static final int MAX_ITEMS_PER_SECTION = 100;
    private static final int MAX_ITEM_LENGTH = 1000;

    @Value("${vibegraph.ai-memory.path:VibeGraph-specs-2month/ai-memory.md}")
    private String memoryPath;

    @Override
    public ProjectConventionsResponse getConventions() {
        Path path = Path.of(memoryPath);
        String relative = memoryPath.replace('\\', '/');

        if (!Files.isRegularFile(path)) {
            return ProjectConventionsResponse.builder()
                    .available(false)
                    .source(relative)
                    .sections(List.of())
                    .warnings(List.of("AI memory file not found: " + relative
                            + ". Create it to expose durable conventions, or set vibegraph.ai-memory.path."))
                    .notes(List.of())
                    .build();
        }

        try {
            if (Files.size(path) > MAX_FILE_BYTES) {
                return ProjectConventionsResponse.builder()
                        .available(false)
                        .source(relative)
                        .sections(List.of())
                        .warnings(List.of("AI memory file is too large to serve safely."))
                        .notes(List.of())
                        .build();
            }
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<Section> sections = parse(lines);
            return ProjectConventionsResponse.builder()
                    .available(true)
                    .source(relative)
                    .sections(sections)
                    .warnings(List.of())
                    .notes(List.of("Curated conventions read from " + relative
                            + ". Treat as durable guidance for editing this repo."))
                    .build();
        } catch (IOException ex) {
            return ProjectConventionsResponse.builder()
                    .available(false)
                    .source(relative)
                    .sections(List.of())
                    .warnings(List.of("AI memory file could not be read as UTF-8 text."))
                    .notes(List.of())
                    .build();
        }
    }

    private List<Section> parse(List<String> lines) {
        List<Section> sections = new ArrayList<>();
        String currentTitle = null;
        List<String> currentItems = new ArrayList<>();
        boolean inFence = false;

        for (String raw : lines) {
            String line = raw.strip();
            if (line.startsWith("```")) {
                inFence = !inFence;
                continue;
            }
            if (inFence) {
                continue;
            }
            if (line.startsWith("## ")) {
                flush(sections, currentTitle, currentItems);
                currentTitle = line.substring(3).strip();
                currentItems = new ArrayList<>();
                continue;
            }
            if (line.startsWith("# ") || line.isEmpty() || currentTitle == null) {
                continue;
            }
            String item = line.startsWith("- ") || line.startsWith("* ") ? line.substring(2).strip() : line;
            if (!item.isEmpty() && currentItems.size() < MAX_ITEMS_PER_SECTION) {
                currentItems.add(item.length() > MAX_ITEM_LENGTH ? item.substring(0, MAX_ITEM_LENGTH) + "…" : item);
            }
        }
        flush(sections, currentTitle, currentItems);
        return sections.size() > MAX_SECTIONS ? sections.subList(0, MAX_SECTIONS) : sections;
    }

    private void flush(List<Section> sections, String title, List<String> items) {
        if (title != null && !items.isEmpty()) {
            sections.add(Section.builder().title(title).items(List.copyOf(items)).build());
        }
    }
}
