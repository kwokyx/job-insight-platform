package com.career.platform.warehouse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.common.util.SecurityUtils;
import com.career.platform.warehouse.entity.Curriculum;
import com.career.platform.warehouse.mapper.CurriculumMapper;
import com.career.platform.warehouse.service.CurriculumSkillMappingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Curriculum Management", description = "Upload, query, and manage curriculum outlines")
@RestController
@RequestMapping("/api/v1/curriculum")
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class CurriculumController {

    private static final Logger log = LoggerFactory.getLogger(CurriculumController.class);
    private static final long MAX_UPLOAD_SIZE_BYTES = 10L * 1024 * 1024;

    private final CurriculumMapper curriculumMapper;
    private final ObjectMapper objectMapper;
    private final CurriculumSkillMappingService curriculumSkillMappingService;

    public CurriculumController(CurriculumMapper curriculumMapper,
                                ObjectMapper objectMapper,
                                CurriculumSkillMappingService curriculumSkillMappingService) {
        this.curriculumMapper = curriculumMapper;
        this.objectMapper = objectMapper;
        this.curriculumSkillMappingService = curriculumSkillMappingService;
    }

    public static class CurriculumRequest {
        private String courseName;
        private String courseCode;
        private String department;
        private String major;
        private BigDecimal credit;
        private String semester;
        private String description;
        private List<String> keywords;

        public String getCourseName() { return courseName; }
        public void setCourseName(String courseName) { this.courseName = courseName; }
        public String getCourseCode() { return courseCode; }
        public void setCourseCode(String courseCode) { this.courseCode = courseCode; }
        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }
        public String getMajor() { return major; }
        public void setMajor(String major) { this.major = major; }
        public BigDecimal getCredit() { return credit; }
        public void setCredit(BigDecimal credit) { this.credit = credit; }
        public String getSemester() { return semester; }
        public void setSemester(String semester) { this.semester = semester; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getKeywords() { return keywords; }
        public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    }

    @Operation(summary = "Curriculum list")
    @GetMapping
    public R<?> listCurriculum(@RequestParam(required = false) String major,
                               @RequestParam(required = false) String department,
                               @RequestParam(required = false) String keyword,
                               @RequestParam(required = false) Long ownerUserId,
                               @RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = SecurityUtils.resolveOwnedUserId(ownerUserId);
        LambdaQueryWrapper<Curriculum> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Curriculum::getIsActive, 1);
        wrapper.eq(Curriculum::getUploadedBy, userId);
        if (StringUtils.hasText(major)) {
            wrapper.like(Curriculum::getMajor, major.trim());
        }
        if (StringUtils.hasText(department)) {
            wrapper.like(Curriculum::getDepartment, department.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(Curriculum::getCourseName, value)
                    .or()
                    .like(Curriculum::getDescription, value));
        }
        wrapper.orderByDesc(Curriculum::getCreatedAt);

        IPage<Curriculum> result = curriculumMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return R.page(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Operation(summary = "下载课程导入模板")
    @GetMapping("/template")
    public ResponseEntity<ByteArrayResource> downloadTemplate() {
        String filename = "class.xlsx";
        String resourcePath = "templates/teacher/" + filename;
        try {
            ByteArrayResource resource = new ByteArrayResource(readClasspathTemplate(resourcePath));
            String encodedFilename = java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8.name()).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(resource.contentLength())
                    .body(resource);
        } catch (Exception e) {
            log.error("Failed to generate curriculum template", e);
            throw BusinessException.of(500, "生成课程导入模板失败");
        }
    }

    private byte[] readClasspathTemplate(String resourcePath) {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw BusinessException.of(500, "模板文件不存在: " + resourcePath);
            }
            byte[] buffer = new byte[4096];
            int len;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            while ((len = inputStream.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
            return output.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.of(500, "读取模板文件失败");
        }
    }

    @Log("Upload curriculum excel")
    @Operation(summary = "Upload curriculum Excel")
    @PostMapping("/upload")
    public R<?> uploadExcel(@RequestParam("file") MultipartFile file) {
        return importExcel(file, false);
    }

    @Log("Replace curriculum excel")
    @Operation(summary = "Replace curriculum Excel")
    @PostMapping("/upload/replace")
    public R<?> replaceExcel(@RequestParam("file") MultipartFile file) {
        return importExcel(file, true);
    }

    private R<?> importExcel(MultipartFile file, boolean replaceExisting) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.of(400, "请选择 Excel 文件");
        }
        validateExcelUpload(file);

        Long userId = getCurrentUserId();
        int imported = 0;
        int skipped = 0;
        int mappedSkills = 0;
        int replaced = 0;

        if (replaceExisting) {
            List<Curriculum> existing = curriculumMapper.selectList(new LambdaQueryWrapper<Curriculum>()
                    .eq(Curriculum::getUploadedBy, userId)
                    .eq(Curriculum::getIsActive, 1));
            for (Curriculum item : existing) {
                item.setIsActive(0);
                item.setUpdatedAt(LocalDateTime.now());
                curriculumMapper.updateById(item);
            }
            replaced = existing.size();
        }

        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw BusinessException.of(400, "Excel 文件内容为空");
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
                curriculum.setCourseCode(trimToNull(getCellString(row, 1)));
                curriculum.setDepartment(trimToNull(getCellString(row, 2)));
                curriculum.setMajor(trimToNull(getCellString(row, 3)));

                String creditStr = getCellString(row, 4);
                if (StringUtils.hasText(creditStr)) {
                    try {
                        curriculum.setCredit(new BigDecimal(creditStr.trim()));
                    } catch (NumberFormatException ignored) {
                        log.debug("Skip invalid credit value: {}", creditStr);
                    }
                }

                curriculum.setSemester(trimToNull(getCellString(row, 5)));
                curriculum.setDescription(trimToNull(getCellString(row, 6)));

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
            throw BusinessException.of(400, "课程 Excel 解析失败: " + e.getMessage());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("imported", imported);
        data.put("skipped", skipped);
        data.put("mappedSkills", mappedSkills);
        if (replaceExisting) {
            data.put("replaced", replaced);
            return R.ok("课程已整批替换并重新导入", data);
        }
        return R.ok("课程导入完成", data);
    }

    @Operation(summary = "Curriculum auto mapped skills")
    @GetMapping("/{id}/skills")
    public R<?> curriculumSkills(@PathVariable Long id) {
        Curriculum curriculum = loadOwnedCurriculum(id);
        List<String> keywords = parseKeywords(curriculum.getKeywords());

        Map<String, Object> data = new HashMap<>();
        data.put("curriculumId", curriculum.getId());
        data.put("courseName", curriculum.getCourseName());
        data.put("keywords", keywords);
        data.put("mappings", curriculumSkillMappingService.listMappings(curriculum.getId()));
        return R.ok(data);
    }

    @Log("Update curriculum")
    @Operation(summary = "Update curriculum")
    @PutMapping("/{id}")
    public R<?> updateCurriculum(@PathVariable Long id, @RequestBody CurriculumRequest req) {
        Curriculum curriculum = loadOwnedCurriculum(id);
        applyCurriculumRequest(curriculum, req);
        curriculum.setUpdatedAt(LocalDateTime.now());
        curriculumMapper.updateById(curriculum);
        curriculumSkillMappingService.rebuildMappings(curriculum, parseKeywords(curriculum.getKeywords()));
        return R.ok("课程更新成功", curriculum.getId());
    }

    @Log("Delete curriculum")
    @Operation(summary = "Delete curriculum")
    @DeleteMapping("/{id}")
    public R<?> deleteCurriculum(@PathVariable Long id) {
        Curriculum curriculum = loadOwnedCurriculum(id);
        curriculum.setIsActive(0);
        curriculum.setUpdatedAt(LocalDateTime.now());
        curriculumMapper.updateById(curriculum);
        return R.ok("课程已删除");
    }

    private List<String> extractKeywords(String raw) {
        List<String> keywords = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return keywords;
        }
        String[] parts = raw.split("[,，、;；]");
        for (String part : parts) {
            String value = trimToNull(part);
            if (value != null) {
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

    private Curriculum loadOwnedCurriculum(Long id) {
        Curriculum curriculum = curriculumMapper.selectById(id);
        if (curriculum == null || curriculum.getIsActive() == null || curriculum.getIsActive() != 1) {
            throw BusinessException.notFound("课程不存在");
        }
        SecurityUtils.requireSelfOrAdmin(curriculum.getUploadedBy());
        return curriculum;
    }

    private void applyCurriculumRequest(Curriculum curriculum, CurriculumRequest req) {
        if (req == null || !StringUtils.hasText(req.getCourseName())) {
            throw BusinessException.of(400, "课程名称不能为空");
        }
        curriculum.setCourseName(req.getCourseName().trim());
        curriculum.setCourseCode(trimToNull(req.getCourseCode()));
        curriculum.setDepartment(trimToNull(req.getDepartment()));
        curriculum.setMajor(trimToNull(req.getMajor()));
        curriculum.setCredit(req.getCredit());
        curriculum.setSemester(trimToNull(req.getSemester()));
        curriculum.setDescription(trimToNull(req.getDescription()));
        curriculum.setKeywords(writeKeywords(req.getKeywords()));
    }

    private String writeKeywords(List<String> keywords) {
        List<String> normalized = new ArrayList<>();
        if (keywords != null) {
            for (String keyword : keywords) {
                String value = trimToNull(keyword);
                if (value != null) {
                    normalized.add(value);
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw BusinessException.of(400, "技能关键词格式不正确");
        }
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        return new DataFormatter().formatCellValue(cell);
    }

    private void validateExcelUpload(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!(filename.endsWith(".xlsx") || filename.endsWith(".xls"))) {
            throw BusinessException.of(400, "仅支持 .xlsx 或 .xls 课程文件");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE_BYTES) {
            throw BusinessException.of(400, "课程文件大小不能超过 10MB");
        }
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
            throw BusinessException.unauthorized("登录状态已失效，请重新登录");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId != null) {
            return userId;
        }
        throw BusinessException.unauthorized("登录状态已失效，请重新登录");
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor((short) 22);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor((short) 9);
        style.setFont(font);
        return style;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
