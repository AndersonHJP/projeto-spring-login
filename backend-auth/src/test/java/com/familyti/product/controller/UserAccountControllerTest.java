package com.familyti.product.controller;

import com.familyti.product.config.JwtAuthFilter;
import com.familyti.product.dto.UserAccountResponse;
import com.familyti.product.exception.EmailAlreadyExistsException;
import com.familyti.product.model.UserAccount;
import com.familyti.product.service.UserAccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserAccountController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("UserAccountController")
class UserAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAccountService service;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/users/register should return 201 with the created user")
    void shouldRegisterUser() throws Exception {
        when(service.register(any()))
                .thenReturn(new UserAccountResponse(1L, "Anderson", "anderson@example.com"));

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Anderson",
                                  "email": "anderson@example.com",
                                  "password": "12345678"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Anderson"))
                .andExpect(jsonPath("$.email").value("anderson@example.com"));
    }

    @Test
    @DisplayName("POST /api/users/register should return 409 when the email is already registered")
    void shouldReturnConflictWhenEmailAlreadyExists() throws Exception {
        when(service.register(any()))
                .thenThrow(new EmailAlreadyExistsException("Email already exists"));

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Anderson",
                                  "email": "anderson@example.com",
                                  "password": "12345678"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Email already exists"));
    }

    @Test
    @DisplayName("GET /api/users/me should return 200 with the authenticated user data")
    void shouldReturnAuthenticatedUser() throws Exception {
        authenticate(authenticatedUser());

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Anderson"))
                .andExpect(jsonPath("$.email").value("anderson@example.com"));
    }

    @Test
    @DisplayName("GET /api/users/me should never expose the password")
    void shouldNotExposePassword() throws Exception {
        authenticate(authenticatedUser());

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    private void authenticate(UserAccount user) {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private UserAccount authenticatedUser() {
        UserAccount user = new UserAccount();
        user.setName("Anderson");
        user.setEmail("anderson@example.com");
        user.setPassword("encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}