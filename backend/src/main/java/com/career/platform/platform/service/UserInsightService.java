package com.career.platform.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.profile.entity.UserProfile;
import com.career.platform.profile.mapper.UserProfileMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserInsightService {

    private final UserProfileMapper userProfileMapper;
    private final JobPostingMapper jobPostingMapper;
    private final MarketSkillService marketSkillService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> loadUserContext(Long userId) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("userId", userId);
        context.put("profileReady", false);
        context.put("skills", Collections.emptyList());
        context.put("targetCityCode", null);
        context.put("targetProvinceCode", null);
        context.put("targetRegionCode", null);
        context.put("profileCompletenessScore", 0);

        if (userId == null) {
            return context;
        }

        UserProfile profile = userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>()
                        .eq(UserProfile::getUserId, userId)
                        .last("LIMIT 1")
        );
        if (profile == null) {
            return context;
        }

        List<String> skills = loadUserSkillNames(profile.getId(), profile.getSkills());

        context.put("profileReady", true);
        context.put("profileId", profile.getId());
        context.put("majorId", profile.getMajorId());
        context.put("educationLevel", defaultString(profile.getEducationLevel()));
        context.put("targetRegionCode", defaultString(profile.getTargetRegionCode()));
        context.put("targetProvinceCode", defaultString(profile.getTargetProvinceCode()));
        context.put("targetCityCode", defaultString(profile.getTargetCityCode()));
        context.put("expectedSalaryMin", profile.getExpectedSalaryMin());
        context.put("expectedSalaryMax", profile.getExpectedSalaryMax());
        context.put("targetJobCategoryId", profile.getTargetJobCategoryId());
        context.put("profileSummary", defaultString(profile.getProfileSummary()));
        context.put("skills", skills);
        context.put("profileCompletenessScore", calculateCompleteness(profile, skills));
        return context;
    }

    public Map<String, Object> buildPlatformAdvisory(Long userId) {
        Map<String, Object> userContext = loadUserContext(userId);
        List<Map<String, Object>> topSkills = marketSkillService.topSkills(20);
        Map<String, Object> overview = safeMap(jobPostingMapper.overviewStats());

        Set<String> userSkillSet = toLowerSet(toStringList(userContext.get("skills")));
        int matched = 0;
        List<Map<String, Object>> missingSkills = new ArrayList<>();
        for (Map<String, Object> item : topSkills) {
            String skill = String.valueOf(item.getOrDefault("skill", ""));
            if (!StringUtils.hasText(skill)) {
                continue;
            }
            if (userSkillSet.contains(skill.toLowerCase(Locale.ROOT))) {
                matched++;
            } else if (missingSkills.size() < 8) {
                Map<String, Object> gap = new LinkedHashMap<>();
                gap.put("skill", skill);
                gap.put("demandCount", item.getOrDefault("count", 0));
                missingSkills.add(gap);
            }
        }

        int marketAlignmentScore = topSkills.isEmpty() ? 0 : percentage(matched, topSkills.size());
        List<String> risks = buildRisks(userContext, marketAlignmentScore, missingSkills);
        List<Map<String, Object>> actions = buildActions(userContext, missingSkills, marketAlignmentScore);

        Map<String, Object> advisory = new LinkedHashMap<>();
        advisory.put("userContext", userContext);
        advisory.put("marketOverview", overview);
        advisory.put("marketTopSkills", topSkills);
        advisory.put("marketAlignmentScore", marketAlignmentScore);
        advisory.put("profileCompletenessScore", userContext.getOrDefault("profileCompletenessScore", 0));
        advisory.put("matchedSkillCount", matched);
        advisory.put("marketSkillCount", topSkills.size());
        advisory.put("missingSkills", missingSkills);
        advisory.put("risks", risks);
        advisory.put("actions", actions);
        advisory.put("quickLinks", buildQuickLinks());
        return advisory;
    }

    public String buildPromptContext(Long userId) {
        Map<String, Object> context = loadUserContext(userId);
        String profileSummary = String.valueOf(context.getOrDefault("profileSummary", ""));
        List<String> skills = toStringList(context.get("skills"));
        String cityCode = String.valueOf(context.getOrDefault("targetCityCode", ""));
        return "User context: profileSummary=" + profileSummary
                + ", skills=" + String.join(", ", skills)
                + ", targetCityCode=" + cityCode
                + ", profileCompleteness=" + context.getOrDefault("profileCompletenessScore", 0) + "%.";
    }

    public List<String> parseJsonList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return values == null ? Collections.emptyList() : values.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<String> loadUserSkillNames(Long profileId, String profileSkillsJson) {
        return parseJsonList(profileSkillsJson);
    }

    private int calculateCompleteness(UserProfile profile, List<String> skills) {
        int score = 0;
        score += profile.getMajorId() != null ? 15 : 0;
        score += StringUtils.hasText(profile.getEducationLevel()) ? 15 : 0;
        score += StringUtils.hasText(profile.getTargetCityCode()) ? 10 : 0;
        score += profile.getTargetJobCategoryId() != null ? 15 : 0;
        score += StringUtils.hasText(profile.getProfileSummary()) ? 20 : 0;
        score += skills.isEmpty() ? 0 : Math.min(25, skills.size() * 4);
        score += profile.getExpectedSalaryMin() != null || profile.getExpectedSalaryMax() != null ? 10 : 0;
        return Math.min(score, 100);
    }

    private int percentage(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0;
        }
        BigDecimal value = BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP);
        return value.intValue();
    }

    private List<String> buildRisks(Map<String, Object> userContext, int marketAlignmentScore, List<Map<String, Object>> missingSkills) {
        List<String> risks = new ArrayList<>();
        int completeness = readInt(userContext.get("profileCompletenessScore"));
        if (completeness < 60) {
            risks.add("用户画像完整度低于 60%，会直接影响推荐精度和报告可信度。");
        }
        if (marketAlignmentScore < 40) {
            risks.add("当前技能与市场高频能力重合度偏低，短期内岗位命中率会受到明显影响。");
        }
        if (missingSkills.size() >= 5) {
            risks.add("高频缺口技能较多，说明和竞争性岗位之间仍存在明显能力断层。");
        }
        if (!StringUtils.hasText(String.valueOf(userContext.getOrDefault("profileSummary", "")))) {
            risks.add("目标方向或个人摘要未填写，平台难以提供足够具体的求职建议。");
        }
        if (risks.isEmpty()) {
            risks.add("当前未发现明显风险，可持续更新画像和技能，并定期复盘岗位反馈。");
        }
        return risks;
    }

    private List<Map<String, Object>> buildActions(Map<String, Object> userContext, List<Map<String, Object>> missingSkills, int marketAlignmentScore) {
        List<Map<String, Object>> actions = new ArrayList<>();
        int priority = 1;

        if (readInt(userContext.get("profileCompletenessScore")) < 80) {
            actions.add(action(priority++, "补全用户画像", "优先完善教育背景、目标岗位、目标城市和求职摘要。", "/profile"));
        }
        if (!missingSkills.isEmpty()) {
            String topGap = String.valueOf(missingSkills.get(0).get("skill"));
            actions.add(action(priority++, "补齐核心缺口：" + topGap, "结合推荐和技能差距模块制定周学习计划。", "/recommend"));
        }
        actions.add(action(priority++, "生成针对性报告", "重新生成报告并跟踪图表洞察、对比项和行动建议。", "/reports"));
        if (marketAlignmentScore < 70) {
            actions.add(action(priority, "调整投递策略", "优先选择更贴近当前能力层级和目标城市的岗位池。", "/jobs"));
        }
        return actions;
    }

    private Map<String, Object> action(int priority, String title, String detail, String modulePath) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("priority", priority);
        item.put("title", title);
        item.put("detail", detail);
        item.put("modulePath", modulePath);
        return item;
    }

    private Map<String, Object> buildQuickLinks() {
        Map<String, Object> links = new LinkedHashMap<>();
        links.put("profile", "/profile");
        links.put("analysis", "/insights");
        links.put("recommend", "/recommend");
        links.put("report", "/report-center");
        links.put("ai", "/ai");
        return links;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> safeList(Object value) {
        if (value instanceof List) {
            return (List<Map<String, Object>>) value;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<Object> values = (List<Object>) value;
        List<String> result = new ArrayList<>();
        for (Object item : values) {
            if (item != null && StringUtils.hasText(String.valueOf(item))) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private Set<String> toLowerSet(List<String> values) {
        return values.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private int readInt(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }
}
