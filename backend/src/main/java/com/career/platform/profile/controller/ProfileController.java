package com.career.platform.profile.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.profile.entity.UserProfile;
import com.career.platform.profile.mapper.UserProfileMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.career.platform.common.util.SecurityUtils;
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
public class ProfileController {

    private final UserProfileMapper profileMapper;
    private final ObjectMapper objectMapper;

    public ProfileController(UserProfileMapper profileMapper, ObjectMapper objectMapper) {
        this.profileMapper = profileMapper;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Get profile")
    @GetMapping
    public R<?> getProfile() {
        Long userId = SecurityUtils.getCurrentUserId();
        UserProfile profile = ensureProfile(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("profile", profile);
        result.put("skills", parseSkills(profile.getSkills()));
        return R.ok(result);
    }

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
        private String targetJob;
        private String currentJob;
        private String targetCityName;
        private String industry;
        private Integer experienceYears;
        private String resumeText;
        private String resumeFileName;

        public Long getMajorId() { return majorId; }
        public void setMajorId(Long majorId) { this.majorId = majorId; }
        public String getEducationLevel() { return educationLevel; }
        public void setEducationLevel(String educationLevel) { this.educationLevel = educationLevel; }
        public String getTargetRegionCode() { return targetRegionCode; }
        public void setTargetRegionCode(String targetRegionCode) { this.targetRegionCode = targetRegionCode; }
        public String getTargetProvinceCode() { return targetProvinceCode; }
        public void setTargetProvinceCode(String targetProvinceCode) { this.targetProvinceCode = targetProvinceCode; }
        public String getTargetCityCode() { return targetCityCode; }
        public void setTargetCityCode(String targetCityCode) { this.targetCityCode = targetCityCode; }
        public Integer getExpectedSalaryMin() { return expectedSalaryMin; }
        public void setExpectedSalaryMin(Integer expectedSalaryMin) { this.expectedSalaryMin = expectedSalaryMin; }
        public Integer getExpectedSalaryMax() { return expectedSalaryMax; }
        public void setExpectedSalaryMax(Integer expectedSalaryMax) { this.expectedSalaryMax = expectedSalaryMax; }
        public Long getTargetJobCategoryId() { return targetJobCategoryId; }
        public void setTargetJobCategoryId(Long targetJobCategoryId) { this.targetJobCategoryId = targetJobCategoryId; }
        public List<String> getSkills() { return skills; }
        public void setSkills(List<String> skills) { this.skills = skills; }
        public String getProfileSummary() { return profileSummary; }
        public void setProfileSummary(String profileSummary) { this.profileSummary = profileSummary; }
        public String getTargetJob() { return targetJob; }
        public void setTargetJob(String targetJob) { this.targetJob = targetJob; }
        public String getCurrentJob() { return currentJob; }
        public void setCurrentJob(String currentJob) { this.currentJob = currentJob; }
        public String getTargetCityName() { return targetCityName; }
        public void setTargetCityName(String targetCityName) { this.targetCityName = targetCityName; }
        public String getIndustry() { return industry; }
        public void setIndustry(String industry) { this.industry = industry; }
        public Integer getExperienceYears() { return experienceYears; }
        public void setExperienceYears(Integer experienceYears) { this.experienceYears = experienceYears; }
        public String getResumeText() { return resumeText; }
        public void setResumeText(String resumeText) { this.resumeText = resumeText; }
        public String getResumeFileName() { return resumeFileName; }
        public void setResumeFileName(String resumeFileName) { this.resumeFileName = resumeFileName; }
    }

    @Operation(summary = "Update profile")
    @PutMapping
    public R<?> updateProfile(@RequestBody UpdateProfileRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
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
        if (req.getTargetJob() != null) profile.setTargetJob(req.getTargetJob());
        if (req.getCurrentJob() != null) profile.setCurrentJob(req.getCurrentJob());
        if (req.getTargetCityName() != null) profile.setTargetCityName(req.getTargetCityName());
        if (req.getIndustry() != null) profile.setIndustry(req.getIndustry());
        if (req.getExperienceYears() != null) profile.setExperienceYears(req.getExperienceYears());
        if (req.getResumeText() != null) profile.setResumeText(req.getResumeText());
        if (req.getResumeFileName() != null) profile.setResumeFileName(req.getResumeFileName());
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

    public static class SkillItem {
        private String name;
        private Integer proficiency = 3;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getProficiency() { return proficiency; }
        public void setProficiency(Integer proficiency) { this.proficiency = proficiency; }
    }

    public static class UpdateSkillsRequest {
        private List<SkillItem> skills = Collections.emptyList();

        public List<SkillItem> getSkills() { return skills; }
        public void setSkills(List<SkillItem> skills) { this.skills = skills; }
    }

    @Operation(summary = "Update skills")
    @PutMapping("/skills")
    public R<?> updateSkills(@RequestBody UpdateSkillsRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        UserProfile profile = ensureProfile(userId);
        List<String> normalizedSkills = req.getSkills().stream()
                .map(SkillItem::getName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        try {
            profile.setSkills(objectMapper.writeValueAsString(normalizedSkills));
        } catch (Exception e) {
            throw BusinessException.of(400, "Invalid skills payload");
        }
        profile.setUpdatedAt(LocalDateTime.now());
        profileMapper.updateById(profile);

        Map<String, Object> data = new HashMap<>();
        data.put("count", normalizedSkills.size());
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



    private List<String> parseSkills(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }
}
