package com.career.platform.warehouse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.warehouse.entity.Curriculum;
import com.career.platform.warehouse.mapper.CurriculumMapper;
import com.career.platform.warehouse.service.CurriculumSkillMappingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Curriculum Management", description = "Upload, query, and manage curriculum outlines")
@RestController
@RequestMapping("/api/v1/curriculum")
public class CurriculumController {

    private static final Logger log = LoggerFactory.getLogger(CurriculumController.class);
    private static final long MAX_UPLOAD_SIZE_BYTES = 10L * 1024 * 1024;

    private final CurriculumMapper curriculumMapper;
    private final ObjectMapper objectMapper;
    private final CurriculumSkillMappingService curriculumSkillMappingService;

    public CurriculumController(CurriculumMapper curriculumMapper, ObjectMapper objectMapper,
                                CurriculumSkillMappingService curriculumSkillMappingService) {
        this.curriculumMapper = curriculumMapper;
        this.objectMapper = objectMapper;
        this.curriculumSkillMappingService = curriculumSkillMappingService;
    }

    @Operation(summary = "Curriculum list")
    @GetMapping
    public R<?> listCurriculum(
            @RequestParam(required = false) String major,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        LambdaQueryWrapper<Curriculum> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Curriculum::getIsActive, 1);
        if (StringUtils.hasText(major)) {
            wrapper.like(Curriculum::getMajor, major);
        }
        if (StringUtils.hasText(department)) {
            wrapper.like(Curriculum::getDepartment, department);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Curriculum::getCourseName, keyword).or().like(Curriculum::getDescription, keyword));
        }
        wrapper.orderByDesc(Curriculum::getCreatedAt);

        IPage<Curriculum> result = curriculumMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return R.page(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Log("Upload curriculum excel")
    @Operation(summary = "Upload curriculum Excel")
    @PostMapping("/upload")
    public R<?> uploadExcel(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.of(400, "Please choose an Excel file");
        }
        validateExcelUpload(file);

        Long userId = getCurrentUserId();
        int imported = 0;
        int skipped = 0;
        int mappedSkills = 0;

        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw BusinessException.of(400, "Excel file is empty");
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                String courseName = getCellString(row, 0);
                if (!StringUtils.hasText(courseName)) {
                    skipped++;
                    continue;
                }

                Curriculum curriculum = new Curriculum();
                curriculum.setCourseName(courseName.trim());
                curriculum.setCourseCode(getCellString(row, 1));
                curriculum.setDepartment(getCellString(row, 2));
                curriculum.setMajor(getCellString(row, 3));

                String creditStr = getCellString(row, 4);
                if (StringUtils.hasText(creditStr)) {
                    try {
                        curriculum.setCredit(new BigDecimal(creditStr.trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }

                curriculum.setSemester(getCellString(row, 5));
                curriculum.setDescription(getCellString(row, 6));

                List<String> keywords = extractKeywords(getCellString(row, 7));
                curriculum.setKeywords(objectMapper.writeValueAsString(keywords));
                curriculum.setIsActive(1);
                curriculum.setUploadedBy(userId);
                curriculum.setCreatedAt(LocalDateTime.now());
                curriculum.setUpdatedAt(LocalDateTime.now());
                curriculumMapper.insert(curriculum);

                mappedSkills += curriculumSkillMappingService.rebuildMappings(curriculum, keywords).size();
                imported++;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Curriculum Excel import failed", e);
            throw BusinessException.of(400, "Excel parse failed: " + e.getMessage());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("imported", imported);
        data.put("skipped", skipped);
        data.put("mappedSkills", mappedSkills);
        return R.ok("Curriculum import completed", data);
    }

    @Operation(summary = "Curriculum auto mapped skills")
    @GetMapping("/{id}/skills")
    public R<?> curriculumSkills(@PathVariable Long id) {
        Curriculum curriculum = curriculumMapper.selectById(id);
        if (curriculum == null || curriculum.getIsActive() == null || curriculum.getIsActive() != 1) {
            throw BusinessException.notFound("Curriculum not found");
        }

        List<String> keywords = parseKeywords(curriculum.getKeywords());
        Map<String, Object> data = new HashMap<>();
        data.put("curriculumId", curriculum.getId());
        data.put("courseName", curriculum.getCourseName());
        data.put("keywords", keywords);
        data.put("mappings", curriculumSkillMappingService.listMappings(curriculum.getId()));
        return R.ok(data);
    }

    @Log("Delete curriculum")
    @Operation(summary = "Delete curriculum")
    @DeleteMapping("/{id}")
    public R<?> deleteCurriculum(@PathVariable Long id) {
        Curriculum curriculum = curriculumMapper.selectById(id);
        if (curriculum == null) {
            throw BusinessException.notFound("Curriculum not found");
        }
        curriculum.setIsActive(0);
        curriculum.setUpdatedAt(LocalDateTime.now());
        curriculumMapper.updateById(curriculum);
        return R.ok("Curriculum deleted");
    }

    private List<String> extractKeywords(String raw) {
        List<String> keywords = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return keywords;
        }
        String[] parts = raw.split("[,，、;；]");
        for (String part : parts) {
            String value = part == null ? null : part.trim();
            if (StringUtils.hasText(value)) {
                keywords.add(value);
            }
        }
        return keywords;
    }

    private List<String> parseKeywords(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        org.apache.poi.ss.usermodel.DataFormatter formatter = new org.apache.poi.ss.usermodel.DataFormatter();
        return formatter.formatCellValue(cell);
    }

    private void validateExcelUpload(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!(filename.endsWith(".xlsx") || filename.endsWith(".xls"))) {
            throw BusinessException.of(400, "Only .xlsx or .xls curriculum files are supported");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE_BYTES) {
            throw BusinessException.of(400, "Curriculum upload exceeds 10MB limit");
        }
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
            throw BusinessException.unauthorized("Please login first");
        }
        return (Long) auth.getPrincipal();
    }
}
