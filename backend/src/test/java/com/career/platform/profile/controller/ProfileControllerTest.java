package com.career.platform.profile.controller;

import com.career.platform.profile.entity.UserProfile;
import com.career.platform.profile.mapper.UserProfileMapper;
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

    @BeforeEach
    void setUp() {
        profileMapper = mock(UserProfileMapper.class);

        ProfileController controller = new ProfileController(
                profileMapper,
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

        verify(profileMapper).updateById(any(UserProfile.class));
    }
}
