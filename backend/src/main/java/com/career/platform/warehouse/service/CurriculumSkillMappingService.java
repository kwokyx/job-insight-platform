package com.career.platform.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.profile.entity.Skill;
import com.career.platform.profile.mapper.SkillMapper;
import com.career.platform.warehouse.entity.Curriculum;
import com.career.platform.warehouse.entity.CurriculumSkillMapping;
import com.career.platform.warehouse.mapper.CurriculumSkillMappingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CurriculumSkillMappingService {

    private static final Logger log = LoggerFactory.getLogger(CurriculumSkillMappingService.class);

    private final CurriculumSkillMappingMapper curriculumSkillMappingMapper;
    private final SkillMapper skillMapper;

    public CurriculumSkillMappingService(CurriculumSkillMappingMapper curriculumSkillMappingMapper, SkillMapper skillMapper) {
        this.curriculumSkillMappingMapper = curriculumSkillMappingMapper;
        this.skillMapper = skillMapper;
    }

    @Transactional
    public List<Map<String, Object>> rebuildMappings(Curriculum curriculum, List<String> keywords) {
        curriculumSkillMappingMapper.delete(
                new LambdaQueryWrapper<CurriculumSkillMapping>()
                        .eq(CurriculumSkillMapping::getCurriculumId, curriculum.getId())
        );

        if (keywords == null || keywords.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> mappings = new ArrayList<>();
        int index = 0;
        for (String rawKeyword : keywords) {
            String keyword = rawKeyword == null ? null : rawKeyword.trim();
            if (!StringUtils.hasText(keyword)) {
                continue;
            }

            Long skillId = skillMapper.findIdByName(keyword);
            if (skillId == null) {
                Skill skill = new Skill();
                skill.setSkillName(keyword);
                skill.setCategory("other");
                skill.setHotScore(0);
                skill.setStatus(1);
                skill.setCreatedAt(LocalDateTime.now());
                skill.setUpdatedAt(LocalDateTime.now());
                skillMapper.insert(skill);
                skillId = skill.getId();
            }

            CurriculumSkillMapping mapping = new CurriculumSkillMapping();
            mapping.setCurriculumId(curriculum.getId());
            mapping.setSkillId(skillId);
            mapping.setRelevance(index == 0 ? new BigDecimal("1.00") : new BigDecimal("0.85"));
            mapping.setSource("AUTO");
            curriculumSkillMappingMapper.insert(mapping);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("skillId", skillId);
            item.put("skillName", keyword);
            item.put("relevance", mapping.getRelevance());
            mappings.add(item);
            index++;
        }

        log.info("Rebuilt {} curriculum-skill mappings for curriculum {}", mappings.size(), curriculum.getId());
        return mappings;
    }

    public List<Map<String, Object>> listMappings(Long curriculumId) {
        List<CurriculumSkillMapping> mappings = curriculumSkillMappingMapper.selectList(
                new LambdaQueryWrapper<CurriculumSkillMapping>()
                        .eq(CurriculumSkillMapping::getCurriculumId, curriculumId)
        );
        if (mappings.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, String> skillNameMap = skillMapper.selectBatchIds(
                mappings.stream().map(CurriculumSkillMapping::getSkillId).collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(Skill::getId, Skill::getSkillName, (a, b) -> a, LinkedHashMap::new));

        List<Map<String, Object>> result = new ArrayList<>();
        for (CurriculumSkillMapping mapping : mappings) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("skillId", mapping.getSkillId());
            item.put("skillName", skillNameMap.get(mapping.getSkillId()));
            item.put("relevance", mapping.getRelevance());
            item.put("source", mapping.getSource());
            result.add(item);
        }
        return result;
    }
}
