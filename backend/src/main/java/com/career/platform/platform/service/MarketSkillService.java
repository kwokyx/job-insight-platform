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
    public static final String TYPE_SKILL = "skill";
    public static final String TYPE_WELFARE = "welfare";
    public static final String TYPE_EDUCATION = "education";
    public static final String TYPE_SALES_METHOD = "sales_method";
    public static final String TYPE_INDUSTRY = "industry";
    public static final String TYPE_OTHER = "other";


    private static final Set<String> BLOCKED_EXACT = new LinkedHashSet<>(Arrays.asList(
            // ── 福利/薪酬类 ──
            "五险", "五险一金", "社保", "公积金", "包吃", "包住", "包吃包住", "包吃住", "餐补", "房补",
            "交通补", "交通补助", "交通补贴", "话补", "话费补贴", "通讯补贴", "住房补贴", "有房补", "有餐补",
            "年终奖", "年终奖金", "绩效奖金", "全勤奖", "优秀员工奖", "季度奖", "项目奖金",
            "带薪年假", "带薪培训", "双休", "周末双休", "大小周", "弹性工作", "弹性工作制",
            "法定节假日", "节假日正常休息", "节日福利", "生日福利", "定期体检", "员工旅游", "团建活动",
            "团建福利", "免费培训", "岗前培训", "带薪培训", "入职培训", "新人培训",
            "高提成", "高薪", "底薪", "无责底薪", "提成", "月结", "日结", "周结",
            "新人保障", "保底工资", "转正五险", "转正", "试用期",
            "就近分配", "就近安排", "就近上班", "上班时间自由",
            // ── 学历类 ──
            "高中", "初中", "中专", "中技", "中专/中技", "大专", "本科", "硕士", "博士",
            "学历不限", "经验不限", "不限", "接受应届生", "应届生", "MBA", "EMBA",
            // ── 销售方式类 ──
            "陌拜", "面销", "电话销售", "电话邀约", "地推", "地推销售", "直销", "网络销售", "微信营销",
            // ── 行业/岗位标签（非技能）──
            "保险", "咨询服务", "企业服务", "人力资源服务", "批发", "零售", "批发零售",
            "互联网", "电子商务", "房地产", "房产", "新能源", "快消", "快消品",
            "金融", "银行", "证券", "医院", "制造业", "餐饮", "物流",
            // ── 岗位名称（非技能）──
            "销售", "销售经理", "销售专员", "销售代表", "销售顾问", "市场营销",
            "软件", "运营", "客服", "人力资源", "行政", "文员", "前台", "保安", "保洁",
            "招聘", "猎头", "外包",
            // ── 其他杂项 ──
            "写字楼", "写字楼办公", "倒班制", "倒班", "夜班", "白班", "两班倒", "三班倒",
            "厂内食堂", "线下授课", "授课老师",
            "快手", "抖音", "小红书", "淘宝", "拼多多",
            "出差", "无需出差", "偶尔出差", "加班", "加班少",
            "晋升空间", "晋升机会", "晋升", "发展空间", "发展前景"
    ));

    private static final List<String> BLOCKED_CONTAINS = Arrays.asList(
            "销售", "客服", "福利", "补贴", "奖金", "提成", "双休", "社保", "住宿", "包吃", "包住", "应届",
            "学历", "经验", "不限", "底薪", "补助", "晋升", "高提成", "招聘", "经销商", "驾驶证", "食堂", "餐饮",
            "房产", "保险", "金融", "零售", "汽车", "工厂", "包装工", "分拣工", "客户", "新媒体", "银行",
            "企业服务", "房地产", "医院", "器械", "新能源", "快消", "抖音", "陌拜", "面销", "渠道",
            "批发", "加盟", "代理", "经销", "外包", "派遣", "实习", "兼职",
            "公积金", "年终", "全勤", "带薪", "转正", "试用", "无责", "保底",
            "出差", "倒班", "夜班", "加班", "月结", "日结",
            "就近", "分配", "入职", "培训", "旅游", "团建", "体检"
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
        return cleanSkillRows(jobPostingMapper.topSkills(Math.max(safeLimit * 4, 40)), safeLimit, TYPE_SKILL);
    }

    public List<Map<String, Object>> topTechnicalSkills(int limit) {
        int safeLimit = Math.max(limit, 1);
        List<Map<String, Object>> cleaned = cleanSkillRows(jobPostingMapper.topSkills(Math.max(safeLimit * 8, 80)), safeLimit * 4, TYPE_SKILL);
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
        return cleanSkillRows(rawRows, limit, TYPE_SKILL);
    }

    public List<Map<String, Object>> cleanSkillRows(List<Map<String, Object>> rawRows, int limit, String expectedType) {
        if (rawRows == null || rawRows.isEmpty()) {
            return Collections.emptyList();
        }
        String normalizedExpectedType = normalizeEntityType(expectedType);
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Object> row : rawRows) {
            String normalized = normalizeSkillName(stringValue(row.get("skill")));
            String entityType = classifyEntityType(normalized);
            if (!matchesEntityType(entityType, normalizedExpectedType)) {
                continue;
            }
            String key = normalized.toLowerCase(Locale.ROOT);
            double count = Math.max(1D, toDouble(row.get("count")));
            Map<String, Object> existing = merged.get(key);
            if (existing == null) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("skill", normalized);
                item.put("count", count);
                item.put("type", entityType);
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
        // Pure numeric / punctuation
        if (skill.matches("^[0-9.\\-_/]+$")) {
            return false;
        }
        // Education / experience prefixes
        if (skill.matches("^(本科|硕士|博士|大专|中专|中技|高中|初中|应届生|1年|2年|3年|5年|10年).*$")) {
            return false;
        }
        // Entity suffixes that indicate non-skill
        if (skill.endsWith("公司") || skill.endsWith("行业") || skill.endsWith("服务")
                || skill.endsWith("有限") || skill.endsWith("集团") || skill.endsWith("工厂")
                || skill.endsWith("中心") || skill.endsWith("学校") || skill.endsWith("学院")) {
            return false;
        }
        // Salary / benefit keywords
        if (lower.matches(".*(万|千|元/月|元/天|k/月|薪|工资|底薪|提成|奖金|补贴|福利|社保|公积金).*")) {
            return false;
        }
        // Very short generic Chinese words that are NOT skill-like
        if (skill.length() == 2 && skill.matches("^[\u4e00-\u9fa5]{2}$")
                && !skill.matches(".*(开发|设计|测试|运维|建模|算法|架构|数据|编程|前端|后端|安全|分析|运营).*")
                && !NORMALIZED_SKILLS.containsKey(lower)) {
            // 2-char Chinese words that don't look like tech skills — reject them
            // (but only if they don't contain any known skill substring)
            boolean hasSkillIndicator = TECH_KEYWORDS.stream().anyMatch(k -> lower.contains(k.toLowerCase(Locale.ROOT)));
            if (!hasSkillIndicator) {
                return false;
            }
        }
        return skill.matches(".*[A-Za-z].*")
                || skill.matches(".*(开发|设计|测试|运维|建模|算法|架构|数据|编程|前端|后端|数据库|网络|安全|分析|嵌入|自动化|爬虫|机器|深度|智能|容器|微服务|中间件|框架|组件|工具|接口|协议|存储|缓存|消息|搜索|监控|可视化|报表).*");
    }

    public String classifyEntityType(String rawSkill) {
        String skill = normalizeSkillName(rawSkill);
        if (!StringUtils.hasText(skill)) {
            return TYPE_OTHER;
        }
        String lower = skill.toLowerCase(Locale.ROOT);
        if (matchesEducation(skill, lower)) {
            return TYPE_EDUCATION;
        }
        if (matchesWelfare(skill, lower)) {
            return TYPE_WELFARE;
        }
        if (matchesSalesMethod(skill, lower)) {
            return TYPE_SALES_METHOD;
        }
        if (matchesIndustry(skill, lower)) {
            return TYPE_INDUSTRY;
        }
        if (isSkillLike(skill)) {
            return TYPE_SKILL;
        }
        return TYPE_OTHER;
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

    private boolean matchesEntityType(String entityType, String expectedType) {
        if (!StringUtils.hasText(expectedType) || "all".equals(expectedType)) {
            return true;
        }
        return expectedType.equals(entityType);
    }

    private String normalizeEntityType(String expectedType) {
        if (!StringUtils.hasText(expectedType)) {
            return TYPE_SKILL;
        }
        String normalized = expectedType.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case TYPE_SKILL:
            case TYPE_WELFARE:
            case TYPE_EDUCATION:
            case TYPE_SALES_METHOD:
            case TYPE_INDUSTRY:
            case TYPE_OTHER:
            case "all":
                return normalized;
            default:
                return TYPE_SKILL;
        }
    }

    private boolean matchesEducation(String skill, String lower) {
        return skill.matches("^(初中|高中|中专|中技|大专|本科|硕士|博士|学历不限|经验不限).*$")
                || lower.contains("bachelor")
                || lower.contains("master")
                || lower.contains("phd");
    }

    private boolean matchesWelfare(String skill, String lower) {
        return skill.matches(".*(五险|五险一金|社保|公积金|包吃|包住|餐补|房补|交通补|交通补贴|年终奖|全勤奖|带薪|双休|弹性工作|法定节假日|福利|补贴|奖金|底薪|提成|保底|转正).*")
                || lower.contains("bonus")
                || lower.contains("benefit");
    }

    private boolean matchesSalesMethod(String skill, String lower) {
        return skill.matches(".*(面销|电话销售|电话邀约|陌拜|地推|直销|网络销售|渠道销售|微信营销).*")
                || lower.contains("sales");
    }

    private boolean matchesIndustry(String skill, String lower) {
        return skill.matches(".*(批发|零售|咨询服务|企业服务|人力资源服务|电子商务|互联网|房地产|新能源|金融|物流|制造业|快消|保险|银行).*")
                || lower.contains("industry")
                || lower.contains("service");
    }
}
