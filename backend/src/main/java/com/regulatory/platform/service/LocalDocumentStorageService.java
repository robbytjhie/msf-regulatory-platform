package com.regulatory.platform.service;

import com.regulatory.platform.entity.Document;
import com.regulatory.platform.exception.InvalidRequestException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class LocalDocumentStorageService {

    @Value("${app.storage.local.base-dir:local-documents}")
    private String baseDir;

    private Path basePath;

    @PostConstruct
    void init() {
        try {
            basePath = Paths.get(baseDir).toAbsolutePath().normalize();
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new InvalidRequestException("Failed to initialize local document storage: " + e.getMessage());
        }
    }

    public String writePlaceholder(Document doc, String sourceNote) {
        String ext = extensionFromName(doc.getOriginalFileName());
        String storedName = "doc-" + UUID.randomUUID() + ext;
        Path out = resolve(storedName);
        String body = """
                Local stored document placeholder
                Saved at: %s
                Source: %s
                Original filename: %s
                Content-Type: %s
                Size bytes: %s
                Category: %s
                """.formatted(
                LocalDateTime.now(),
                sourceNote,
                safe(doc.getOriginalFileName()),
                safe(doc.getContentType()),
                doc.getFileSizeBytes() == null ? "unknown" : doc.getFileSizeBytes(),
                safe(doc.getDocumentCategory())
        );
        try {
            Files.writeString(out, body, StandardCharsets.UTF_8);
            return storedName;
        } catch (IOException e) {
            throw new InvalidRequestException("Failed to store document locally: " + e.getMessage());
        }
    }

    public Path resolve(String storedFileName) {
        return basePath.resolve(storedFileName).normalize();
    }

    private static String extensionFromName(String name) {
        if (name == null) return ".txt";
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) return ".txt";
        String ext = name.substring(idx);
        if (ext.length() > 10) return ".txt";
        return ext;
    }

    private static String safe(String s) {
        return s == null ? "-" : s;
    }
}
