package com.career.platform.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.common.result.R;
import com.career.platform.common.util.SecurityUtils;
import com.career.platform.platform.entity.TeacherMaterialAsset;
import com.career.platform.platform.mapper.TeacherMaterialAssetMapper;
import com.career.platform.system.entity.SysUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "Teacher Student Insights", description = "Teacher retrace and student resume status")
@RestController
@RequestMapping("/api/v1/teacher/student-insights")
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class TeacherStudentInsightController {

    private static final String TYPE_STUDENT_STATUS = "STUDENT_STATUS";

    private final TeacherMaterialAssetMapper materialAssetMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TeacherStudentInsightController(TeacherMaterialAssetMapper materialAssetMapper,
                                           JdbcTemplate jdbcTemplate,
                                           ObjectMapper objectMapper) {
        this.materialAssetMapper = materialAssetMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Retrace student results based on teacher uploaded student status")
    @GetMapping("/retrace")
    public R<?> retrace(@RequestParam(required = false) String major,
                        @RequestParam(required = false) Long ownerUserId) {
        Long resolvedOwnerUserId = SecurityUtils.resolveOwnedUserId(ownerUserId);
        String resolvedMajor = normalizeMajor(major);
        TeacherMaterialAsset asset = loadLatestStudentStatusAsset(resolvedOwnerUserId, resolvedMajor);
        if (asset == null && StringUtils.hasText(resolvedMajor)) {
            asset = loadLatestStudentStatusAsset(resolvedOwnerUserId, null);
        }

        Map<String, Object> summary = asset == null ? Collections.emptyMap() : parseSummary(asset.getSummaryJson());
        String scopeMajor = firstText(resolvedMajor, asset == null ? null : asset.getMajor());
        List<Map<String, Object>> studentProfiles = listStudentProfiles(scopeMajor, 80, 0);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ownerUserId", resolvedOwnerUserId);
        payload.put("major", scopeMajor);
        payload.put("studentStatusUploaded", asset != null);
        payload.put("studentStatusAsset", asset == null ? Collections.emptyMap() : buildAssetPayload(asset));
        payload.put("cohortSummary", buildCohortSummary(summary));
        payload.put("resumeOverview", buildResumeOverview(studentProfiles));
        payload.put("studentProfiles", studentProfiles);
        payload.put("insights", buildTeacherInsights(summary, studentProfiles));
        return R.ok(payload);
    }

    @Operation(summary = "Platform student resume upload status")
    @GetMapping("/resume-status")
    public R<?> resumeStatus(@RequestParam(required = false) String major,
                             @RequestParam(defaultValue = "1") Integer page,
                             @RequestParam(defaultValue = "20") Integer pageSize) {
        String resolvedMajor = normalizeMajor(major);
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safePageSize;

        List<Map<String, Object>> items = listStudentProfiles(resolvedMajor, safePageSize, offset);
        long total = countStudentProfiles(resolvedMajor);
        List<Map<String, Object>> sampleForStats = listStudentProfiles(resolvedMajor, 500, 0);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("major", resolvedMajor == null ? "" : resolvedMajor);
        payload.put("stats", buildResumeOverview(sampleForStats));
        payload.put("items", items);
        payload.put("page", safePage);
        payload.put("pageSize", safePageSize);
        payload.put("total", total);
        return R.ok(payload);
    }

    private TeacherMaterialAsset loadLatestStudentStatusAsset(Long userId, String major) {
        LambdaQueryWrapper<TeacherMaterialAsset> wrapper = new LambdaQueryWrapper<TeacherMaterialAsset>()
                .eq(TeacherMaterialAsset::getUserId, userId)
                .eq(TeacherMaterialAsset::getMaterialType, TYPE_STUDENT_STATUS);
        if (StringUtils.hasText(major)) {
            wrapper.like(TeacherMaterialAsset::getMajor, major.trim());
        }
        List<TeacherMaterialAsset> assets = materialAssetMapper.selectList(
                wrapper.orderByDesc(TeacherMaterialAsset::getUpdatedAt).last("LIMIT 1")
        );
        return assets.isEmpty() ? null : assets.get(0);
    }

    private Map<String, Object> buildAssetPayload(TeacherMaterialAsset asset) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", asset.getId());
        payload.put("major", asset.getMajor());
        payload.put("fileName", asset.getFileName());
        payload.put("rowCount", asset.getRowCount());
        payload.put("updatedAt", asset.getUpdatedAt());
        payload.put("summary", parseSummary(asset.getSummaryJson()));
        return payload;
    }

    private Map<String, Object> buildCohortSummary(Map<String, Object> summary) {
        List<Map<String, String>> sampleRows = readSampleRows(summary.get("samples"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rowCount", readInt(summary.get("rowCount")));
        payload.put("headers", toStringList(summary.get("headers")));
        payload.put("sampleRows", sampleRows);
        payload.put("classNames", extractColumnValues(sampleRows, "班级", "班"));
        payload.put("targetRoles", extractColumnValues(sampleRows, "目标岗位", "岗位"));
        payload.put("weakSkills", extractColumnValues(sampleRows, "能力短板", "短板"));
        payload.put("supportNotes", extractColumnValues(sampleRows, "重点帮扶", "备注", "说明"));
        return payload;
    }

    private Map<String, Object> buildResumeOverview(List<Map<String, Object>> items) {
        int total = items.size();
        long uploaded = items.stream().filter(item -> Boolean.TRUE.equals(item.get("resumeUploaded"))).count();
        long ready = items.stream().filter(item -> Boolean.TRUE.equals(item.get("profileReady"))).count();
        double averageCompleteness = items.isEmpty()
                ? 0D
                : items.stream().mapToInt(item -> readInt(item.get("profileCompleteness"))).average().orElse(0D);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("totalStudents", total);
        payload.put("resumeUploadedCount", uploaded);
        payload.put("profileReadyCount", ready);
        payload.put("resumeUploadedRate", percentage(uploaded, total));
        payload.put("profileReadyRate", percentage(ready, total));
        payload.put("averageCompleteness", Math.round(averageCompleteness));
        return payload;
    }

    private List<String> buildTeacherInsights(Map<String, Object> summary, List<Map<String, Object>> studentProfiles) {
        List<String> insights = new ArrayList<>();
        if (readInt(summary.get("rowCount")) > 0) {
            insights.add("当前回查范围已切换为教师本人上传的学生情况批次，不再跳转到通用学生推荐页面。");
        } else {
            insights.add("当前还没有上传学生情况 Excel，建议先上传班级数据后再做回查。");
        }
        Map<String, Object> overview = buildResumeOverview(studentProfiles);
        insights.add("平台学生简历上传率为 " + overview.get("resumeUploadedRate") + "，画像就绪率为 " + overview.get("profileReadyRate") + "。");
        List<String> weakSkills = extractColumnValues(readSampleRows(summary.get("samples")), "能力短板", "短板");
        if (!weakSkills.isEmpty()) {
            insights.add("教师上传样本中高频短板包括：" + String.join("、", weakSkills.stream().limit(4).collect(Collectors.toList())) + "。");
        }
        return insights;
    }

    private List<Map<String, Object>> listStudentProfiles(String major, int limit, int offset) {
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();
        sql.append("SELECT u.id AS user_id, u.username, u.nickname, u.created_at AS user_created_at, ")
                .append("p.id AS profile_id, p.major_id, p.education_level, p.target_city_code, p.target_job_category_id, ")
                .append("p.expected_salary_min, p.expected_salary_max, p.skills, p.profile_summary, p.updated_at AS profile_updated_at, ")
                .append("m.major_name ")
                .append("FROM sys_user u ")
                .append("LEFT JOIN user_profile p ON p.user_id = u.id ")
                .append("LEFT JOIN dim_major m ON m.id = p.major_id ")
                .append("WHERE u.role_type = ? ");
        params.add(SysUser.ROLE_USER);

        if (StringUtils.hasText(major)) {
            sql.append("AND m.major_name LIKE ? ");
            params.add("%" + major.trim() + "%");
        }
        sql.append("ORDER BY COALESCE(p.updated_at, u.created_at) DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> buildStudentProfileItem(rs));
    }

    private long countStudentProfiles(String major) {
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();
        sql.append("SELECT COUNT(1) ")
                .append("FROM sys_user u ")
                .append("LEFT JOIN user_profile p ON p.user_id = u.id ")
                .append("LEFT JOIN dim_major m ON m.id = p.major_id ")
                .append("WHERE u.role_type = ? ");
        params.add(SysUser.ROLE_USER);
        if (StringUtils.hasText(major)) {
            sql.append("AND m.major_name LIKE ? ");
            params.add("%" + major.trim() + "%");
        }
        Long count = jdbcTemplate.queryForObject(sql.toString(), params.toArray(), Long.class);
        return count == null ? 0L : count;
    }

    private Map<String, Object> buildStudentProfileItem(ResultSet rs) throws SQLException {
        List<String> skills = parseJsonList(rs.getString("skills"));
        int profileCompleteness = calculateCompleteness(rs, skills);
        boolean resumeUploaded = !skills.isEmpty() || StringUtils.hasText(rs.getString("profile_summary"));
        boolean profileReady = resumeUploaded && profileCompleteness >= 60;

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("userId", rs.getLong("user_id"));
        item.put("username", rs.getString("username"));
        item.put("nickname", rs.getString("nickname"));
        item.put("majorName", rs.getString("major_name"));
        item.put("educationLevel", rs.getString("education_level"));
        item.put("targetCity", rs.getString("target_city_code"));
        item.put("targetJob", rs.getString("profile_summary"));
        item.put("skills", skills);
        item.put("skillsCount", skills.size());
        item.put("resumeUploaded", resumeUploaded);
        item.put("profileReady", profileReady);
        item.put("profileCompleteness", profileCompleteness);
        Timestamp updatedAt = rs.getTimestamp("profile_updated_at");
        item.put("updatedAt", updatedAt == null ? null : updatedAt.toLocalDateTime());
        return item;
    }

    private int calculateCompleteness(ResultSet rs, List<String> skills) throws SQLException {
        int score = 0;
        score += rs.getObject("major_id") == null ? 0 : 15;
        score += StringUtils.hasText(rs.getString("education_level")) ? 15 : 0;
        score += StringUtils.hasText(rs.getString("target_city_code")) ? 10 : 0;
        score += rs.getObject("target_job_category_id") == null ? 0 : 15;
        score += StringUtils.hasText(rs.getString("profile_summary")) ? 20 : 0;
        score += skills.isEmpty() ? 0 : Math.min(25, skills.size() * 4);
        score += rs.getObject("expected_salary_min") != null || rs.getObject("expected_salary_max") != null ? 10 : 0;
        return Math.min(score, 100);
    }

    private Map<String, Object> parseSummary(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private List<Map<String, String>> readSampleRows(Object raw) {
        if (!(raw instanceof List)) {
            return Collections.emptyList();
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            ((Map<?, ?>) item).forEach((key, value) -> row.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
            rows.add(row);
        }
        return rows;
    }

    private List<String> extractColumnValues(List<Map<String, String>> rows, String... keywords) {
        Set<String> values = new LinkedHashSet<>();
        for (Map<String, String> row : rows) {
            for (Map.Entry<String, String> entry : row.entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
                boolean matched = false;
                for (String keyword : keywords) {
                    if (key.contains(keyword.toLowerCase(Locale.ROOT))) {
                        matched = true;
                        break;
                    }
                }
                if (matched && StringUtils.hasText(entry.getValue())) {
                    values.add(entry.getValue().trim());
                }
            }
        }
        return new ArrayList<>(values);
    }

    private List<String> parseJsonList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<String> toStringList(Object raw) {
        if (!(raw instanceof List)) {
            return Collections.emptyList();
        }
        return ((List<?>) raw).stream()
                .filter(item -> item != null && StringUtils.hasText(String.valueOf(item)))
                .map(item -> String.valueOf(item).trim())
                .collect(Collectors.toList());
    }

    private int readInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private String percentage(long numerator, int denominator) {
        if (denominator <= 0) {
            return "0%";
        }
        return String.format(Locale.ROOT, "%.1f%%", numerator * 100D / denominator);
    }

    private String normalizeMajor(String major) {
        return StringUtils.hasText(major) ? major.trim() : null;
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return "";
    }
}
