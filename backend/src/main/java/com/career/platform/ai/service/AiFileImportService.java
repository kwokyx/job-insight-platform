package com.career.platform.ai.service;

import com.career.platform.common.exception.BusinessException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class AiFileImportService {

    public String extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.of(400, "Uploaded file is empty");
        }

        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lower = filename.toLowerCase(Locale.ROOT);

        try {
            if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".json") || lower.endsWith(".csv")) {
                return new String(file.getBytes(), StandardCharsets.UTF_8);
            }
            if (lower.endsWith(".docx")) {
                return extractDocx(file.getBytes());
            }
        } catch (IOException e) {
            throw BusinessException.of(500, "Failed to read uploaded file");
        }

        throw BusinessException.of(400, "Unsupported file type. Use txt, md, json, csv, or docx");
    }

    private String extractDocx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.joining("\n"));
        }
    }
}
