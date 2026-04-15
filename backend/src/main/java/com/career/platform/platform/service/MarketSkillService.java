package com.career.platform.platform.service;

import com.career.platform.job.mapper.JobPostingMapper;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class MarketSkillService {

    private static final Set<String> BLOCKED_EXACT = new LinkedHashSet<>(Arrays.asList(
            "保险", "计算机软件", "咨询服务", "电话销售", "网络销售", "销售", "销售经理", "销售专员",
            "市场营销", "电子商务", "互联网", "软件", "运营", "客服", "人力资源", "行政", "文员",
            "不限", "经验不限", "学历不限", "接受应届生", "应届生", "五险一金", "年终奖", "绩效奖金",
            "带薪年假", "周末双休", "包吃", "包住", "餐补", "房补", "交通补助", "节日福利", "免费培训"
    ));

    private static final List<String> BLOCKED_CONTAINS = Arrays.asList(
            "销售", "客服", "福利", "补贴", "奖金", "提成", "双休", "社保", "住宿", "包吃", "包住",
            "应届", "学历", "经验", "不限", "底薪", "补助", "晋升", "高提成"
    );

    private static final Map<String, String> NORMALIZED_SKILLS = new LinkedHashMap<>();

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
    }

    private final JobPostingMapper jobPostingMapper;

    public List<Map<String, Object>> topSkills(int limit) {
        int fetchSize = Math.max(limit * 8, 120);
        return cleanSkillRows(jobPostingMapper.topSkills(fetchSize), limit);
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
            double count = toDouble(row.get("count"));
            Map<String, Object> existing = merged.get(key);
            if (existing == null) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("skill", normalized);
                item.put("count", count <= 0 ? 1D : count);
                merged.put(key, item);
            } else {
                existing.put("count", toDouble(existing.get("count")) + (count <= 0 ? 1D : count));
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparingDouble(item -> -toDouble(item.get("count"))));
        if (result.size() > limit) {
            return new ArrayList<>(result.subList(0, limit));
        }
        return result;
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
        if (!StringUtils.hasText(skill)) {
            return false;
        }
        if (skill.length() < 2 || skill.length() > 32) {
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
        return true;
    }

    public String normalizeSkillName(String rawSkill) {
        String skill = stringValue(rawSkill)
                .replace('（', '(')
                .replace('）', ')')
                .replace('，', ',')
                .replace('、', ',')
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
        String[] parts = text.split("[,，、;/|]+");
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
