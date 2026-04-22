package com.career.platform.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.entity.TeacherMaterialAsset;
import com.career.platform.platform.mapper.TeacherMaterialAssetMapper;
import com.career.platform.profile.entity.UserProfile;
import com.career.platform.profile.mapper.UserProfileMapper;
import com.career.platform.system.entity.SysUser;
import com.career.platform.system.mapper.SysUserMapper;
import com.career.platform.warehouse.entity.Curriculum;
import com.career.platform.warehouse.mapper.CurriculumMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReadinessService {

    public static final String DATA_VERSION = "2026-04-22-readiness-v1";

    private final UserProfileMapper userProfileMapper;
    private final CurriculumMapper curriculumMapper;
    private final TeacherMaterialAssetMapper teacherMaterialAssetMapper;
    private final SysUserMapper sysUserMapper;
    private final JobPostingMapper jobPostingMapper;

    public ReadinessService(UserProfileMapper userProfileMapper,
                            CurriculumMapper curriculumMapper,
                            TeacherMaterialAssetMapper teacherMaterialAssetMapper,
                            SysUserMapper sysUserMapper,
                            JobPostingMapper jobPostingMapper) {
        this.userProfileMapper = userProfileMapper;
        this.curriculumMapper = curriculumMapper;
        this.teacherMaterialAssetMapper = teacherMaterialAssetMapper;
        this.sysUserMapper = sysUserMapper;
        this.jobPostingMapper = jobPostingMapper;
    }

    public Map<String, Object> buildReadiness(Long userId, Integer roleType) {
        Integer resolvedRoleType = roleType == null ? SysUser.ROLE_USER : roleType;
        List<String> missingFields = new ArrayList<>();
        Map<String, Object> nextAction = new HashMap<>();
        LocalDateTime updatedAt = LocalDateTime.now();

        if (resolvedRoleType == SysUser.ROLE_TEACHER) {
            fillTeacherReadiness(userId, missingFields, nextAction);
        } else if (resolvedRoleType == SysUser.ROLE_ADMIN) {
            fillAdminReadiness(missingFields, nextAction);
        } else {
            updatedAt = fillStudentReadiness(userId, missingFields, nextAction);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("roleType", resolvedRoleType);
        payload.put("ready", missingFields.isEmpty());
        payload.put("missingFields", missingFields);
        payload.put("nextAction", nextAction);
        payload.put("updatedAt", updatedAt);
        payload.put("dataVersion", DATA_VERSION);
        return payload;
    }

    private LocalDateTime fillStudentReadiness(Long userId, List<String> missingFields, Map<String, Object> nextAction) {
        UserProfile profile = userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>()
                        .eq(UserProfile::getUserId, userId)
                        .last("LIMIT 1")
        );
        LocalDateTime updatedAt = profile == null ? LocalDateTime.now() : profile.getUpdatedAt();
        if (profile == null || !StringUtils.hasText(profile.getProfileSummary())) {
            missingFields.add("profileSummary");
        }
        if (profile == null || !StringUtils.hasText(profile.getTargetCityCode())) {
            missingFields.add("targetCityCode");
        }
        if (profile == null || !hasJsonArrayContent(profile.getSkills())) {
            missingFields.add("skills");
        }
        if (!missingFields.isEmpty()) {
            nextAction.put("path", "/recommend");
            nextAction.put("label", "先上传简历并补齐画像");
            nextAction.put("detail", "上传简历后自动回填技能、求职方向和目标城市，再解锁推荐、薪资分析和报告。");
        } else {
            nextAction.put("path", "/recommend");
            nextAction.put("label", "进入智能推荐");
            nextAction.put("detail", "当前简历画像已齐备，可继续做推荐、简历优化和薪资分析。");
        }
        return updatedAt == null ? LocalDateTime.now() : updatedAt;
    }

    private void fillTeacherReadiness(Long userId, List<String> missingFields, Map<String, Object> nextAction) {
        boolean hasCurriculum = curriculumMapper.selectCount(
                new LambdaQueryWrapper<Curriculum>()
                        .eq(Curriculum::getUploadedBy, userId)
                        .eq(Curriculum::getIsActive, 1)
        ) > 0;
        boolean hasSyllabus = hasTeacherMaterial(userId, "SYLLABUS");
        boolean hasStudentStatus = hasTeacherMaterial(userId, "STUDENT_STATUS");
        if (!hasCurriculum) {
            missingFields.add("courses");
        }
        if (!hasSyllabus) {
            missingFields.add("syllabus");
        }
        if (!hasStudentStatus) {
            missingFields.add("studentStatus");
        }
        if (!missingFields.isEmpty()) {
            nextAction.put("path", "/teacher");
            nextAction.put("label", "先上传课程与教学资料");
            nextAction.put("detail", "教师端需先具备课程清单、教学大纲和学生情况，后续分析与报告才有可信输入。");
        } else {
            nextAction.put("path", "/teacher");
            nextAction.put("label", "进入教师工作台");
            nextAction.put("detail", "当前教学资料已齐备，可继续做课程匹配、教改建议和教师报告。");
        }
    }

    private void fillAdminReadiness(List<String> missingFields, Map<String, Object> nextAction) {
        if (sysUserMapper.selectCount(null) <= 0) {
            missingFields.add("users");
        }
        if (jobPostingMapper.selectCount(null) <= 0) {
            missingFields.add("collectedData");
        }
        if (!missingFields.isEmpty()) {
            nextAction.put("path", "/crawler");
            nextAction.put("label", "先补齐采集与运营数据");
            nextAction.put("detail", "管理员报告依赖采集数据、用户数据和运营看板样本，缺一项都会影响分析结果。");
        } else {
            nextAction.put("path", "/admin");
            nextAction.put("label", "进入运营面板");
            nextAction.put("detail", "当前运营基础数据已到位，可继续做用户治理、运营分析和管理报告。");
        }
    }

    private boolean hasTeacherMaterial(Long userId, String materialType) {
        return teacherMaterialAssetMapper.selectCount(
                new LambdaQueryWrapper<TeacherMaterialAsset>()
                        .eq(TeacherMaterialAsset::getUserId, userId)
                        .eq(TeacherMaterialAsset::getMaterialType, materialType)
        ) > 0;
    }

    private boolean hasJsonArrayContent(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim();
        return normalized.length() > 2 && !"[]".equals(normalized);
    }
}
