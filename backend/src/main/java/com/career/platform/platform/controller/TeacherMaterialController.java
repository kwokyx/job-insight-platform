package com.career.platform.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.common.util.SecurityUtils;
import com.career.platform.platform.entity.TeacherMaterialAsset;
import com.career.platform.platform.mapper.TeacherMaterialAssetMapper;
import com.career.platform.warehouse.entity.Curriculum;
import com.career.platform.warehouse.mapper.CurriculumMapper;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Tag(name = "Teacher Materials", description = "Teacher material upload status, templates, and Excel imports")
@RestController
@RequestMapping("/api/v1/teacher/materials")
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class TeacherMaterialController {

    private static final long MAX_UPLOAD_SIZE_BYTES = 10L * 1024 * 1024;
    private static final String TYPE_CURRICULUM = "CURRICULUM";
    private static final String TYPE_SYLLABUS = "SYLLABUS";
    private static final String TYPE_STUDENT_STATUS = "STUDENT_STATUS";

    private final TeacherMaterialAssetMapper materialAssetMapper;
    private final CurriculumMapper curriculumMapper;
    private final ObjectMapper objectMapper;

    public TeacherMaterialController(TeacherMaterialAssetMapper materialAssetMapper,
                                     CurriculumMapper curriculumMapper,
                                     ObjectMapper objectMapper) {
        this.materialAssetMapper = materialAssetMapper;
        this.curriculumMapper = curriculumMapper;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "List teacher material assets")
    @GetMapping
    public R<?> listMaterials(@RequestParam(required = false) String major,
                              @RequestParam(required = false) String materialType,
                              @RequestParam(required = false) Long ownerUserId) {
        Long userId = SecurityUtils.resolveOwnedUserId(ownerUserId);
        LambdaQueryWrapper<TeacherMaterialAsset> wrapper = new LambdaQueryWrapper<TeacherMaterialAsset>()
                .eq(TeacherMaterialAsset::getUserId, userId);
        if (StringUtils.hasText(major)) {
            wrapper.like(TeacherMaterialAsset::getMajor, major.trim());
        }
        if (StringUtils.hasText(materialType)) {
            wrapper.eq(TeacherMaterialAsset::getMaterialType, normalizeMaterialType(materialType));
        }
        wrapper.orderByDesc(TeacherMaterialAsset::getUpdatedAt);

        List<TeacherMaterialAsset> assets = materialAssetMapper.selectList(wrapper);
        List<Map<String, Object>> items = new ArrayList<>();
        for (TeacherMaterialAsset asset : assets) {
            items.add(toAssetPayload(asset));
        }
        return R.ok(items);
    }

    @Operation(summary = "Get teacher material preparation status")
    @GetMapping("/status")
    public R<?> materialStatus(@RequestParam(required = false) String major,
                               @RequestParam(required = false) Long ownerUserId) {
        Long userId = SecurityUtils.resolveOwnedUserId(ownerUserId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("major", major == null ? "" : major.trim());
        payload.put("items", Arrays.asList(
                buildCurriculumStatus(userId, major),
                buildAssetStatus(userId, major, TYPE_SYLLABUS),
                buildAssetStatus(userId, major, TYPE_STUDENT_STATUS)
        ));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        boolean ready = items.stream().allMatch(item -> Boolean.TRUE.equals(item.get("uploaded")));
        payload.put("ready", ready);
        payload.put("guidance", ready
                ? Collections.singletonList("资料已齐备，可以继续查看教学诊断、治理蓝图和教改建议。")
                : Arrays.asList(
                        "请先上传课程清单 Excel、教学大纲 Excel、学生情况 Excel，再使用下方分析与教改功能。",
                        "模板已按当前平台导入字段提供，建议先下载模板填写后再上传。"
                ));
        return R.ok(payload);
    }

    @Operation(summary = "Upload teaching materials Excel")
    @PostMapping("/upload")
    public R<?> uploadMaterial(@RequestParam("file") MultipartFile file,
                               @RequestParam String materialType,
                               @RequestParam(required = false) String major) {
        String normalizedType = normalizeMaterialType(materialType);
        if (TYPE_CURRICULUM.equals(normalizedType)) {
            throw BusinessException.of(400, "课程清单请使用课程 Excel 上传入口");
        }
        validateExcelUpload(file);

        Long userId = SecurityUtils.getCurrentUserId();
        Map<String, Object> summary = parseWorkbookSummary(file, normalizedType, major);

        TeacherMaterialAsset asset = new TeacherMaterialAsset();
        asset.setUserId(userId);
        asset.setMaterialType(normalizedType);
        asset.setMaterialName(materialLabel(normalizedType));
        asset.setMajor(stringValue(summary.get("major")));
        asset.setFileName(file.getOriginalFilename());
        asset.setRowCount(parseInt(summary.get("rowCount")));
        try {
            asset.setSummaryJson(objectMapper.writeValueAsString(summary));
        } catch (Exception e) {
            asset.setSummaryJson("{}");
        }
        asset.setCreatedAt(LocalDateTime.now());
        asset.setUpdatedAt(LocalDateTime.now());
        materialAssetMapper.insert(asset);

        Map<String, Object> payload = toAssetPayload(asset);
        payload.put("previewHeaders", summary.getOrDefault("headers", Collections.emptyList()));
        return R.ok("资料上传成功", payload);
    }

    @Operation(summary = "Replace teaching material asset")
    @PutMapping("/{id}")
    public R<?> replaceMaterial(@PathVariable Long id,
                                @RequestParam("file") MultipartFile file,
                                @RequestParam(required = false) String major) {
        TeacherMaterialAsset asset = loadOwnedAsset(id);
        validateExcelUpload(file);

        Map<String, Object> summary = parseWorkbookSummary(file, asset.getMaterialType(), major);
        asset.setMajor(stringValue(summary.get("major")));
        asset.setFileName(file.getOriginalFilename());
        asset.setRowCount(parseInt(summary.get("rowCount")));
        try {
            asset.setSummaryJson(objectMapper.writeValueAsString(summary));
        } catch (Exception e) {
            asset.setSummaryJson("{}");
        }
        asset.setUpdatedAt(LocalDateTime.now());
        materialAssetMapper.updateById(asset);
        return R.ok("资料更新成功", toAssetPayload(asset));
    }

    @Operation(summary = "Delete teaching material asset")
    @DeleteMapping("/{id}")
    public R<?> deleteMaterial(@PathVariable Long id) {
        TeacherMaterialAsset asset = loadOwnedAsset(id);
        materialAssetMapper.deleteById(id);
        return R.ok("资料已删除", asset.getId());
    }

    @Operation(summary = "Download teacher material template")
    @GetMapping("/template/{materialType}")
    public ResponseEntity<ByteArrayResource> downloadTemplate(@PathVariable String materialType) {
        String normalizedType = normalizeMaterialType(materialType);
        String filename = templateFilename(normalizedType);
        String resourcePath = "templates/teacher/" + filename;
        try {
            ByteArrayResource resource = new ByteArrayResource(readClasspathTemplate(resourcePath));
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.name()).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(resource.contentLength())
                    .body(resource);
        } catch (Exception e) {
            throw BusinessException.of(500, "生成模板失败");
        }
    }

    private String templateFilename(String materialType) {
        if (TYPE_SYLLABUS.equals(materialType)) {
            return "school.xlsx";
        }
        if (TYPE_STUDENT_STATUS.equals(materialType)) {
            return "student.xlsx";
        }
        throw BusinessException.of(400, "不支持的资料模板类型");
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

    private Map<String, Object> buildCurriculumStatus(Long userId, String major) {
        LambdaQueryWrapper<Curriculum> wrapper = new LambdaQueryWrapper<Curriculum>()
                .eq(Curriculum::getIsActive, 1)
                .eq(Curriculum::getUploadedBy, userId);
        if (StringUtils.hasText(major)) {
            wrapper.like(Curriculum::getMajor, major.trim());
        }
        Long count = curriculumMapper.selectCount(wrapper);
        List<Curriculum> latest = curriculumMapper.selectList(wrapper.orderByDesc(Curriculum::getUpdatedAt).last("LIMIT 1"));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", TYPE_CURRICULUM);
        item.put("label", "课程清单 Excel");
        item.put("uploaded", count != null && count > 0);
        item.put("required", true);
        item.put("assetId", null);
        item.put("recordCount", count == null ? 0 : count);
        item.put("latestFileName", latest.isEmpty() ? "" : "课程清单已导入平台课程库");
        item.put("latestUploadedAt", latest.isEmpty() ? null : latest.get(0).getUpdatedAt());
        item.put("templatePath", "/api/v1/curriculum/template");
        item.put("summary", Collections.emptyMap());
        item.put("hint", "用于建立专业、课程与能力点之间的基础课程库。");
        return item;
    }

    private Map<String, Object> buildAssetStatus(Long userId, String major, String materialType) {
        LambdaQueryWrapper<TeacherMaterialAsset> wrapper = new LambdaQueryWrapper<TeacherMaterialAsset>()
                .eq(TeacherMaterialAsset::getUserId, userId)
                .eq(TeacherMaterialAsset::getMaterialType, materialType);
        if (StringUtils.hasText(major)) {
            wrapper.and(w -> w.eq(TeacherMaterialAsset::getMajor, major.trim())
                    .or().isNull(TeacherMaterialAsset::getMajor)
                    .or().eq(TeacherMaterialAsset::getMajor, ""));
        }
        List<TeacherMaterialAsset> assets = materialAssetMapper.selectList(
                wrapper.orderByDesc(TeacherMaterialAsset::getUpdatedAt).last("LIMIT 1"));
        TeacherMaterialAsset latest = assets.isEmpty() ? null : assets.get(0);
        if (latest == null && StringUtils.hasText(major)) {
            latest = findLatestAsset(userId, materialType);
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", materialType);
        item.put("label", materialLabel(materialType));
        item.put("uploaded", latest != null);
        item.put("required", true);
        item.put("assetId", latest == null ? null : latest.getId());
        item.put("recordCount", latest == null || latest.getRowCount() == null ? 0 : latest.getRowCount());
        item.put("latestFileName", latest == null ? "" : latest.getFileName());
        item.put("latestUploadedAt", latest == null ? null : latest.getUpdatedAt());
        item.put("templatePath", "/api/v1/teacher/materials/template/" + materialType.toLowerCase(Locale.ROOT));
        item.put("summary", latest == null ? Collections.emptyMap() : parseSummary(latest.getSummaryJson()));
        item.put("hint", TYPE_SYLLABUS.equals(materialType)
                ? "用于梳理课程目标、能力点、毕业要求和考核方式。"
                : "用于沉淀学生基础、能力短板、目标岗位和教学支持重点。");
        return item;
    }

    private TeacherMaterialAsset findLatestAsset(Long userId, String materialType) {
        List<TeacherMaterialAsset> assets = materialAssetMapper.selectList(
                new LambdaQueryWrapper<TeacherMaterialAsset>()
                        .eq(TeacherMaterialAsset::getUserId, userId)
                        .eq(TeacherMaterialAsset::getMaterialType, materialType)
                        .orderByDesc(TeacherMaterialAsset::getUpdatedAt)
                        .last("LIMIT 1")
        );
        return assets.isEmpty() ? null : assets.get(0);
    }

    private TeacherMaterialAsset loadOwnedAsset(Long id) {
        TeacherMaterialAsset asset = materialAssetMapper.selectById(id);
        if (asset == null) {
            throw BusinessException.notFound("资料不存在");
        }
        SecurityUtils.requireSelfOrAdmin(asset.getUserId());
        return asset;
    }

    private Map<String, Object> toAssetPayload(TeacherMaterialAsset asset) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assetId", asset.getId());
        payload.put("id", asset.getId());
        payload.put("materialType", asset.getMaterialType());
        payload.put("materialName", asset.getMaterialName());
        payload.put("major", asset.getMajor());
        payload.put("fileName", asset.getFileName());
        payload.put("rowCount", asset.getRowCount());
        payload.put("summary", parseSummary(asset.getSummaryJson()));
        payload.put("createdAt", asset.getCreatedAt());
        payload.put("updatedAt", asset.getUpdatedAt());
        return payload;
    }

    private Map<String, Object> parseSummary(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return Collections.emptyMap();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = objectMapper.readValue(rawJson, Map.class);
            return summary == null ? Collections.emptyMap() : summary;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> parseWorkbookSummary(MultipartFile file, String materialType, String major) {
        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw BusinessException.of(400, "Excel 文件内容为空");
            }
            DataFormatter formatter = new DataFormatter();
            List<String> headers = new ArrayList<>();
            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    String value = formatter.formatCellValue(headerRow.getCell(i));
                    if (StringUtils.hasText(value)) {
                        headers.add(value.trim());
                    }
                }
            }

            int rowCount = 0;
            List<Map<String, String>> samples = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                boolean hasContent = false;
                Map<String, String> sample = new LinkedHashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    String value = formatter.formatCellValue(row.getCell(j));
                    if (StringUtils.hasText(value)) {
                        hasContent = true;
                    }
                    if (samples.size() < 3) {
                        sample.put(headers.get(j), value);
                    }
                }
                if (hasContent) {
                    rowCount++;
                    if (samples.size() < 3) {
                        samples.add(sample);
                    }
                }
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("materialType", materialType);
            summary.put("materialLabel", materialLabel(materialType));
            summary.put("major", StringUtils.hasText(major) ? major.trim() : inferMajor(samples));
            summary.put("headers", headers);
            summary.put("rowCount", rowCount);
            summary.put("samples", samples);
            return summary;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.of(400, "Excel 解析失败，请检查模板和文件格式");
        }
    }

    private String inferMajor(List<Map<String, String>> samples) {
        for (Map<String, String> sample : samples) {
            for (Map.Entry<String, String> entry : sample.entrySet()) {
                String key = entry.getKey();
                if (key != null && key.contains("专业") && StringUtils.hasText(entry.getValue())) {
                    return entry.getValue().trim();
                }
            }
        }
        return "";
    }

    private void validateExcelUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.of(400, "请选择 Excel 文件");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!(filename.endsWith(".xlsx") || filename.endsWith(".xls"))) {
            throw BusinessException.of(400, "仅支持 .xlsx 或 .xls 文件");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE_BYTES) {
            throw BusinessException.of(400, "上传文件不能超过 10MB");
        }
    }

    private String normalizeMaterialType(String value) {
        String type = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        if (TYPE_CURRICULUM.equals(type) || TYPE_SYLLABUS.equals(type) || TYPE_STUDENT_STATUS.equals(type)) {
            return type;
        }
        throw BusinessException.of(400, "不支持的资料类型");
    }

    private String materialLabel(String type) {
        if (TYPE_CURRICULUM.equals(type)) {
            return "课程清单 Excel";
        }
        if (TYPE_SYLLABUS.equals(type)) {
            return "教学大纲 Excel";
        }
        return "学生情况 Excel";
    }

    private String[] templateHeaders(String type) {
        if (TYPE_SYLLABUS.equals(type)) {
            return new String[]{"专业", "课程名称", "课程目标", "能力点", "毕业要求", "考核方式", "实践环节", "备注"};
        }
        return new String[]{"专业", "班级", "学生人数", "能力短板", "目标岗位族", "求职阶段", "重点帮扶学生数", "备注"};
    }

    private String[][] templateRows(String type) {
        if (TYPE_SYLLABUS.equals(type)) {
            return new String[][]{
                    {"软件工程", "Web前端开发", "完成企业级前端页面开发与联调", "Vue组件化、接口联调、工程化发布",
                            "GR-1 工程实现 / GR-3 协作表达", "项目作业 + 答辩 + 周报", "企业官网改版 / 中台看板", "建议附上课程周次安排"},
                    {"数据科学与大数据技术", "Python数据分析", "完成数据清洗、分析建模与可视化表达", "Pandas、数据清洗、可视化表达",
                            "GR-2 数据分析 / GR-4 结果解释", "实验报告 + 案例复盘", "招聘数据分析项目", "建议补充成果展示要求"}
            };
        }
        return new String[][]{
                {"软件工程", "软工2201", "42", "JavaScript基础薄弱、项目表达不足", "前端开发 / 测试与质量", "准备实习", "8", "建议增加作品集辅导"},
                {"数据科学与大数据技术", "数科2302", "38", "SQL建模薄弱、BI表达不足", "数据分析 / 数据工程", "准备秋招", "6", "建议安排专项训练营"}
        };
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

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Integer parseInt(Object value) {
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
