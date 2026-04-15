package com.career.platform.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.ai.client.LlmClient;
import com.career.platform.job.entity.JobPosting;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.profile.entity.Skill;
import com.career.platform.profile.entity.UserProfile;
import com.career.platform.profile.entity.UserSkill;
import com.career.platform.profile.mapper.SkillMapper;
import com.career.platform.profile.mapper.UserProfileMapper;
import com.career.platform.profile.mapper.UserSkillMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiAgentService {

    private static final List<String> CITY_TERMS = Arrays.asList(
            "beijing", "shanghai", "guangzhou", "shenzhen", "hangzhou", "chengdu",
            "nanjing", "wuhan", "xian", "chongqing", "suzhou", "tianjin",
            "\u5317\u4eac", "\u4e0a\u6d77", "\u5e7f\u5dde", "\u6df1\u5733", "\u676d\u5dde", "\u6210\u90fd"
    );

    private static final List<String> INDUSTRY_TERMS = Arrays.asList(
            "internet", "finance", "education", "medical", "ecommerce", "game",
            "software", "ai", "manufacturing", "logistics",
            "\u4e92\u8054\u7f51", "\u91d1\u878d", "\u6559\u80b2", "\u533b\u7597",
            "\u7535\u5546", "\u6e38\u620f", "\u8f6f\u4ef6", "\u4eba\u5de5\u667a\u80fd"
    );

    private static final List<String> SKILL_HINTS = Arrays.asList(
            "java", "spring", "spring boot", "mysql", "redis", "docker", "kubernetes",
            "python", "pytorch", "tensorflow", "sql", "vue", "react", "javascript",
            "typescript", "go", "linux", "git", "html", "css", "mybatis", "nginx"
    );

    private final LlmClient llmClient;
    private final JobPostingMapper jobPostingMapper;
    private final UserProfileMapper userProfileMapper;
    private final UserSkillMapper userSkillMapper;
    private final SkillMapper skillMapper;
    private final ObjectMapper objectMapper;

    public Map<String, Object> runAgent(Long userId, String message, String preferredTool) {
        String tool = StringUtils.hasText(preferredTool) ? preferredTool.trim() : chooseTool(message);
        Map<String, Object> toolResult = executeTool(tool, userId, message);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(messageItem("user", safe(message)));
        messages.add(messageItem("user", "Tool result JSON:\n" + toJson(toolResult)));

        String answer = llmClient.chat(buildAgentPrompt(tool), messages);
        if (!StringUtils.hasText(answer)) {
            answer = buildLocalAgentSummary(tool, toolResult);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", tool);
        result.put("toolResult", toolResult);
        result.put("answer", answer);
        return result;
    }

    public Map<String, Object> importProfileFromText(Long userId, String fileName, String text, boolean overwriteSkills) {
        UserProfile profile = ensureProfile(userId);
        Map<String, Object> extracted = extractProfileData(text);

        setIfPresent(extracted.get("education"), profile::setEducationLevel);
        setIfPresent(extracted.get("profileSummary"), profile::setProfileSummary);
        setFirstCityCode(profile, extracted.get("preferredCities"));
        writeJson(profile, "skills", extracted.get("skills"));
        profile.setUpdatedAt(LocalDateTime.now());
        if (profile.getId() == null) {
            userProfileMapper.insert(profile);
        } else {
            userProfileMapper.updateById(profile);
        }

        @SuppressWarnings("unchecked")
        List<String> skills = (List<String>) extracted.getOrDefault("skills", Collections.emptyList());
        int savedSkills = syncSkills(profile.getId(), skills, overwriteSkills);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", fileName);
        result.put("profileId", profile.getId());
        result.put("savedSkills", savedSkills);
        result.put("profile", profile);
        result.put("extracted", extracted);
        return result;
    }

    private Map<String, Object> executeTool(String tool, Long userId, String message) {
        switch (tool) {
            case "profile_snapshot":
                return buildProfileSnapshot(userId);
            case "salary_insight":
                return buildSalaryInsight(message);
            case "skill_gap":
                return buildSkillGap(userId, message);
            case "job_match":
                return buildJobMatch(userId, message);
            case "career_path":
                return buildCareerPath(userId, message);
            default:
                return buildOverview();
        }
    }

    private String chooseTool(String message) {
        String normalized = safe(message).toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "profile", "resume", "cv", "\u7b80\u5386", "\u753b\u50cf")) {
            return "profile_snapshot";
        }
        if (containsAny(normalized, "salary", "pay", "compensation", "\u85aa\u8d44", "\u5de5\u8d44")) {
            return "salary_insight";
        }
        if (containsAny(normalized, "gap", "skill", "ability", "\u6280\u80fd", "\u80fd\u529b")) {
            return "skill_gap";
        }
        if (containsAny(normalized, "career", "path", "plan", "growth", "\u804c\u4e1a", "\u89c4\u5212")) {
            return "career_path";
        }
        if (containsAny(normalized, "job", "match", "recommend", "\u5c97\u4f4d", "\u63a8\u8350")) {
            return "job_match";
        }
        return "market_overview";
    }

    private Map<String, Object> buildOverview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overview", jobPostingMapper.overviewStats());
        result.put("topCities", jobPostingMapper.aggregateByCity(8));
        result.put("topIndustries", jobPostingMapper.aggregateByIndustry(8));
        result.put("topSkills", jobPostingMapper.topSkills(10));
        return result;
    }

    private Map<String, Object> buildSalaryInsight(String message) {
        String city = detectTerm(message, CITY_TERMS);
        String industry = detectTerm(message, INDUSTRY_TERMS);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("filters", mapOf("city", city, "industry", industry));
        result.put("trend", jobPostingMapper.salaryTrend(city, industry));
        result.put("education", jobPostingMapper.aggregateByEducation());
        result.put("experience", jobPostingMapper.aggregateByExperience());
        return result;
    }

    private Map<String, Object> buildProfileSnapshot(Long userId) {
        UserProfile profile = ensureProfile(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", profile);
        result.put("skills", loadUserSkillNames(profile));
        result.put("marketOverview", buildOverview());
        return result;
    }

    private Map<String, Object> buildSkillGap(Long userId, String message) {
        UserProfile profile = ensureProfile(userId);
        List<String> userSkills = loadUserSkillNames(profile);
        String target = firstNonBlank(extractQuoted(message), profile.getProfileSummary(), "backend engineer");
        List<Map<String, Object>> marketTopSkills = queryMarketSkills(target, detectTerm(message, CITY_TERMS), 12);

        Set<String> current = userSkills.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<String> missing = new ArrayList<>();
        for (Map<String, Object> item : marketTopSkills) {
            String skill = String.valueOf(item.get("skill"));
            if (!current.contains(skill.toLowerCase(Locale.ROOT))) {
                missing.add(skill);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetJob", target);
        result.put("currentSkills", userSkills);
        result.put("marketSkills", marketTopSkills);
        result.put("missingSkills", missing);
        return result;
    }

    private Map<String, Object> buildJobMatch(Long userId, String message) {
        UserProfile profile = ensureProfile(userId);
        List<String> userSkills = loadUserSkillNames(profile);
        Set<String> userSkillSet = userSkills.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        List<JobPosting> jobs = jobPostingMapper.selectList(
                new LambdaQueryWrapper<JobPosting>()
                        .orderByDesc(JobPosting::getPublishDate)
                        .last("LIMIT 60")
        );

        String city = detectTerm(message, CITY_TERMS);
        String target = firstNonBlank(extractQuoted(message), profile.getProfileSummary(), "");
        List<Map<String, Object>> items = new ArrayList<>();

        for (JobPosting job : jobs) {
            if (StringUtils.hasText(city) && !safe(job.getCity()).toLowerCase(Locale.ROOT).contains(city.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (StringUtils.hasText(target) && !safe(job.getTitle()).toLowerCase(Locale.ROOT).contains(target.toLowerCase(Locale.ROOT))) {
                continue;
            }

            List<String> jobSkills = jobPostingMapper.jobSkills(job.getId());
            List<String> matchedSkills = jobSkills.stream()
                    .filter(skill -> userSkillSet.contains(skill.toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
            if (matchedSkills.isEmpty() && !StringUtils.hasText(target)) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("jobId", job.getId());
            item.put("title", job.getTitle());
            item.put("companyName", job.getCompanyName());
            item.put("city", job.getCity());
            item.put("salaryText", job.getSalaryText());
            item.put("matchedSkills", matchedSkills);
            item.put("matchedSkillCount", matchedSkills.size());
            items.add(item);
            if (items.size() >= 8) {
                break;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profileSummary", profile.getProfileSummary());
        result.put("careerGoal", profile.getProfileSummary());
        result.put("skills", userSkills);
        result.put("items", items);
        return result;
    }

    private Map<String, Object> buildCareerPath(Long userId, String message) {
        UserProfile profile = ensureProfile(userId);
        String target = firstNonBlank(profile.getProfileSummary(), extractQuoted(message), "target role");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentProfile", profile);
        result.put("skills", loadUserSkillNames(profile));
        result.put("suggestedSteps", Arrays.asList(
                "solidify current engineering stack",
                "add one differentiating capability such as distributed systems or AI tooling",
                "build project evidence with measurable outcomes",
                "prepare for the next role: " + target
        ));
        result.put("marketSkills", queryMarketSkills(target, detectTerm(message, CITY_TERMS), 10));
        return result;
    }

    private List<Map<String, Object>> queryMarketSkills(String targetJobType, String city, int limit) {
        String keyword = firstNonBlank(targetJobType, "");
        List<Map<String, Object>> searchRows = jobPostingMapper.searchJobs(keyword, keyword, 0, Math.max(20, limit * 2));
        Map<String, Map<String, Object>> uniqueSkills = new LinkedHashMap<>();

        for (Map<String, Object> row : searchRows) {
            Long jobId = readLong(row.get("id"));
            if (jobId == null) {
                continue;
            }
            if (StringUtils.hasText(city) && !safe(String.valueOf(row.get("city"))).toLowerCase(Locale.ROOT).contains(city.toLowerCase(Locale.ROOT))) {
                continue;
            }
            for (String skill : jobPostingMapper.jobSkills(jobId)) {
                if (!StringUtils.hasText(skill)) {
                    continue;
                }
                uniqueSkills.putIfAbsent(skill, mapOf("skill", skill, "sourceJob", row.get("title")));
                if (uniqueSkills.size() >= limit) {
                    return new ArrayList<>(uniqueSkills.values());
                }
            }
        }
        return new ArrayList<>(uniqueSkills.values());
    }

    private Map<String, Object> extractProfileData(String text) {
        String safeText = safe(text);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("realName", extractByLabels(safeText, "\u59d3\u540d", "Name"));
        result.put("university", extractByLabels(safeText, "\u6bd5\u4e1a\u9662\u6821", "\u5b66\u6821", "University", "College"));
        result.put("major", extractByLabels(safeText, "\u4e13\u4e1a", "Major"));
        result.put("education", detectEducation(safeText));
        result.put("graduationYear", detectGraduationYear(safeText));
        String profileSummary = firstNonBlank(
                extractByLabels(safeText, "\u6c42\u804c\u610f\u5411", "\u76ee\u6807\u5c97\u4f4d", "\u804c\u4e1a\u76ee\u6807", "Target", "Objective"),
                detectCareerGoalFromText(safeText)
        );
        result.put("profileSummary", profileSummary);
        result.put("careerGoal", profileSummary);
        result.put("preferredCities", detectMultiple(safeText, CITY_TERMS));
        result.put("preferredIndustries", detectMultiple(safeText, INDUSTRY_TERMS));
        result.put("skills", detectSkills(safeText));
        return result;
    }

    private List<String> detectSkills(String text) {
        Set<String> skills = new LinkedHashSet<>();
        String normalized = text.toLowerCase(Locale.ROOT);

        for (String skillHint : SKILL_HINTS) {
            if (normalized.contains(skillHint.toLowerCase(Locale.ROOT))) {
                skills.add(skillHint);
            }
        }
        for (Map<String, Object> item : jobPostingMapper.topSkills(120)) {
            String skill = String.valueOf(item.get("skill"));
            if (StringUtils.hasText(skill) && normalized.contains(skill.toLowerCase(Locale.ROOT))) {
                skills.add(skill);
            }
        }
        return new ArrayList<>(skills);
    }

    private String detectEducation(String text) {
        for (String candidate : Arrays.asList("phd", "master", "bachelor", "college", "\u535a\u58eb", "\u7855\u58eb", "\u672c\u79d1", "\u5927\u4e13")) {
            if (text.toLowerCase(Locale.ROOT).contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return null;
    }

    private Integer detectGraduationYear(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(20\\d{2})").matcher(text);
        while (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            if (year >= 2000 && year <= 2100) {
                return year;
            }
        }
        return null;
    }

    private String detectCareerGoalFromText(String text) {
        for (String candidate : Arrays.asList(
                "backend engineer", "frontend engineer", "full stack", "data analyst",
                "algorithm engineer", "product manager",
                "\u540e\u7aef\u5f00\u53d1", "\u524d\u7aef\u5f00\u53d1", "\u5168\u6808",
                "\u6570\u636e\u5206\u6790", "\u7b97\u6cd5\u5de5\u7a0b\u5e08", "\u4ea7\u54c1\u7ecf\u7406"
        )) {
            if (text.toLowerCase(Locale.ROOT).contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return null;
    }

    private int syncSkills(Long profileId, List<String> skills, boolean overwriteSkills) {
        if (overwriteSkills) {
            userSkillMapper.delete(new LambdaQueryWrapper<UserSkill>().eq(UserSkill::getProfileId, profileId));
        }

        List<UserSkill> existing = userSkillMapper.selectList(
                new LambdaQueryWrapper<UserSkill>().eq(UserSkill::getProfileId, profileId)
        );
        Set<Long> existingIds = existing.stream().map(UserSkill::getSkillId).collect(Collectors.toSet());

        int count = 0;
        for (String skillName : skills) {
            if (!StringUtils.hasText(skillName)) {
                continue;
            }
            Long skillId = skillMapper.findIdByName(skillName);
            if (skillId == null) {
                Skill skill = new Skill();
                skill.setSkillName(skillName);
                skill.setCategory("imported");
                skill.setHotScore(0);
                skillMapper.insert(skill);
                skillId = skill.getId();
            }
            if (existingIds.contains(skillId)) {
                continue;
            }
            UserSkill userSkill = new UserSkill();
            userSkill.setProfileId(profileId);
            userSkill.setSkillId(skillId);
            userSkill.setProficiency(3);
            userSkill.setSource("ai_import");
            userSkillMapper.insert(userSkill);
            existingIds.add(skillId);
            count++;
        }
        return count;
    }

    private List<String> loadUserSkillNames(UserProfile profile) {
        if (profile == null) {
            return Collections.emptyList();
        }
        Long profileId = profile.getId();
        List<UserSkill> userSkills = userSkillMapper.selectList(
                new LambdaQueryWrapper<UserSkill>().eq(UserSkill::getProfileId, profileId)
        );
        if (userSkills.isEmpty()) {
            return parseJsonList(profile.getSkills());
        }

        List<Long> skillIds = userSkills.stream().map(UserSkill::getSkillId).distinct().collect(Collectors.toList());
        Map<Long, String> names = skillMapper.selectBatchIds(skillIds).stream()
                .collect(Collectors.toMap(Skill::getId, Skill::getSkillName, (left, right) -> left));
        return userSkills.stream()
                .map(item -> names.get(item.getSkillId()))
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private UserProfile ensureProfile(Long userId) {
        UserProfile profile = userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId).last("LIMIT 1")
        );
        if (profile != null) {
            return profile;
        }
        UserProfile created = new UserProfile();
        created.setUserId(userId);
        created.setSkills("[]");
        created.setCreatedAt(LocalDateTime.now());
        created.setUpdatedAt(LocalDateTime.now());
        userProfileMapper.insert(created);
        return created;
    }

    private void writeJson(UserProfile profile, String field, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            if ("skills".equals(field)) {
                profile.setSkills(json);
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private void setFirstCityCode(UserProfile profile, Object value) {
        if (!(value instanceof List)) {
            return;
        }
        List<Object> cities = (List<Object>) value;
        for (Object city : cities) {
            if (city != null && StringUtils.hasText(String.valueOf(city))) {
                profile.setTargetCityCode(String.valueOf(city).trim());
                return;
            }
        }
    }

    private List<String> parseJsonList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readerForListOf(String.class).readValue(json);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String buildAgentPrompt(String tool) {
        return "You are the career platform AI agent. "
                + "Use the provided tool result as the source of truth. "
                + "Give concise and actionable recommendations grounded in data. "
                + "Current tool: " + tool + ".";
    }

    private String buildLocalAgentSummary(String tool, Map<String, Object> toolResult) {
        return "Tool `" + tool + "` completed successfully. "
                + "Use the returned data to decide next actions in profile, recommendation, and report modules.\n\n"
                + toJson(toolResult);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String extractByLabels(String text, String... labels) {
        for (String label : labels) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile(label + "\\s*[:\\uff1a]\\s*([^\\n\\r]{1,60})")
                    .matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    private List<String> detectMultiple(String text, List<String> dictionary) {
        String normalized = text.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String item : dictionary) {
            if (normalized.contains(item.toLowerCase(Locale.ROOT)) && !result.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }

    private String detectTerm(String text, List<String> dictionary) {
        String normalized = safe(text).toLowerCase(Locale.ROOT);
        for (String item : dictionary) {
            if (normalized.contains(item.toLowerCase(Locale.ROOT))) {
                return item;
            }
        }
        return null;
    }

    private String extractQuoted(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[\"'\\u201c\\u201d]([^\"'\\u201c\\u201d]{2,40})[\"'\\u201c\\u201d]")
                .matcher(safe(text));
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void setIfPresent(Object value, java.util.function.Consumer<String> consumer) {
        if (value instanceof String && StringUtils.hasText((String) value)) {
            consumer.accept(((String) value).trim());
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private Long readLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> mapOf(String key1, Object value1, String key2, Object value2) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key1, value1);
        result.put(key2, value2);
        return result;
    }

    private Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> result = new HashMap<>();
        result.put(key, value);
        return result;
    }

    private Map<String, String> messageItem(String role, String content) {
        Map<String, String> item = new HashMap<>();
        item.put("role", role);
        item.put("content", content);
        return item;
    }
}
