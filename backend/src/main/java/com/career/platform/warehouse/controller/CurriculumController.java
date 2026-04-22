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
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @Operation(summary = "下载课程导入模板")
    @GetMapping("/template")
    public ResponseEntity<ByteArrayResource> downloadTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("课程导入模板");
            CellStyle headerStyle = createHeaderStyle(workbook);

            String[] headers = {
                    "课程名称", "课程代码", "院系", "专业", "学分", "开课学期", "课程描述", "技能关键词"
            };
            String[][] rows = {
                    {
                            "Python数据分析",
                            "DS101",
                            "信息工程学院",
                            "数据科学与大数据技术",
                            "3",
                            "2026春",
                            "围绕数据处理、分析建模与可视化输出设计课程任务",
                            "Python，Pandas，数据清洗，可视化"
                    },
                    {
                            "Web前端开发",
                            "SE204",
                            "软件学院",
                            "软件工程",
                            "4",
                            "2026秋",
                            "结合企业项目案例完成页面开发、联调与部署实践",
                            "HTML，CSS，JavaScript，Vue"
                    },
                    {
                            "数据库应用",
                            "CS202",
                            "计算机学院",
                            "计算机科学与技术",
                            "3.5",
                            "2026春",
                            "覆盖数据库设计、查询优化和项目场景中的数据管理",
                            "MySQL，SQL优化，数据建模"
                    }
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                for (int colIndex = 0; colIndex < rows[rowIndex].length; colIndex++) {
                    row.createCell(colIndex).setCellValue(rows[rowIndex][colIndex]);
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 1024, 40 * 256));
            }

            workbook.write(output);
            ByteArrayResource resource = new ByteArrayResource(output.toByteArray());
            String filename = java.net.URLEncoder.encode("课程导入模板.xlsx", StandardCharsets.UTF_8.name()).replace("+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(resource.contentLength())
                    .body(resource);
        } catch (Exception e) {
            log.error("Failed to generate curriculum template", e);
            throw BusinessException.of(500, "生成课程导入模板失败");
        }
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
            throw BusinessException.unauthorized("登录状态已失效，请重新登录");
        }
        return (Long) auth.getPrincipal();
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
}
