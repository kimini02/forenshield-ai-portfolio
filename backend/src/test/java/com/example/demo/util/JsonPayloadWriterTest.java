package com.example.demo.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonPayloadWriterTest {

    @Test
    void toJson_serializesMap() {
        JsonPayloadWriter writer = new JsonPayloadWriter(new ObjectMapper());

        String json = writer.toJson(java.util.Map.of("step", "UPLOADED", "evidenceId", 1L));

        assertThat(json).contains("\"step\":\"UPLOADED\"");
        assertThat(json).contains("\"evidenceId\":1");
    }
}
