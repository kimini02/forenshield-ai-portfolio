package com.example.demo.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class EvidenceApiTestSupport {

    private EvidenceApiTestSupport() {
    }

    public static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    public static String uploadVideo(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            String accessToken,
            MockMultipartFile file,
            String caseName
    ) throws Exception {
        return mockMvc.perform(multipart("/api/v1/evidences/upload")
                        .file(file)
                        .param("caseName", caseName)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    public static long extractEvidenceId(ObjectMapper objectMapper, String responseBody) throws Exception {
        return objectMapper.readTree(responseBody).get("evidenceId").asLong();
    }

    public static String extractHashValue(String responseBody) {
        int index = responseBody.indexOf("\"hashValue\":\"");
        if (index < 0) {
            throw new IllegalStateException("hashValue not found in response: " + responseBody);
        }
        int start = index + "\"hashValue\":\"".length();
        int end = responseBody.indexOf('"', start);
        return responseBody.substring(start, end);
    }

    public static String extractOriginalSha256(String responseBody) {
        int index = responseBody.indexOf("\"originalSha256\":\"");
        if (index < 0) {
            throw new IllegalStateException("originalSha256 not found in response: " + responseBody);
        }
        int start = index + "\"originalSha256\":\"".length();
        int end = responseBody.indexOf('"', start);
        return responseBody.substring(start, end);
    }

    public static void assertUploadHashFieldsMatch(ObjectMapper objectMapper, String responseBody) throws Exception {
        JsonNode response = objectMapper.readTree(responseBody);
        assertThat(response.hasNonNull("hashValue")).isTrue();
        assertThat(response.hasNonNull("originalSha256")).isTrue();
        String hashValue = response.get("hashValue").asText();
        String originalSha256 = response.get("originalSha256").asText();
        assertThat(originalSha256).isEqualTo(hashValue);
        assertThat(originalSha256).matches("[0-9a-f]{64}");
    }

    public static void startAnalysis(
            MockMvc mockMvc,
            String accessToken,
            String caseName,
            long... evidenceIds
    ) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < evidenceIds.length; i++) {
            if (i > 0) {
                ids.append(", ");
            }
            ids.append(evidenceIds[i]);
        }

        mockMvc.perform(post("/api/v1/evidences/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseName": "%s",
                                  "evidenceIds": [%s],
                                  "acknowledgeQualityWarning": true
                                }
                                """.formatted(caseName, ids)))
                .andExpect(status().isOk());
    }

    public static long uploadAndStartAnalysis(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            String accessToken,
            String fileName,
            String caseName
    ) throws Exception {
        String uploadResponseBody = uploadVideo(
                mockMvc,
                objectMapper,
                accessToken,
                EvidenceTestFixtures.videoMp4(fileName, "bytes-" + fileName),
                caseName
        );
        long evidenceId = extractEvidenceId(objectMapper, uploadResponseBody);
        startAnalysis(mockMvc, accessToken, caseName, evidenceId);
        return evidenceId;
    }

    public static void seedS3Object(S3Client s3Client, String bucket, String objectKey, byte[] content) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .build(),
                RequestBody.fromBytes(content)
        );
    }
}
