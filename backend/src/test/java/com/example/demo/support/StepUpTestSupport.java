package com.example.demo.support;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class StepUpTestSupport {

    public static final String STEP_UP_HEADER = "X-Step-Up-Token";

    private StepUpTestSupport() {
    }

    public static String issueStepUpToken(MockMvc mockMvc, String accessToken, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/step-up/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"%s"}
                                """.formatted(password)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        int tokenKey = body.indexOf("\"stepUpToken\":\"");
        if (tokenKey < 0) {
            throw new IllegalStateException("Step-up response does not contain stepUpToken: " + body);
        }
        int start = tokenKey + "\"stepUpToken\":\"".length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }
}
