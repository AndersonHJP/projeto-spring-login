package com.familyti.product.controller;

import com.familyti.product.config.JwtAuthFilter;
import com.familyti.product.dto.AuthAccountResponse;
import com.familyti.product.exception.InvalidCredentialsException;
import com.familyti.product.service.AuthorizationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthenticationController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthenticationController")
class AuthenticationControllerTest {

    private static final String EMAIL = "anderson@example.com";
    private static final String PASSWORD = "12345678";
    private static final String ACCESS_TOKEN = "jwt-token-123";
    private static final long EXPIRATION = 86_400_000L;

    private static final String LOGIN_BODY = """
            {
              "email": "anderson@example.com",
              "password": "12345678"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthorizationService service;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    @DisplayName("POST /api/auth/login should return 200 with the access token")
    void shouldReturnAccessTokenWhenCredentialsAreValid() throws Exception {
        when(service.login(EMAIL, PASSWORD))
                .thenReturn(AuthAccountResponse.of(ACCESS_TOKEN, EXPIRATION));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN));
    }

    @Test
    @DisplayName("POST /api/auth/login should return 401 when the credentials are invalid")
    void shouldReturnUnauthorizedWhenCredentialsAreInvalid() throws Exception {
        when(service.login(EMAIL, PASSWORD))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /api/auth/login should never echo the password back")
    void shouldNotEchoPassword() throws Exception {
        when(service.login(EMAIL, PASSWORD))
                .thenReturn(AuthAccountResponse.of(ACCESS_TOKEN, EXPIRATION));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());
    }
}