package com.career.platform.platform.controller;

import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.platform.entity.TeacherMaterialAsset;
import com.career.platform.platform.mapper.TeacherMaterialAssetMapper;
import com.career.platform.warehouse.mapper.CurriculumMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeacherMaterialControllerTest {

    private TeacherMaterialAssetMapper assetMapper;
    private TeacherMaterialController controller;

    @BeforeEach
    void setUp() {
        assetMapper = mock(TeacherMaterialAssetMapper.class);
        controller = new TeacherMaterialController(assetMapper, mock(CurriculumMapper.class), new ObjectMapper());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(7L, 2));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void replaceMaterialRejectsOtherTeachersAsset() {
        TeacherMaterialAsset asset = ownedAsset();
        asset.setUserId(19L);
        when(assetMapper.selectById(5L)).thenReturn(asset);

        MockMultipartFile file = excelFile("syllabus.xlsx");

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.replaceMaterial(5L, file, "软件工程"));

        assertEquals(403, ex.getCode());
        verify(assetMapper, never()).updateById(any());
    }

    @Test
    void deleteMaterialRejectsOtherTeachersAsset() {
        TeacherMaterialAsset asset = ownedAsset();
        asset.setUserId(21L);
        when(assetMapper.selectById(6L)).thenReturn(asset);

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.deleteMaterial(6L));

        assertEquals(403, ex.getCode());
        verify(assetMapper, never()).deleteById(6L);
    }

    @Test
    void replaceMaterialUpdatesCurrentTeachersAsset() {
        TeacherMaterialAsset asset = ownedAsset();
        when(assetMapper.selectById(8L)).thenReturn(asset);
        doAnswer(invocation -> {
            TeacherMaterialAsset updated = invocation.getArgument(0);
            updated.setUpdatedAt(LocalDateTime.now());
            return 1;
        }).when(assetMapper).updateById(any(TeacherMaterialAsset.class));

        R<?> response = (R<?>) controller.replaceMaterial(8L, excelFile("student.xlsx"), "数据科学与大数据技术");

        assertEquals(200, response.getCode());
        verify(assetMapper).updateById(any(TeacherMaterialAsset.class));
    }

    private TeacherMaterialAsset ownedAsset() {
        TeacherMaterialAsset asset = new TeacherMaterialAsset();
        asset.setId(8L);
        asset.setUserId(7L);
        asset.setMaterialType("SYLLABUS");
        asset.setMaterialName("教学大纲 Excel");
        asset.setFileName("old.xlsx");
        asset.setSummaryJson("{}");
        asset.setRowCount(4);
        asset.setCreatedAt(LocalDateTime.now());
        asset.setUpdatedAt(LocalDateTime.now());
        return asset;
    }

    private MockMultipartFile excelFile(String fileName) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Sheet1");
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("专业");
            header.createCell(1).setCellValue("课程名称");
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("软件工程");
            row.createCell(1).setCellValue("Web前端开发");
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    fileName,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
