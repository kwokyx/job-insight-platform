package com.career.platform.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.ai.client.LlmClient;
import com.career.platform.job.entity.JobPosting;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.service.MarketSkillService;
import com.career.platform.profile.entity.Skill;
import com.career.platform.profile.entity.UserProfile;
import com.career.platform.profile.mapper.SkillMapper;
import com.career.platform.profile.mapper.UserProfileMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
public class AiAgentService {

    private static final List<String> CITY_TERMS = Arrays.asList("北京", "上海", "广州", "深圳", "杭州", "成都", "南京", "武汉", "西安", "重庆", "苏州", "天津");
    private static final List<String> INDUSTRY_TERMS = Arrays.asList("互联网", "金融", "教育", "医疗", "电商", "游戏", "软件", "人工智能");
    private static final List<String> ROLE_HINTS = Arrays.asList(
            "后端开发", "前端开发", "全栈开发", "Java开发", "Python开发", "数据分析", "数据工程", "算法工程师", "测试开发", "运维工程师", "产品经理",
            "backend", "frontend", "full stack", "java", "python", "data analyst", "data engineer", "algorithm", "qa", "devops", "product manager"
    );
    private static final List<String> SKILL_HINTS = Arrays.asList(
            "java", "spring", "spring boot", "mysql", "redis", "docker", "kubernetes", "python", "sql", "vue", "react",
            "javascript", "typescript", "go", "linux", "git", "mybatis", "nginx", "elasticsearch", "kafka"
    );

    private final JobPostingMapper jobPostingMapper;
    private final MarketSkillService marketSkillService;
    private final UserProfileMapper userProfileMapper;
    private final SkillMapper skillMapper;
    private final ObjectMapper objectMapper;
    private final LlmClient llmClient;

    public AiAgentService(JobPostingMapper jobPostingMapper, MarketSkillService marketSkillService,
                          UserProfileMapper userProfileMapper, SkillMapper skillMapper,
                          ObjectMapper objectMapper, LlmClient llmClient) {
        this.jobPostingMapper = jobPostingMapper;
        this.marketSkillService = marketSkillService;
        this.userProfileMapper = userProfileMapper;
        this.skillMapper = skillMapper;
        this.objectMapper = objectMapper;
        this.llmClient = llmClient;
    }

    public Map<String, Object> runAgent(Long userId, String message, String preferredTool) {
        List<String> toolPlan = resolveToolPlan(message, preferredTool);
        List<Map<String, Object>> outputs = new ArrayList<>();
        List<Map<String, Object>> trace = new ArrayList<>();
        for (String tool : toolPlan) {
            Map<String, Object> output = executeTool(tool, userId, message);
            outputs.add(output);
            trace.add(traceResult(tool, output));
        }
        Map<String, Object> toolResult = buildCombinedResult(userId, message, toolPlan, outputs);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", toolPlan.isEmpty() ? "market_overview" : toolPlan.get(0));
        result.put("toolPlan", toolPlan);
        result.put("toolTrace", trace);
        result.put("toolResult", toolResult);
        String answer = buildExternalAgentAnswer(message, toolResult);
        boolean externalLlmUsed = StringUtils.hasText(answer);
        if (!externalLlmUsed) {
            answer = buildLocalAgentSummary(toolResult);
        }
        result.put("answer", answer);
        result.put("externalLlmUsed", externalLlmUsed);
        return result;
    }

    public Map<String, Object> importProfileFromText(Long userId, String fileName, String text, boolean overwriteSkills) {
        UserProfile profile = ensureProfile(userId);
        Map<String, Object> extracted = extractProfileData(text);
        setIfPresent(extracted.get("education"), profile::setEducationLevel);
        setIfPresent(extracted.get("profileSummary"), profile::setProfileSummary);
        setFirstCityCode(profile, extracted.get("preferredCities"));

        List<String> mergedSkills = overwriteSkills ? new ArrayList<>() : new ArrayList<>(parseJsonList(profile.getSkills()));
        mergedSkills.addAll(toStringList(extracted.get("skills")));
        mergedSkills = marketSkillService.cleanSkillNames(mergedSkills, 24);
        writeSkills(profile, mergedSkills);

        profile.setUpdatedAt(LocalDateTime.now());
        if (profile.getId() == null) userProfileMapper.insert(profile); else userProfileMapper.updateById(profile);
        syncSkills(mergedSkills);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", fileName);
        result.put("profileId", profile.getId());
        result.put("savedSkills", mergedSkills.size());
        result.put("profile", profile);
        result.put("extracted", extracted);
        result.put("ocrRecommended", shouldRecommendOcr(fileName, text));
        result.put("supportedFormats", Arrays.asList("txt", "md", "json", "csv", "docx", "pdf", "png", "jpg", "jpeg"));
        return result;
    }

    private Map<String, Object> executeTool(String tool, Long userId, String message) {
        switch (tool) {
            case "profile_snapshot": return buildProfileSnapshot(userId);
            case "salary_insight": return buildSalaryInsight(message);
            case "skill_gap": return buildSkillGap(userId, message);
            case "job_match": return buildJobMatch(userId, message);
            case "career_path": return buildCareerPath(userId, message);
            default: return buildOverview();
        }
    }

    private List<String> resolveToolPlan(String message, String preferredTool) {
        if (StringUtils.hasText(preferredTool) && !"auto".equalsIgnoreCase(preferredTool.trim())) return Collections.singletonList(preferredTool.trim());
        String normalized = safe(message).toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "salary", "pay", "compensation", "薪资", "工资", "薪酬")) return Arrays.asList("salary_insight", "market_overview");
        if (containsAny(normalized, "career", "path", "plan", "growth", "职业", "规划", "成长", "晋升")) return Arrays.asList("profile_snapshot", "career_path", "market_overview");
        if (containsAny(normalized, "job", "match", "recommend", "岗位", "推荐", "投递", "机会", "匹配")) return Arrays.asList("profile_snapshot", "job_match");
        if (containsAny(normalized, "profile", "resume", "cv", "gap", "skill", "ability", "简历", "画像", "技能", "能力")) return Arrays.asList("profile_snapshot", "skill_gap");
        return Arrays.asList("profile_snapshot", "market_overview");
    }

    private Map<String, Object> buildOverview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overview", safeMap(jobPostingMapper.overviewStats()));
        result.put("topCities", safeList(jobPostingMapper.aggregateByCity(8)));
        result.put("topIndustries", safeList(jobPostingMapper.aggregateByIndustry(8)));
        result.put("topSkills", marketSkillService.topTechnicalSkills(10));
        return result;
    }

    private Map<String, Object> buildSalaryInsight(String message) {
        String city = detectTerm(message, CITY_TERMS);
        String industry = detectTerm(message, INDUSTRY_TERMS);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("filters", mapOf("city", city, "industry", industry));
        result.put("overview", safeMap(jobPostingMapper.overviewStats()));
        result.put("trend", safeList(jobPostingMapper.salaryTrend(city, industry)));
        result.put("education", safeList(jobPostingMapper.aggregateByEducation()));
        result.put("experience", safeList(jobPostingMapper.aggregateByExperience()));
        return result;
    }

    private Map<String, Object> buildProfileSnapshot(Long userId) {
        UserProfile profile = ensureProfile(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", profile);
        result.put("skills", loadUserSkillNames(profile));
        return result;
    }

    private Map<String, Object> buildSkillGap(Long userId, String message) {
        UserProfile profile = ensureProfile(userId);
        List<String> userSkills = loadUserSkillNames(profile);
        String city = firstNonBlank(detectTerm(message, CITY_TERMS), profile.getTargetCityCode());
        String role = inferTargetRole(profile, message);
        List<Map<String, Object>> marketTopSkills = queryMarketSkills(role, city, 12);
        Set<String> current = userSkills.stream().map(v -> v.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<String> missing = marketTopSkills.stream()
                .map(item -> stringValue(item.get("skill")))
                .filter(StringUtils::hasText)
                .filter(skill -> !current.contains(skill.toLowerCase(Locale.ROOT)))
                .distinct()
                .collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetJob", role);
        result.put("currentSkills", userSkills);
        result.put("marketSkills", marketTopSkills);
        result.put("missingSkills", missing);
        return result;
    }

    private Map<String, Object> buildJobMatch(Long userId, String message) {
        UserProfile profile = ensureProfile(userId);
        List<String> userSkills = loadUserSkillNames(profile);
        Set<String> userSkillSet = userSkills.stream().map(v -> v.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        String city = firstNonBlank(detectTerm(message, CITY_TERMS), profile.getTargetCityCode());
        String keyword = inferSearchKeyword(profile, message);
        List<Map<String, Object>> rows = StringUtils.hasText(keyword)
                ? safeList(jobPostingMapper.searchJobs(keyword, keyword, 0, 12))
                : jobPostingMapper.selectList(new LambdaQueryWrapper<JobPosting>()
                        .select(JobPosting::getId, JobPosting::getTitle, JobPosting::getCompanyName, JobPosting::getCity, JobPosting::getSalaryText, JobPosting::getPublishDate)
                        .orderByDesc(JobPosting::getPublishDate)
                        .last("LIMIT 8"))
                .stream().map(this::mapJob).collect(Collectors.toList());

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long jobId = readLong(row.get("id"));
            if (jobId == null) continue;
            String rowCity = stringValue(row.get("city"));
            if (StringUtils.hasText(city) && !rowCity.toLowerCase(Locale.ROOT).contains(city.toLowerCase(Locale.ROOT))) continue;
            List<String> jobSkills = marketSkillService.cleanSkillNames(jobPostingMapper.jobSkills(jobId), 10);
            List<String> matchedSkills = jobSkills.stream().filter(skill -> userSkillSet.contains(skill.toLowerCase(Locale.ROOT))).collect(Collectors.toList());
            int score = matchedSkills.size() * 20;
            if (StringUtils.hasText(keyword) && stringValue(row.get("title")).toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) score += 25;
            if (StringUtils.hasText(city) && rowCity.toLowerCase(Locale.ROOT).contains(city.toLowerCase(Locale.ROOT))) score += 10;
            if (score <= 0 && !jobSkills.isEmpty()) score = 15;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("jobId", jobId);
            item.put("title", row.get("title"));
            item.put("companyName", row.get("companyName"));
            item.put("city", rowCity);
            item.put("salaryText", row.get("salaryText"));
            item.put("industryName", row.get("industryName"));
            item.put("publishDate", row.get("publishDate"));
            item.put("jobSkills", jobSkills);
            item.put("matchedSkills", matchedSkills);
            item.put("matchedSkillCount", matchedSkills.size());
            item.put("matchScore", Math.min(score, 100));
            items.add(item);
        }
        items.sort((a, b) -> Integer.compare(readInt(b.get("matchScore")), readInt(a.get("matchScore"))));
        if (items.size() > 6) items = new ArrayList<>(items.subList(0, 6));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("keyword", keyword);
        result.put("city", city);
        result.put("profileSummary", profile.getProfileSummary());
        result.put("skills", userSkills);
        result.put("items", items);
        return result;
    }

    private Map<String, Object> buildCareerPath(Long userId, String message) {
        UserProfile profile = ensureProfile(userId);
        String city = firstNonBlank(detectTerm(message, CITY_TERMS), profile.getTargetCityCode());
        String role = inferTargetRole(profile, message);
        List<String> profileSkills = loadUserSkillNames(profile);
        List<String> marketSkills = queryMarketSkills(role, city, 8).stream().map(item -> stringValue(item.get("skill"))).filter(StringUtils::hasText).collect(Collectors.toList());
        List<String> missing = marketSkills.stream().filter(skill -> profileSkills.stream().noneMatch(existing -> existing.equalsIgnoreCase(skill))).limit(5).collect(Collectors.toList());
        List<String> steps = new ArrayList<>();
        steps.add("明确目标岗位画像，围绕“" + role + "”整理一版可投递简历。");
        steps.add("补齐基础技能、接口设计、数据库和排障能力，先做到可独立交付。");
        if (!missing.isEmpty()) steps.add("优先补齐这些高频技能：" + String.join("、", missing) + "。");
        steps.add("准备 2 到 3 个可量化成果的项目案例，突出性能、稳定性或业务结果。");
        steps.add("按目标城市连续跟踪岗位变化，每周复盘一次投递和面试反馈。");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentProfile", profile);
        result.put("skills", profileSkills);
        result.put("suggestedSteps", steps);
        result.put("marketSkills", queryMarketSkills(role, city, 10));
        return result;
    }

    private Map<String, Object> buildCombinedResult(Long userId, String message, List<String> toolPlan, List<Map<String, Object>> outputs) {
        UserProfile profile = ensureProfile(userId);
        List<String> profileSkills = loadUserSkillNames(profile);
        List<Map<String, Object>> items = new ArrayList<>();
        List<Map<String, Object>> marketSkills = new ArrayList<>();
        List<String> missingSkills = new ArrayList<>();
        Map<String, Object> overview = null;

        for (int i = 0; i < toolPlan.size(); i++) {
            String tool = toolPlan.get(i);
            Map<String, Object> output = i < outputs.size() ? outputs.get(i) : Collections.emptyMap();
            if ("job_match".equals(tool)) items.addAll(asList(output.get("items")));
            if ("skill_gap".equals(tool)) {
                marketSkills.addAll(asList(output.get("marketSkills")));
                missingSkills.addAll(toStringList(output.get("missingSkills")));
            }
            if ("career_path".equals(tool) && marketSkills.isEmpty()) marketSkills.addAll(asList(output.get("marketSkills")));
            if ((tool.equals("market_overview") || tool.equals("salary_insight")) && output.get("overview") instanceof Map) overview = asMap(output.get("overview"));
            if ("market_overview".equals(tool) && marketSkills.isEmpty()) marketSkills.addAll(asList(output.get("topSkills")));
        }
        if (overview == null) overview = safeMap(jobPostingMapper.overviewStats());
        if (marketSkills.isEmpty()) marketSkills = marketSkillService.topTechnicalSkills(8);

        List<String> prioritySkills = dedupe(missingSkills);
        if (prioritySkills.isEmpty()) {
            prioritySkills = marketSkills.stream().map(item -> stringValue(item.get("skill"))).filter(StringUtils::hasText).limit(6).collect(Collectors.toList());
        }

        List<String> risks = new ArrayList<>();
        List<String> nextSteps = new ArrayList<>();
        List<String> evidence = new ArrayList<>();

        if (!StringUtils.hasText(profile.getProfileSummary())) {
            risks.add("当前用户画像没有明确目标岗位，建议会偏通用。");
            nextSteps.add("先补全目标岗位和求职方向，再重新执行岗位匹配或成长路径分析。");
        }
        if (!StringUtils.hasText(profile.getTargetCityCode())) {
            risks.add("当前没有设置目标城市，推荐结果无法针对具体地域市场收敛。");
            nextSteps.add("补充目标城市后，再查看岗位推荐和薪资趋势。");
        }
        if (profileSkills.isEmpty()) {
            risks.add("当前画像没有有效技能标签，岗位匹配和技能差距分析可信度偏低。");
            nextSteps.add("上传简历或手动补充 5 到 8 个核心技能。");
        }
        if (!prioritySkills.isEmpty()) nextSteps.add("优先补齐这些高频技能：" + joinCn(prioritySkills, 5));
        if (!items.isEmpty()) nextSteps.add("从候选岗位中选 2 到 3 个对标岗位，反向优化简历关键词和项目描述。");
        if (containsAny(safe(message).toLowerCase(Locale.ROOT), "报告", "report")) nextSteps.add("生成分角色报告，把图表、对比项和行动建议一起固化下来。");

        evidence.add("工具链：" + String.join(" -> ", toolPlan));
        evidence.add("画像技能数：" + profileSkills.size());
        evidence.add("候选岗位数：" + items.size());
        evidence.add("市场高频技能数：" + marketSkills.size());
        evidence.add("市场岗位样本：" + formatNumber(overview.get("totalJobs")) + "，平均薪资约 " + formatSalarySafe(overview.get("avgSalaryMin")) + " - " + formatSalarySafe(overview.get("avgSalaryMax")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executiveSummary", buildExecutiveSummary(profile, toolPlan, overview, prioritySkills, items));
        result.put("evidence", trimList(dedupe(evidence), 6));
        result.put("risks", trimList(dedupe(risks), 5));
        result.put("prioritySkills", trimList(prioritySkills, 8));
        result.put("nextSteps", trimList(dedupe(nextSteps), 6));
        result.put("items", trimMapList(items, 6));
        result.put("profile", profile);
        result.put("profileSkills", profileSkills);
        result.put("marketOverview", overview);
        result.put("marketSkills", trimMapList(marketSkills, 10));
        result.put("toolOutputs", buildLeanToolOutputs(toolPlan, outputs));
        enrichReadableAgentResult(result, profile, message, toolPlan, items, marketSkills, prioritySkills, overview, profileSkills);
        return result;
    }

    private List<Map<String, Object>> buildLeanToolOutputs(List<String> toolPlan, List<Map<String, Object>> outputs) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < toolPlan.size(); i++) {
            String tool = toolPlan.get(i);
            Map<String, Object> output = i < outputs.size() ? outputs.get(i) : Collections.emptyMap();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tool", tool);
            switch (tool) {
                case "profile_snapshot":
                    item.put("skills", trimList(toStringList(output.get("skills")), 8));
                    break;
                case "salary_insight":
                    item.put("trend", trimMapList(asList(output.get("trend")), 8));
                    break;
                case "skill_gap":
                    item.put("targetJob", output.get("targetJob"));
                    item.put("marketSkills", trimMapList(asList(output.get("marketSkills")), 8));
                    item.put("missingSkills", trimList(toStringList(output.get("missingSkills")), 8));
                    break;
                case "job_match":
                    item.put("items", trimMapList(asList(output.get("items")), 5));
                    break;
                case "career_path":
                    item.put("suggestedSteps", trimList(toStringList(output.get("suggestedSteps")), 5));
                    item.put("marketSkills", trimMapList(asList(output.get("marketSkills")), 6));
                    break;
                default:
                    item.put("overview", asMap(output.get("overview")));
                    item.put("topSkills", trimMapList(asList(output.get("topSkills")), 6));
                    break;
            }
            result.add(item);
        }
        return result;
    }

    private String buildExecutiveSummary(UserProfile profile, List<String> toolPlan, Map<String, Object> overview, List<String> prioritySkills, List<Map<String, Object>> items) {
        StringBuilder summary = new StringBuilder();
        summary.append("本次 Agent 调用了 ").append(toolPlan.size()).append(" 个平台工具");
        if (StringUtils.hasText(profile.getProfileSummary())) {
            summary.append("，围绕“").append(profile.getProfileSummary()).append("”做了联合分析。");
        } else {
            summary.append("，但当前用户画像仍不完整。");
        }
        summary.append(" 当前市场样本量约 ").append(formatNumber(overview.get("totalJobs")))
                .append("，平均薪资约 ").append(formatSalarySafe(overview.get("avgSalaryMin")))
                .append(" - ").append(formatSalarySafe(overview.get("avgSalaryMax"))).append("。");
        if (!prioritySkills.isEmpty()) {
            summary.append(" 当前优先级最高的能力缺口主要集中在 ").append(joinReadable(prioritySkills, 4)).append("。");
        }
        if (!items.isEmpty()) {
            Map<String, Object> top = items.get(0);
            summary.append(" 已找到较高匹配度岗位，例如“")
                    .append(stringValue(top.get("title"))).append(" / ")
                    .append(stringValue(top.get("city"))).append("”。");
        }
        return summary.toString();
    }

    private String buildLocalAgentSummary(Map<String, Object> toolResult) {
        StringBuilder summary = new StringBuilder(String.valueOf(toolResult.getOrDefault("executiveSummary", "已完成平台数据分析。")));
        List<String> evidence = toStringList(toolResult.get("evidence"));
        List<String> risks = toStringList(toolResult.get("risks"));
        List<String> nextSteps = toStringList(toolResult.get("nextSteps"));
        if (!evidence.isEmpty()) summary.append("\n\n证据依据：\n- ").append(String.join("\n- ", evidence));
        if (!risks.isEmpty()) summary.append("\n\n主要风险：\n- ").append(String.join("\n- ", risks));
        if (!nextSteps.isEmpty()) {
            summary.append("\n\n下一步建议：\n");
            for (int i = 0; i < Math.min(nextSteps.size(), 5); i++) summary.append(i + 1).append(". ").append(nextSteps.get(i)).append("\n");
        }
        return summary.toString().trim();
    }

    private String buildExternalAgentAnswer(String userMessage, Map<String, Object> toolResult) {
        if (!llmClient.isConfigured()) return "";

        String contextJson;
        try {
            Map<String, Object> compact = new LinkedHashMap<>();
            compact.put("executiveSummary", toolResult.get("executiveSummary"));
            compact.put("evidence", trimList(toStringList(toolResult.get("evidence")), 6));
            compact.put("risks", trimList(toStringList(toolResult.get("risks")), 4));
            compact.put("prioritySkills", trimList(toStringList(toolResult.get("prioritySkills")), 8));
            compact.put("nextSteps", trimList(toStringList(toolResult.get("nextSteps")), 6));
            compact.put("marketOverview", asMap(toolResult.get("marketOverview")));
            compact.put("profileSkills", trimList(toStringList(toolResult.get("profileSkills")), 12));
            compact.put("marketSkills", trimMapList(asList(toolResult.get("marketSkills")), 8));
            compact.put("items", trimMapList(asList(toolResult.get("items")), 5));
            contextJson = objectMapper.writeValueAsString(compact);
        } catch (Exception e) {
            return "";
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(buildMessage("user",
                "用户问题：\n" + safe(userMessage) + "\n\n"
                        + "平台工具分析结果：\n" + contextJson + "\n\n"
                        + "请直接输出给用户看的中文回答，要求："
                        + "1. 先给结论；"
                        + "2. 用数据支撑；"
                        + "3. 给出明确行动建议；"
                        + "4. 不要暴露内部推理，不要说你没有返回。"));

        String answer = llmClient.chat(
                "You are the platform's AI agent. Answer in Chinese using the provided platform data. "
                        + "Use three short sections titled 结论, 数据依据, 立即行动. "
                        + "Be concrete, practical, personalized, and evidence-based. Avoid meta commentary.",
                messages,
                600,
                45
        );
        return sanitizeExternalAnswer(answer);
    }

    private Map<String, String> buildMessage(String role, String content) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("content", content);
        return item;
    }

    private String sanitizeExternalAnswer(String text) {
        String value = safe(text).trim();
        if (!StringUtils.hasText(value)) return "";
        value = value.replaceAll("(?is)^.*?</think>", "").trim();
        value = value.replaceAll("(?is)^```(?:markdown|md)?\\s*", "").replaceAll("(?is)```$", "").trim();
        value = value.replaceAll("(?is)^(okay|ok|alright|sure)[,\\s:.-]*", "").trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("temporarily unavailable")
                || lower.contains("provider is not configured")
                || lower.contains("empty response")) {
            return "";
        }
        return value;
    }

    private List<Map<String, Object>> queryMarketSkills(String role, String city, int limit) {
        List<Map<String, Object>> rows = StringUtils.hasText(role) ? safeList(jobPostingMapper.searchJobs(role, role, 0, 8)) : Collections.emptyList();
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long jobId = readLong(row.get("id"));
            if (jobId == null) continue;
            for (String skill : marketSkillService.cleanSkillNames(jobPostingMapper.jobSkills(jobId), 12)) counter.merge(skill, 1, Integer::sum);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        if (!counter.isEmpty()) {
            counter.entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).limit(limit).forEach(entry -> result.add(skillItem(entry.getKey(), entry.getValue(), role, city)));
        }
        if (result.isEmpty()) {
            for (Map<String, Object> item : marketSkillService.topTechnicalSkills(limit)) result.add(skillItem(stringValue(item.get("skill")), item.get("count"), role, city));
        }
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    private Map<String, Object> extractProfileData(String text) {
        String safeText = safe(text);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("realName", extractByLabels(safeText, "姓名", "Name"));
        result.put("university", extractByLabels(safeText, "毕业院校", "学校", "University", "College"));
        result.put("major", extractByLabels(safeText, "专业", "Major"));
        result.put("education", detectEducation(safeText));
        result.put("graduationYear", detectGraduationYear(safeText));
        String summary = firstNonBlank(extractByLabels(safeText, "求职意向", "目标岗位", "职业目标", "Target", "Objective"), detectCareerGoalFromText(safeText));
        result.put("profileSummary", summary);
        result.put("careerGoal", summary);
        result.put("preferredCities", detectMultiple(safeText, CITY_TERMS));
        result.put("preferredIndustries", detectMultiple(safeText, INDUSTRY_TERMS));
        result.put("skills", detectSkills(safeText));
        return result;
    }

    private List<String> detectSkills(String text) {
        String normalized = safe(text).toLowerCase(Locale.ROOT);
        Set<String> skills = new LinkedHashSet<>();
        for (String hint : SKILL_HINTS) if (normalized.contains(hint.toLowerCase(Locale.ROOT))) skills.add(hint);
        for (Map<String, Object> item : marketSkillService.topTechnicalSkills(60)) {
            String skill = stringValue(item.get("skill"));
            if (StringUtils.hasText(skill) && normalized.contains(skill.toLowerCase(Locale.ROOT))) skills.add(skill);
        }
        return marketSkillService.cleanSkillNames(new ArrayList<>(skills), 24);
    }

    private String detectEducation(String text) {
        for (String candidate : Arrays.asList("博士", "硕士", "本科", "大专", "PhD", "Master", "Bachelor", "College")) {
            if (safe(text).toLowerCase(Locale.ROOT).contains(candidate.toLowerCase(Locale.ROOT))) return candidate;
        }
        return null;
    }

    private Integer detectGraduationYear(String text) {
        Matcher matcher = Pattern.compile("(20\\d{2})").matcher(safe(text));
        while (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            if (year >= 2000 && year <= 2100) return year;
        }
        return null;
    }

    private String detectCareerGoalFromText(String text) {
        String lower = safe(text).toLowerCase(Locale.ROOT);
        for (String hint : ROLE_HINTS) if (lower.contains(hint.toLowerCase(Locale.ROOT))) return hint;
        return null;
    }

    private void syncSkills(List<String> skills) {
        for (String skillName : skills) {
            if (!StringUtils.hasText(skillName) || skillMapper.findIdByName(skillName) != null) continue;
            Skill skill = new Skill();
            skill.setSkillName(skillName);
            skill.setCategory("imported");
            skill.setStatus(1);
            skill.setCreatedAt(LocalDateTime.now());
            skill.setUpdatedAt(LocalDateTime.now());
            skillMapper.insert(skill);
        }
    }

    private List<String> loadUserSkillNames(UserProfile profile) {
        return profile == null ? Collections.emptyList() : marketSkillService.cleanSkillNames(parseJsonList(profile.getSkills()), 24);
    }

    private UserProfile ensureProfile(Long userId) {
        UserProfile profile = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId).last("LIMIT 1"));
        if (profile != null) return profile;
        UserProfile created = new UserProfile();
        created.setUserId(userId);
        created.setSkills("[]");
        created.setCreatedAt(LocalDateTime.now());
        created.setUpdatedAt(LocalDateTime.now());
        userProfileMapper.insert(created);
        return created;
    }

    private void writeSkills(UserProfile profile, Object value) {
        try {
            profile.setSkills(objectMapper.writeValueAsString(value));
        } catch (Exception ignored) {
            profile.setSkills("[]");
        }
    }

    @SuppressWarnings("unchecked")
    private void setFirstCityCode(UserProfile profile, Object value) {
        if (!(value instanceof List)) return;
        for (Object city : (List<Object>) value) {
            if (city != null && StringUtils.hasText(String.valueOf(city))) {
                profile.setTargetCityCode(String.valueOf(city).trim());
                return;
            }
        }
    }

    private List<String> parseJsonList(String json) {
        if (!StringUtils.hasText(json)) return Collections.emptyList();
        try {
            return objectMapper.readerForListOf(String.class).readValue(json);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private boolean shouldRecommendOcr(String fileName, String text) {
        String lower = safe(fileName).toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return true;
        return !StringUtils.hasText(text) || text.trim().length() < 80;
    }

    private Map<String, Object> traceResult(String tool, Map<String, Object> output) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("tool", tool);
        trace.put("label", toolLabel(tool));
        trace.put("summary", summarizeToolOutput(tool, output));
        return trace;
    }

    private String summarizeToolOutput(String tool, Map<String, Object> output) {
        switch (tool) {
            case "profile_snapshot": return "已读取用户画像，识别到技能 " + toStringList(output.get("skills")).size() + " 项";
            case "salary_insight": return "返回薪资趋势 " + asList(output.get("trend")).size() + " 个时间点";
            case "skill_gap": return "识别技能缺口 " + toStringList(output.get("missingSkills")).size() + " 项";
            case "job_match": return "返回岗位候选 " + asList(output.get("items")).size() + " 个";
            case "career_path": return "返回成长步骤 " + toStringList(output.get("suggestedSteps")).size() + " 条";
            default: return "返回市场概览和分布数据";
        }
    }

    private String toolLabel(String tool) {
        switch (tool) {
            case "profile_snapshot": return "画像快照";
            case "salary_insight": return "薪资洞察";
            case "skill_gap": return "技能缺口";
            case "job_match": return "岗位匹配";
            case "career_path": return "成长路径";
            default: return "市场概览";
        }
    }

    private String inferTargetRole(UserProfile profile, String message) {
        return firstNonBlank(extractQuoted(message), extractRole(message), profile == null ? null : profile.getProfileSummary(), "后端开发");
    }

    private String inferSearchKeyword(UserProfile profile, String message) {
        String role = inferTargetRole(profile, message);
        if (!StringUtils.hasText(role)) return null;
        return role.length() > 18 ? firstNonBlank(extractRole(role), role) : role.trim();
    }

    private String extractRole(String text) {
        String lower = safe(text).toLowerCase(Locale.ROOT);
        for (String hint : ROLE_HINTS) if (lower.contains(hint.toLowerCase(Locale.ROOT))) return hint;
        return null;
    }

    private Map<String, Object> skillItem(String skill, Object count, String role, String city) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("skill", skill);
        item.put("count", readInt(count));
        item.put("sourceJob", firstNonBlank(role, "通用岗位") + (StringUtils.hasText(city) ? " / " + city : ""));
        return item;
    }

    private Map<String, Object> mapJob(JobPosting job) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", job.getId());
        row.put("title", job.getTitle());
        row.put("companyName", job.getCompanyName());
        row.put("city", job.getCity());
        row.put("salaryText", job.getSalaryText());
        row.put("publishDate", job.getPublishDate());
        return row;
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List)) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (Object item : (List<?>) value) if (item != null && StringUtils.hasText(String.valueOf(item))) result.add(String.valueOf(item).trim());
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(Object value) {
        if (!(value instanceof List)) return Collections.emptyList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : (List<?>) value) if (item instanceof Map) result.add((Map<String, Object>) item);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<>();
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : value;
    }

    private List<Map<String, Object>> safeList(List<Map<String, Object>> value) {
        return value == null ? Collections.emptyList() : value;
    }

    private List<String> dedupe(List<String> values) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) continue;
            String trimmed = value.trim();
            if (seen.add(trimmed.toLowerCase(Locale.ROOT))) result.add(trimmed);
        }
        return result;
    }

    private String joinCn(List<String> values, int limit) {
        return values.stream().filter(StringUtils::hasText).limit(limit).collect(Collectors.joining("、"));
    }

    private String extractByLabels(String text, String... labels) {
        for (String label : labels) {
            Matcher matcher = Pattern.compile(Pattern.quote(label) + "\\s*[:：]\\s*([^\\n\\r]{1,60})").matcher(safe(text));
            if (matcher.find()) return matcher.group(1).trim();
        }
        return null;
    }

    private List<String> detectMultiple(String text, List<String> dictionary) {
        String normalized = safe(text).toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String item : dictionary) if (normalized.contains(item.toLowerCase(Locale.ROOT)) && !result.contains(item)) result.add(item);
        return result;
    }

    private String detectTerm(String text, List<String> dictionary) {
        String normalized = safe(text).toLowerCase(Locale.ROOT);
        for (String item : dictionary) if (normalized.contains(item.toLowerCase(Locale.ROOT))) return item;
        return null;
    }

    private String extractQuoted(String text) {
        Matcher matcher = Pattern.compile("[\"'“”‘’]([^\"'“”‘’]{2,40})[\"'“”‘’]").matcher(safe(text));
        return matcher.find() ? matcher.group(1) : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (StringUtils.hasText(value)) return value.trim();
        return null;
    }

    private boolean containsAny(String text, String... needles) {
        String normalized = safe(text).toLowerCase(Locale.ROOT);
        for (String needle : needles) if (normalized.contains(needle.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private void enrichReadableAgentResult(
            Map<String, Object> result,
            UserProfile profile,
            String message,
            List<String> toolPlan,
            List<Map<String, Object>> items,
            List<Map<String, Object>> marketSkills,
            List<String> prioritySkills,
            Map<String, Object> overview,
            List<String> profileSkills
    ) {
        List<String> risks = new ArrayList<>();
        List<String> nextSteps = new ArrayList<>();
        List<String> evidence = new ArrayList<>();

        if (!StringUtils.hasText(profile.getProfileSummary())) {
            risks.add("当前用户画像没有明确目标岗位，建议先收敛方向再做更精细的匹配分析。");
            nextSteps.add("先补全目标岗位和求职方向，再重新执行岗位匹配或成长路径分析。");
        }
        if (!StringUtils.hasText(profile.getTargetCityCode())) {
            risks.add("当前没有设置目标城市，推荐结果无法针对具体地域市场收敛。");
            nextSteps.add("补充目标城市后，再查看岗位推荐和薪资趋势。");
        }
        if (profileSkills.isEmpty()) {
            risks.add("当前画像没有有效技能标签，岗位匹配和技能差距分析可信度偏低。");
            nextSteps.add("上传简历或手动补充 5 到 8 个核心技能。");
        }
        if (!prioritySkills.isEmpty()) {
            nextSteps.add("优先补齐这些高频技能：" + joinReadable(prioritySkills, 5));
        }
        if (!items.isEmpty()) {
            nextSteps.add("从候选岗位中选 2 到 3 个对标岗位，反向优化简历关键词和项目描述。");
        }
        if (containsAny(safe(message).toLowerCase(Locale.ROOT), "报告", "report")) {
            nextSteps.add("生成分角色报告，把图表、对比项和行动建议一起固化下来。");
        }

        evidence.add("工具链：" + String.join(" -> ", toolPlan));
        evidence.add("画像技能数：" + profileSkills.size());
        evidence.add("候选岗位数：" + items.size());
        evidence.add("市场高频技能数：" + marketSkills.size());
        evidence.add("市场岗位样本：" + formatNumber(overview.get("totalJobs")) + "，平均薪资约 " + formatSalarySafe(overview.get("avgSalaryMin")) + " - " + formatSalarySafe(overview.get("avgSalaryMax")));

        result.put("executiveSummary", buildExecutiveSummary(profile, toolPlan, overview, prioritySkills, items));
        result.put("evidence", trimList(dedupe(evidence), 6));
        result.put("risks", trimList(dedupe(risks), 5));
        result.put("nextSteps", trimList(dedupe(nextSteps), 6));
    }

    private String joinReadable(List<String> values, int limit) {
        return values.stream().filter(StringUtils::hasText).limit(limit).collect(Collectors.joining("、"));
    }

    private void setIfPresent(Object value, java.util.function.Consumer<String> consumer) {
        if (value instanceof String && StringUtils.hasText((String) value)) consumer.accept(((String) value).trim());
    }

    private String safe(String value) { return value == null ? "" : value; }
    private String stringValue(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private Long readLong(Object value) { try { return value == null ? null : Long.valueOf(String.valueOf(value)); } catch (Exception e) { return null; } }
    private int readInt(Object value) { try { return value instanceof Number ? ((Number) value).intValue() : (value == null ? 0 : (int) Math.round(Double.parseDouble(String.valueOf(value)))); } catch (Exception e) { return 0; } }
    private String formatSalarySafe(Object value) { try { if (value == null) return "--"; double amount = Double.parseDouble(String.valueOf(value)); return amount >= 1000D ? String.format(Locale.US, "%.2fK", amount / 1000D) : String.format(Locale.US, "%.2f", amount); } catch (Exception e) { return String.valueOf(value); } }
    private String formatNumber(Object value) { try { return value == null ? "--" : String.format(Locale.US, "%,.0f", Double.parseDouble(String.valueOf(value))); } catch (Exception e) { return String.valueOf(value); } }
    private List<String> trimList(List<String> values, int limit) { return values == null || values.isEmpty() ? Collections.emptyList() : new ArrayList<>(values.subList(0, Math.min(values.size(), limit))); }
    private List<Map<String, Object>> trimMapList(List<Map<String, Object>> values, int limit) { return values == null || values.isEmpty() ? Collections.emptyList() : new ArrayList<>(values.subList(0, Math.min(values.size(), limit))); }
    private Map<String, Object> mapOf(String key1, Object value1, String key2, Object value2) { Map<String, Object> result = new LinkedHashMap<>(); result.put(key1, value1); result.put(key2, value2); return result; }
}
