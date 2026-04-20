package com.career.platform.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.career.platform.profile.entity.Skill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SkillMapper extends BaseMapper<Skill> {

    @Select("SELECT id FROM job_label_dict WHERE label_name = #{skillName} LIMIT 1")
    Long findIdByName(@Param("skillName") String skillName);
}
