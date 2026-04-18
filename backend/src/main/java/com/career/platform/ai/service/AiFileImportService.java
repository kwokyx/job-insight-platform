package com.career.platform.ai.service;

import com.career.platform.common.exception.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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

        String orig = file.getOriginalFilename();
        String filename = orig == null ? "" : orig;
        String lower = filename.toLowerCase(Locale.ROOT);

        try {
            if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".json") || lower.endsWith(".csv")) {
                return new String(file.getBytes(), StandardCharsets.UTF_8);
            }
            if (lower.endsWith(".docx")) {
                return extractDocx(file.getBytes());
            }
            if (lower.endsWith(".pdf")) {
                return extractPdf(file.getBytes());
            }
        } catch (IOException e) {
            throw BusinessException.of(400, "Failed to read uploaded file. Please retry with txt, docx, or text-based pdf; scanned/image files need OCR.");
        }

        throw BusinessException.of(400, "Unsupported file type. Use txt, md, json, csv, docx, or text-based pdf");
    }

    private String extractDocx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.joining("\n"));
        }
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = PDDocument.load(bytes)) {
            String text = new PDFTextStripper().getText(document);
            if (text == null || text.trim().length() < 20) {
                throw BusinessException.of(400, "PDF contains little or no extractable text. Image/scanned resumes need OCR.");
            }
            return text;
        }
    }
}
