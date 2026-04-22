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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AiFileImportService {
    private static final long MAX_UPLOAD_SIZE_BYTES = 10L * 1024 * 1024;

    public String extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.of(400, "Uploaded file is empty");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE_BYTES) {
            throw BusinessException.of(400, "Uploaded file exceeds 10MB limit");
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

    public Map<String, Object> parseResumeStructured(String text) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (text == null || text.trim().isEmpty()) {
            return result;
        }

        String normalizedText = text.replace("\r\n", "\n");
        result.put("text", normalizedText);

        putIfNotBlank(result, "name", findLabeledValue(normalizedText, "姓名", "名字", "Name"));
        putIfNotBlank(result, "phone", findLabeledValue(normalizedText, "手机", "手机号", "电话", "联系方式", "Mobile", "Phone"));
        putIfNotBlank(result, "email", findLabeledValue(normalizedText, "邮箱", "邮件", "Email", "E-mail"));

        String targetJob = findLabeledValue(normalizedText, "目标岗位", "目标职位", "意向岗位", "求职岗位", "应聘岗位");
        putIfNotBlank(result, "targetJob", targetJob);
        putIfNotBlank(result, "target_job_type", targetJob);

        String currentJob = findLabeledValue(normalizedText, "当前岗位", "当前职位", "现岗位", "现职位");
        putIfNotBlank(result, "currentJob", currentJob);
        putIfNotBlank(result, "current_job", currentJob);

        String targetCity = findLabeledValue(normalizedText, "目标城市", "意向城市", "期望城市", "目标地区");
        putIfNotBlank(result, "targetCity", targetCity);
        putIfNotBlank(result, "target_city", targetCity);

        putIfNotBlank(result, "industry", findLabeledValue(normalizedText, "目标行业", "意向行业", "行业方向", "所属行业"));
        putIfNotBlank(result, "education", findLabeledValue(normalizedText, "学历", "最高学历", "教育背景"));

        String summary = findMultiLineBlock(normalizedText, "经历摘要", "自我评价", "个人总结", "个人简介");
        if (summary == null) {
            summary = normalizedText;
        }
        putIfNotBlank(result, "resumeText", summary);
        putIfNotBlank(result, "resume_text", summary);

        Matcher phoneMatcher = Pattern.compile("(?:1[3-9]\\d{9})").matcher(normalizedText);
        if (!result.containsKey("phone") && phoneMatcher.find()) {
            result.put("phone", phoneMatcher.group());
        }

        Matcher emailMatcher = Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}").matcher(normalizedText);
        if (!result.containsKey("email") && emailMatcher.find()) {
            result.put("email", emailMatcher.group());
        }

        if (!result.containsKey("education")) {
            for (String edu : new String[]{"博士", "硕士", "研究生", "本科", "大专", "专科", "中专", "MBA", "EMBA"}) {
                if (normalizedText.contains(edu)) {
                    result.put("education", edu);
                    break;
                }
            }
        }

        Set<String> skills = new LinkedHashSet<>();
        skills.addAll(splitSkillLabels(normalizedText, "核心技能", "专业技能", "技能", "技能标签", "专业能力", "工具 / 证书", "工具/证书"));
        skills.addAll(extractSkillKeywords(normalizedText));
        if (!skills.isEmpty()) {
            result.put("skills", new ArrayList<>(skills));
        }

        String labeledYears = findLabeledValue(normalizedText, "工作年限", "经验年限", "从业年限");
        if (labeledYears != null) {
            Matcher labeledMatcher = Pattern.compile("(\\d+)").matcher(labeledYears);
            if (labeledMatcher.find()) {
                int years = Integer.parseInt(labeledMatcher.group(1));
                result.put("experienceYears", years);
                result.put("experience_years", years);
                result.put("experience", years + "年");
            }
        }

        Matcher expMatcher = Pattern.compile("(\\d+)\\s*[年].*(?:经验|工作|从业)").matcher(normalizedText);
        if (!expMatcher.find()) {
            expMatcher = Pattern.compile("(?:经验|工作|从业).*?(\\d+)\\s*[年]").matcher(normalizedText);
        }
        if (!result.containsKey("experienceYears") && expMatcher.find()) {
            int years = Integer.parseInt(expMatcher.group(1));
            result.put("experienceYears", years);
            result.put("experience_years", years);
            result.put("experience", years + "年");
        }

        if (!result.containsKey("name")) {
            for (String line : normalizedText.split("\\n")) {
                String trimmed = line.trim();
                if (trimmed.matches("^[\\u4e00-\\u9fa5]{2,4}$")) {
                    result.put("name", trimmed);
                    break;
                }
            }
        }

        List<String> awardItems = parseLabeledList(normalizedText, "荣誉奖项", "获奖奖项", "奖项荣誉", "竞赛奖项");
        if (!awardItems.isEmpty()) {
            result.put("awards", buildAwardInsights(awardItems));
        }

        List<String> certificateItems = parseLabeledList(normalizedText, "资格证书", "证书", "职业证书");
        if (!certificateItems.isEmpty()) {
            result.put("certificates", certificateItems);
        }

        return result;
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            target.put(key, value.trim());
        }
    }

    private String findLabeledValue(String text, String... labels) {
        for (String label : labels) {
            Pattern pattern = Pattern.compile("(?im)^\\s*[-*•]?\\s*" + Pattern.quote(label) + "\\s*[：:]\\s*(.+?)\\s*$");
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    private String findMultiLineBlock(String text, String... labels) {
        String[] lines = text.split("\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            for (String label : labels) {
                if (line.matches("^[-*•]?\\s*" + Pattern.quote(label) + "\\s*[：:].*$")) {
                    String inline = line.replaceFirst("^[-*•]?\\s*" + Pattern.quote(label) + "\\s*[：:]\\s*", "").trim();
                    List<String> chunks = new ArrayList<>();
                    if (!inline.isEmpty()) {
                        chunks.add(inline);
                    }
                    for (int j = i + 1; j < lines.length; j++) {
                        String next = lines[j].trim();
                        if (next.isEmpty()) {
                            if (!chunks.isEmpty()) {
                                break;
                            }
                            continue;
                        }
                        if (next.matches("^#{1,6}\\s+.*$") || next.matches("^[-*•]?\\s*[\\u4e00-\\u9fa5A-Za-z0-9 /]+\\s*[：:].*$")) {
                            break;
                        }
                        chunks.add(next);
                    }
                    String joined = String.join("\n", chunks).trim();
                    if (!joined.isEmpty()) {
                        return joined;
                    }
                }
            }
        }
        return null;
    }

    private Set<String> splitSkillLabels(String text, String... labels) {
        Set<String> result = new LinkedHashSet<>();
        for (String label : labels) {
            String value = findLabeledValue(text, label);
            if (value == null) {
                continue;
            }
            String[] parts = value.split("[,，、/；;|\\n]+");
            if (parts.length == 1 && value.contains("  ")) {
                parts = value.split("\\s{2,}");
            }
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    private List<String> parseLabeledList(String text, String... labels) {
        List<String> values = new ArrayList<>();
        for (String label : labels) {
            String inline = findLabeledValue(text, label);
            if (inline != null) {
                for (String item : inline.split("[,，、/；;|]+")) {
                    String trimmed = item.trim();
                    if (!trimmed.isEmpty()) {
                        values.add(trimmed);
                    }
                }
            }
        }

        String block = findMultiLineBlock(text, labels);
        if (block != null) {
            for (String line : block.split("\\n")) {
                String trimmed = line.replaceFirst("^[-*•]\\s*", "").trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
        }

        return values.stream().distinct().collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildAwardInsights(List<String> awardItems) {
        List<Map<String, Object>> awards = new ArrayList<>();
        for (String item : awardItems) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", item);
            row.put("level", inferAwardLevel(item));
            row.put("rank", inferAwardRank(item));
            row.put("score", scoreAward(item));
            awards.add(row);
        }
        awards.sort((a, b) -> Integer.compare((Integer) b.get("score"), (Integer) a.get("score")));
        return awards;
    }

    private String inferAwardLevel(String text) {
        if (containsAny(text, "国家级", "全国", "国赛", "国家")) return "国家级";
        if (containsAny(text, "省级", "省赛", "自治区", "直辖市")) return "省级";
        if (containsAny(text, "市级", "校际", "行业")) return "市级/行业级";
        if (containsAny(text, "校级", "院级", "院系")) return "校级";
        return "未识别级别";
    }

    private String inferAwardRank(String text) {
        if (containsAny(text, "特等奖")) return "特等奖";
        if (containsAny(text, "一等奖", "金奖", "冠军")) return "一等奖/金奖";
        if (containsAny(text, "二等奖", "银奖", "亚军")) return "二等奖/银奖";
        if (containsAny(text, "三等奖", "铜奖", "季军")) return "三等奖/铜奖";
        if (containsAny(text, "优秀奖", "优胜奖", "入围", "入选")) return "优秀奖/入围";
        return "未识别奖项等级";
    }

    private int scoreAward(String text) {
        int score = 45;
        if (containsAny(text, "国家级", "全国", "国赛", "国家")) score += 35;
        else if (containsAny(text, "省级", "省赛", "自治区", "直辖市")) score += 25;
        else if (containsAny(text, "市级", "行业")) score += 18;
        else if (containsAny(text, "校级", "院级", "院系")) score += 10;

        if (containsAny(text, "特等奖")) score += 12;
        else if (containsAny(text, "一等奖", "金奖", "冠军")) score += 10;
        else if (containsAny(text, "二等奖", "银奖", "亚军")) score += 7;
        else if (containsAny(text, "三等奖", "铜奖", "季军")) score += 4;
        else if (containsAny(text, "优秀奖", "优胜奖", "入围", "入选")) score += 2;

        return Math.min(score, 99);
    }

    private boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> extractSkillKeywords(String text) {
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
        String textLower = text.toLowerCase(Locale.ROOT);
        for (String skill : skillDict) {
            if (textLower.contains(skill.toLowerCase(Locale.ROOT))) {
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
