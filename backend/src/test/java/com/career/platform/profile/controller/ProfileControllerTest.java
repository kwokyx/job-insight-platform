package com.career.platform.profile.controller;

import com.career.platform.profile.entity.Skill;
import com.career.platform.profile.entity.UserProfile;
import com.career.platform.profile.entity.UserSkill;
import com.career.platform.profile.mapper.SkillMapper;
import com.career.platform.profile.mapper.UserProfileMapper;
import com.career.platform.profile.mapper.UserSkillMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileControllerTest {

    private MockMvc mockMvc;
    private UserProfileMapper profileMapper;
    private UserSkillMapper userSkillMapper;
    private SkillMapper skillMapper;

    @BeforeEach
    void setUp() {
        profileMapper = mock(UserProfileMapper.class);
        userSkillMapper = mock(UserSkillMapper.class);
        skillMapper = mock(SkillMapper.class);

        ProfileController controller = new ProfileController(
                profileMapper,
                userSkillMapper,
                skillMapper,
                new ObjectMapper()
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(9L, 0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateSkillsCreatesMissingSkillAndBindsProfileSkill() throws Exception {
        UserProfile profile = new UserProfile();
        profile.setId(15L);
        profile.setUserId(9L);

        when(profileMapper.selectOne(any())).thenReturn(profile);
        when(skillMapper.findIdByName("Python")).thenReturn(null);

        doAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            skill.setId(42L);
            return 1;
        }).when(skillMapper).insert(any(Skill.class));

        String payload = "{"
                + "\"skills\":["
                + "{\"name\":\"Python\",\"proficiency\":5},"
                + "{\"name\":\" \",\"proficiency\":1}"
                + "]"
                + "}";

        mockMvc.perform(put("/api/v1/profile/skills")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.count").value(1));

        verify(userSkillMapper).delete(any());
        verify(skillMapper).insert(any(Skill.class));
        verify(userSkillMapper).insert(any(UserSkill.class));
    }
}
