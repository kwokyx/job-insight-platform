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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    /**
     * 结构化简历解析：从纯文本中提取姓名、手机、邮箱、学历、技能列表、工作经历
     */
    public Map<String, Object> parseResumeStructured(String text) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (text == null || text.trim().isEmpty()) {
            return result;
        }

        // 手机号
        Matcher phoneMatcher = Pattern.compile("(?:1[3-9]\\d{9})").matcher(text);
        if (phoneMatcher.find()) {
            result.put("phone", phoneMatcher.group());
        }

        // 邮箱
        Matcher emailMatcher = Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}").matcher(text);
        if (emailMatcher.find()) {
            result.put("email", emailMatcher.group());
        }

        // 学历
        String[] eduKeywords = {"博士", "硕士", "研究生", "本科", "大专", "专科", "大学", "学院", "MBA", "EMBA"};
        for (String edu : eduKeywords) {
            if (text.contains(edu)) {
                result.put("education", edu);
                break;
            }
        }

        // 技能关键词提取
        Set<String> skills = extractSkillKeywords(text);
        if (!skills.isEmpty()) {
            result.put("skills", new ArrayList<>(skills));
        }

        // 工作年限
        Matcher expMatcher = Pattern.compile("(\\d+)\\s*[年年].*(?:经验|工作|从业)").matcher(text);
        if (!expMatcher.find()) {
            expMatcher = Pattern.compile("(?:经验|工作|从业).*?(\\d+)\\s*[年年]").matcher(text);
        }
        if (expMatcher.find()) {
            result.put("experienceYears", Integer.parseInt(expMatcher.group(1)));
        }

        // 姓名（简历第一行通常是姓名，2-4个中文字符）
        String[] lines = text.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches("^[\\u4e00-\\u9fa5]{2,4}$")) {
                result.put("name", trimmed);
                break;
            }
        }

        return result;
    }

    /**
     * 从文本中提取IT/职场技能关键词
     */
    private Set<String> extractSkillKeywords(String text) {
        // 常见技术技能词典
        String[] skillDict = {
            "Java", "Python", "JavaScript", "TypeScript", "C++", "C#", "Go", "Rust", "PHP", "Ruby",
            "Spring Boot", "Spring Cloud", "Spring MVC", "Spring", "Django", "Flask", "FastAPI",
            "Vue", "React", "Angular", "Node.js", "Express", "Next.js",
            "MySQL", "PostgreSQL", "Oracle", "SQL Server", "MongoDB", "Redis", "Elasticsearch",
            "Docker", "Kubernetes", "K8s", "Linux", "Nginx", "Git", "Jenkins", "CI/CD",
            "AWS", "Azure", "阿里云", "腾讯云", "GCP",
            "HTML", "CSS", "REST", "GraphQL", "gRPC", "微服务", "分布式",
            "机器学习", "深度学习", "NLP", "自然语言处理", "计算机视觉", "人工智能", "AI",
            "TensorFlow", "PyTorch", "Hadoop", "Spark", "Flink", "Kafka", "RabbitMQ",
            "数据分析", "数据挖掘", "大数据", "BI", "Tableau", "Power BI",
            "产品管理", "项目管理", "PMP", "Scrum", "Agile", "敏捷开发",
            "UI设计", "UX设计", "Figma", "Sketch", "Photoshop", "Illustrator",
            "运营", "SEO", "SEM", "新媒体", "内容运营", "用户运营",
            "会计", "审计", "财务分析", "税务", "CPA", "CFA",
            "人力资源", "招聘", "薪酬", "绩效", "培训",
            "市场营销", "品牌策划", "公关", "商务拓展", "BD",
            "英语", "日语", "韩语", "法语", "德语",
            "沟通能力", "团队协作", "领导力", "创新能力", "学习能力"
        };

        Set<String> found = new LinkedHashSet<>();
        String textLower = text.toLowerCase();
        for (String skill : skillDict) {
            if (textLower.contains(skill.toLowerCase())) {
                found.add(skill);
            }
        }
        return found;
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
