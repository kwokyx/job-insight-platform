package com.career.platform.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.career.platform.profile.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
