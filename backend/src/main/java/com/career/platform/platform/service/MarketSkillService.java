package com.career.platform.platform.service;

import com.career.platform.job.mapper.JobPostingMapper;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class MarketSkillService {

    private static final Set<String> BLOCKED_EXACT = new LinkedHashSet<>(Arrays.asList(
            "保险", "咨询服务", "电话销售", "网络销售", "销售", "销售经理", "销售专员", "市场营销", "电子商务", "互联网",
            "软件", "运营", "客服", "人力资源", "行政", "文员", "不限", "经验不限", "学历不限", "接受应届生", "应届生",
            "五险一金", "年终奖", "绩效奖金", "带薪年假", "周末双休", "包吃", "包住", "餐补", "房补", "交通补助", "节日福利",
            "免费培训", "招聘", "写字楼", "高提成", "月结", "倒班制", "厂内食堂", "团建福利", "线下授课", "授课老师", "地推销售", "面销", "陌拜", "快手"
    ));

    private static final List<String> BLOCKED_CONTAINS = Arrays.asList(
            "销售", "客服", "福利", "补贴", "奖金", "提成", "双休", "社保", "住宿", "包吃", "包住", "应届",
            "学历", "经验", "不限", "底薪", "补助", "晋升", "高提成", "招聘", "经销商", "驾驶证", "食堂", "餐饮",
            "房产", "保险", "金融", "零售", "汽车", "工厂", "包装工", "分拣工", "客户", "新媒体", "银行",
            "企业服务", "房地产", "医院", "器械", "新能源", "快消", "抖音", "陌拜", "面销", "渠道"
    );

    private static final Map<String, String> NORMALIZED_SKILLS = new LinkedHashMap<>();
    private static final List<String> TECH_KEYWORDS = Arrays.asList(
            "java", "spring", "spring boot", "mysql", "redis", "docker", "kubernetes", "sql", "linux", "python",
            "vue", "react", "javascript", "typescript", "mybatis", "nginx", "git", "html", "css", "go", "oracle",
            "node", "ai", "c++", "c#", "php", "selenium", "jenkins", "hadoop", "spark", "flink", "hive", "elasticsearch",
            "开发", "测试", "运维", "架构", "算法", "数据", "数据库", "前端", "后端", "编程", "云计算", "大数据", "人工智能",
            "机器学习", "深度学习", "网络安全", "信息安全", "嵌入式", "自动化", "爬虫", "建模", "分析"
    );

    static {
        NORMALIZED_SKILLS.put("java", "Java");
        NORMALIZED_SKILLS.put("spring", "Spring");
        NORMALIZED_SKILLS.put("springboot", "Spring Boot");
        NORMALIZED_SKILLS.put("spring boot", "Spring Boot");
        NORMALIZED_SKILLS.put("mysql", "MySQL");
        NORMALIZED_SKILLS.put("redis", "Redis");
        NORMALIZED_SKILLS.put("docker", "Docker");
        NORMALIZED_SKILLS.put("kubernetes", "Kubernetes");
        NORMALIZED_SKILLS.put("sql", "SQL");
        NORMALIZED_SKILLS.put("linux", "Linux");
        NORMALIZED_SKILLS.put("python", "Python");
        NORMALIZED_SKILLS.put("vue", "Vue");
        NORMALIZED_SKILLS.put("react", "React");
        NORMALIZED_SKILLS.put("javascript", "JavaScript");
        NORMALIZED_SKILLS.put("typescript", "TypeScript");
        NORMALIZED_SKILLS.put("mybatis", "MyBatis");
        NORMALIZED_SKILLS.put("nginx", "Nginx");
        NORMALIZED_SKILLS.put("git", "Git");
        NORMALIZED_SKILLS.put("html", "HTML");
        NORMALIZED_SKILLS.put("css", "CSS");
        NORMALIZED_SKILLS.put("go", "Go");
        NORMALIZED_SKILLS.put("oracle", "Oracle");
        NORMALIZED_SKILLS.put("ai", "AI");
        NORMALIZED_SKILLS.put("ps", "Photoshop");
        NORMALIZED_SKILLS.put("c语言", "C");
        NORMALIZED_SKILLS.put("c#", "C#");
        NORMALIZED_SKILLS.put("c++", "C++");
        NORMALIZED_SKILLS.put("nodejs", "Node.js");
        NORMALIZED_SKILLS.put("node.js", "Node.js");
    }

    private final JobPostingMapper jobPostingMapper;

    public MarketSkillService(JobPostingMapper jobPostingMapper) {
        this.jobPostingMapper = jobPostingMapper;
    }

    public List<Map<String, Object>> topSkills(int limit) {
        int safeLimit = Math.max(limit, 1);
        return cleanSkillRows(jobPostingMapper.topSkills(Math.max(safeLimit * 4, 40)), safeLimit);
    }

    public List<Map<String, Object>> topTechnicalSkills(int limit) {
        int safeLimit = Math.max(limit, 1);
        List<Map<String, Object>> cleaned = cleanSkillRows(jobPostingMapper.topSkills(Math.max(safeLimit * 8, 80)), safeLimit * 4);
        List<Map<String, Object>> technical = new ArrayList<>();
        for (Map<String, Object> row : cleaned) {
            if (isTechnicalSkill(stringValue(row.get("skill")))) {
                technical.add(row);
            }
            if (technical.size() >= safeLimit) {
                break;
            }
        }
        if (!technical.isEmpty()) {
            return technical;
        }
        return cleaned.size() > safeLimit ? new ArrayList<>(cleaned.subList(0, safeLimit)) : cleaned;
    }

    public List<Map<String, Object>> cleanSkillRows(List<Map<String, Object>> rawRows, int limit) {
        if (rawRows == null || rawRows.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Object> row : rawRows) {
            String normalized = normalizeSkillName(stringValue(row.get("skill")));
            if (!isSkillLike(normalized)) {
                continue;
            }
            String key = normalized.toLowerCase(Locale.ROOT);
            double count = Math.max(1D, toDouble(row.get("count")));
            Map<String, Object> existing = merged.get(key);
            if (existing == null) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("skill", normalized);
                item.put("count", count);
                merged.put(key, item);
            } else {
                existing.put("count", toDouble(existing.get("count")) + count);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparingDouble(item -> -toDouble(item.get("count"))));
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    public List<String> cleanSkillNames(List<String> rawSkills, int limit) {
        if (rawSkills == null || rawSkills.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String rawSkill : rawSkills) {
            for (String piece : splitSkillPieces(rawSkill)) {
                String normalized = normalizeSkillName(piece);
                if (isSkillLike(normalized)) {
                    unique.add(normalized);
                }
                if (unique.size() >= limit) {
                    return new ArrayList<>(unique);
                }
            }
        }
        return new ArrayList<>(unique);
    }

    public boolean isSkillLike(String rawSkill) {
        String skill = normalizeSkillName(rawSkill);
        if (!StringUtils.hasText(skill) || skill.length() < 2 || skill.length() > 32) {
            return false;
        }
        String lower = skill.toLowerCase(Locale.ROOT);
        if (BLOCKED_EXACT.contains(skill) || BLOCKED_EXACT.contains(lower)) {
            return false;
        }
        for (String blocked : BLOCKED_CONTAINS) {
            if (skill.contains(blocked) || lower.contains(blocked.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (skill.matches("^[0-9.\\-_/]+$")) {
            return false;
        }
        if (skill.matches("^(本科|硕士|博士|大专|中专|高中|应届生|1年|2年|3年|5年|10年).*$")) {
            return false;
        }
        if (skill.endsWith("公司") || skill.endsWith("行业") || skill.endsWith("服务")) {
            return false;
        }
        return skill.matches(".*[A-Za-z].*")
                || skill.matches(".*(开发|设计|测试|运维|建模|算法|架构|数据|编程|前端|后端|数据库|网络|安全|分析).*");
    }

    public boolean isTechnicalSkill(String rawSkill) {
        String skill = normalizeSkillName(rawSkill);
        if (!isSkillLike(skill)) {
            return false;
        }
        String lower = skill.toLowerCase(Locale.ROOT);
        for (String keyword : TECH_KEYWORDS) {
            String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
            if (lower.contains(normalizedKeyword) || skill.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public String inferCapabilityDimension(String rawSkill) {
        String skill = normalizeSkillName(rawSkill);
        String lower = skill.toLowerCase(Locale.ROOT);
        if (lower.contains("java")
                || lower.contains("python")
                || lower.contains("golang")
                || lower.contains("go")
                || lower.contains("spring")
                || lower.contains("后端")
                || lower.contains("前端")
                || lower.contains("全栈")
                || lower.contains("开发")
                || lower.contains("编程")) {
            return "工程开发能力";
        }
        if (lower.contains("mysql")
                || lower.contains("redis")
                || lower.contains("sql")
                || lower.contains("hive")
                || lower.contains("数据")
                || lower.contains("bi")
                || lower.contains("tableau")
                || lower.contains("分析")
                || lower.contains("建模")) {
            return "数据分析与建模能力";
        }
        if (lower.contains("docker")
                || lower.contains("kubernetes")
                || lower.contains("k8s")
                || lower.contains("linux")
                || lower.contains("nginx")
                || lower.contains("运维")
                || lower.contains("云")
                || lower.contains("网络")
                || lower.contains("安全")) {
            return "系统与云平台能力";
        }
        if (lower.contains("ai")
                || lower.contains("机器学习")
                || lower.contains("深度学习")
                || lower.contains("算法")
                || lower.contains("推荐")
                || lower.contains("nlp")) {
            return "算法与智能应用能力";
        }
        return "岗位综合能力";
    }

    public double skillSimilarity(String leftSkill, String rightSkill) {
        String left = normalizeSkillName(leftSkill);
        String right = normalizeSkillName(rightSkill);
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return 0D;
        }
        String leftLower = left.toLowerCase(Locale.ROOT);
        String rightLower = right.toLowerCase(Locale.ROOT);
        if (leftLower.equals(rightLower)) {
            return 1D;
        }
        if (leftLower.contains(rightLower) || rightLower.contains(leftLower)) {
            return 0.88D;
        }

        Set<String> leftTokens = tokenizeSkill(leftLower);
        Set<String> rightTokens = tokenizeSkill(rightLower);
        double tokenJaccard = jaccard(leftTokens, rightTokens);

        int maxLen = Math.max(leftLower.length(), rightLower.length());
        double editScore = 0D;
        if (maxLen > 0) {
            int distance = levenshteinDistance(leftLower, rightLower);
            editScore = Math.max(0D, 1D - (distance * 1.0D / maxLen));
        }

        return Math.max(tokenJaccard, Math.min(0.85D, editScore));
    }

    public String normalizeSkillName(String rawSkill) {
        String skill = stringValue(rawSkill)
                .replace('（', '(')
                .replace('）', ')')
                .replace('，', ',')
                .replace('。', '.')
                .replace('；', ';')
                .trim();
        if (!StringUtils.hasText(skill)) {
            return "";
        }
        String lower = skill.toLowerCase(Locale.ROOT);
        if (NORMALIZED_SKILLS.containsKey(lower)) {
            return NORMALIZED_SKILLS.get(lower);
        }
        return skill;
    }

    private List<String> splitSkillPieces(String rawSkill) {
        String text = stringValue(rawSkill);
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        String[] parts = text.split("[,，、/|]+");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                result.add(part.trim());
            }
        }
        return result;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Set<String> tokenizeSkill(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptySet();
        }
        String[] parts = value.split("[\\s,，、/|+_\\-().]+");
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                tokens.add(part.trim());
            }
        }
        if (tokens.isEmpty()) {
            tokens.add(value);
        }
        return tokens;
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0D;
        }
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        Set<String> inter = new LinkedHashSet<>(left);
        inter.retainAll(right);
        return union.isEmpty() ? 0D : (inter.size() * 1.0D / union.size());
    }

    private int levenshteinDistance(String left, String right) {
        int n = left.length();
        int m = right.length();
        if (n == 0) return m;
        if (m == 0) return n;

        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char lc = left.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = lc == right.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[m];
    }

    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return value == null ? 0D : Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return 0D;
        }
    }
}
