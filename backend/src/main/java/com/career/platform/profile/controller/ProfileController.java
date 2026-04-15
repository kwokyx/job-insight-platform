package com.career.platform.profile.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.profile.entity.Skill;
import com.career.platform.profile.entity.UserProfile;
import com.career.platform.profile.entity.UserSkill;
import com.career.platform.profile.mapper.SkillMapper;
import com.career.platform.profile.mapper.UserProfileMapper;
import com.career.platform.profile.mapper.UserSkillMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Profile", description = "User profile and skills")
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserProfileMapper profileMapper;
    private final UserSkillMapper userSkillMapper;
    private final SkillMapper skillMapper;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Get profile")
    @GetMapping
    public R<?> getProfile() {
        Long userId = getCurrentUserId();
        UserProfile profile = ensureProfile(userId);
        List<UserSkill> skills = userSkillMapper.selectList(
                new LambdaQueryWrapper<UserSkill>().eq(UserSkill::getProfileId, profile.getId())
        );

        Map<String, Object> result = new HashMap<>();
        result.put("profile", profile);
        result.put("skills", skills);
        return R.ok(result);
    }

    @Data
    public static class UpdateProfileRequest {
        private Long majorId;
        private String educationLevel;
        private String targetRegionCode;
        private String targetProvinceCode;
        private String targetCityCode;
        private Integer expectedSalaryMin;
        private Integer expectedSalaryMax;
        private Long targetJobCategoryId;
        private List<String> skills;
        private String profileSummary;
    }

    @Operation(summary = "Update profile")
    @PutMapping
    public R<?> updateProfile(@RequestBody UpdateProfileRequest req) {
        Long userId = getCurrentUserId();
        UserProfile profile = ensureProfile(userId);

        if (req.getMajorId() != null) profile.setMajorId(req.getMajorId());
        if (req.getEducationLevel() != null) profile.setEducationLevel(req.getEducationLevel());
        if (req.getTargetRegionCode() != null) profile.setTargetRegionCode(req.getTargetRegionCode());
        if (req.getTargetProvinceCode() != null) profile.setTargetProvinceCode(req.getTargetProvinceCode());
        if (req.getTargetCityCode() != null) profile.setTargetCityCode(req.getTargetCityCode());
        if (req.getExpectedSalaryMin() != null) profile.setExpectedSalaryMin(req.getExpectedSalaryMin());
        if (req.getExpectedSalaryMax() != null) profile.setExpectedSalaryMax(req.getExpectedSalaryMax());
        if (req.getTargetJobCategoryId() != null) profile.setTargetJobCategoryId(req.getTargetJobCategoryId());
        if (req.getProfileSummary() != null) profile.setProfileSummary(req.getProfileSummary());
        if (req.getSkills() != null) {
            try {
                profile.setSkills(objectMapper.writeValueAsString(
                        req.getSkills().stream().filter(StringUtils::hasText).map(String::trim).distinct().collect(Collectors.toList())
                ));
            } catch (Exception e) {
                throw BusinessException.of(400, "Invalid profile payload");
            }
        }

        profile.setUpdatedAt(LocalDateTime.now());
        profileMapper.updateById(profile);
        return R.ok("Profile updated");
    }

    @Data
    public static class SkillItem {
        private String name;
        private Integer proficiency = 3;
    }

    @Data
    public static class UpdateSkillsRequest {
        private List<SkillItem> skills = Collections.emptyList();
    }

    @Operation(summary = "Update skills")
    @PutMapping("/skills")
    public R<?> updateSkills(@RequestBody UpdateSkillsRequest req) {
        Long userId = getCurrentUserId();
        UserProfile profile = ensureProfile(userId);

        userSkillMapper.delete(new LambdaQueryWrapper<UserSkill>().eq(UserSkill::getProfileId, profile.getId()));

        int inserted = 0;
        for (SkillItem item : req.getSkills()) {
            if (!StringUtils.hasText(item.getName())) {
                continue;
            }

            Long skillId = skillMapper.findIdByName(item.getName().trim());
            if (skillId == null) {
                Skill skill = new Skill();
                skill.setSkillName(item.getName().trim());
                skill.setCategory("user_defined");
                skill.setHotScore(0);
                skillMapper.insert(skill);
                skillId = skill.getId();
            }

            UserSkill userSkill = new UserSkill();
            userSkill.setProfileId(profile.getId());
            userSkill.setSkillId(skillId);
            userSkill.setProficiency(item.getProficiency() == null ? 3 : item.getProficiency());
            userSkill.setSource("manual");
            userSkillMapper.insert(userSkill);
            inserted++;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("count", inserted);
        return R.ok(data);
    }

    private UserProfile ensureProfile(Long userId) {
        UserProfile profile = profileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId)
        );
        if (profile != null) {
            return profile;
        }

        UserProfile created = new UserProfile();
        created.setUserId(userId);
        created.setSkills("[]");
        created.setCreatedAt(LocalDateTime.now());
        created.setUpdatedAt(LocalDateTime.now());
        profileMapper.insert(created);
        return created;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
            throw BusinessException.unauthorized("Please login first");
        }
        return (Long) auth.getPrincipal();
    }
}
