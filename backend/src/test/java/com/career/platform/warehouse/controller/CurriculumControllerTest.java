package com.career.platform.warehouse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.warehouse.entity.Curriculum;
import com.career.platform.warehouse.mapper.CurriculumMapper;
import com.career.platform.warehouse.service.CurriculumSkillMappingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurriculumControllerTest {

    private CurriculumMapper curriculumMapper;
    private CurriculumSkillMappingService curriculumSkillMappingService;
    private CurriculumController controller;

    @BeforeEach
    void setUp() {
        curriculumMapper = mock(CurriculumMapper.class);
        curriculumSkillMappingService = mock(CurriculumSkillMappingService.class);
        controller = new CurriculumController(curriculumMapper, new ObjectMapper(), curriculumSkillMappingService);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(7L, 2));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listCurriculumReturnsPagedRecords() {
        Curriculum curriculum = ownedCurriculum();
        IPage<Curriculum> page = new Page<>(1, 20);
        page.setRecords(Collections.singletonList(curriculum));
        page.setTotal(1);
        when(curriculumMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        R<?> response = (R<?>) controller.listCurriculum("软件工程", null, null, null, 1, 20);

        assertEquals(200, response.getCode());
        assertEquals(1L, response.getTotal());
    }

    @Test
    void updateCurriculumRejectsOtherTeachersRecord() {
        Curriculum other = ownedCurriculum();
        other.setUploadedBy(9L);
        when(curriculumMapper.selectById(11L)).thenReturn(other);

        CurriculumController.CurriculumRequest request = new CurriculumController.CurriculumRequest();
        request.setCourseName("数据分析基础");
        request.setCredit(new BigDecimal("3"));

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.updateCurriculum(11L, request));

        assertEquals(403, ex.getCode());
    }

    @Test
    void deleteCurriculumRejectsOtherTeachersRecord() {
        Curriculum other = ownedCurriculum();
        other.setUploadedBy(99L);
        when(curriculumMapper.selectById(12L)).thenReturn(other);

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.deleteCurriculum(12L));

        assertEquals(403, ex.getCode());
    }

    private Curriculum ownedCurriculum() {
        Curriculum curriculum = new Curriculum();
        curriculum.setId(1L);
        curriculum.setCourseName("Web 前端开发");
        curriculum.setUploadedBy(7L);
        curriculum.setIsActive(1);
        curriculum.setMajor("软件工程");
        return curriculum;
    }
}
