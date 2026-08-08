package com.example.demo.support;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class JwtTestSupport {

    private JwtTestSupport() {
    }

    public static String loginAndGetToken(MockMvc mockMvc, String loginId, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"%s"}
                                """.formatted(loginId, password)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        int tokenKey = body.indexOf("\"token\":\"");
        if (tokenKey < 0) {
            throw new IllegalStateException("Login response does not contain token: " + body);
        }
        int start = tokenKey + "\"token\":\"".length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }
}
