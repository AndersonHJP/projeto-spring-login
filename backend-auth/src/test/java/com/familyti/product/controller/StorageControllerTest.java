package com.familyti.product.controller;

import com.familyti.product.config.JwtAuthFilter;
import com.familyti.product.storage.StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StorageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(StorageControllerTest.ActiveProvider.class)
@DisplayName("StorageController")
class StorageControllerTest {

    static class ActiveProvider {

        @Bean
        StorageProperties storageProperties() {
            return new StorageProperties("  MinIO  ", List.of("MinIO", " S3 "), Map.of());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    @DisplayName("GET /api/storage should return the write provider and the enabled ones, normalized")
    void shouldReturnStorageState() throws Exception {
        mockMvc.perform(get("/api/storage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("minio"))
                .andExpect(jsonPath("$.enabled").isArray())
                .andExpect(jsonPath("$.enabled[0]").value("minio"))
                .andExpect(jsonPath("$.enabled[1]").value("s3"));
    }
}
