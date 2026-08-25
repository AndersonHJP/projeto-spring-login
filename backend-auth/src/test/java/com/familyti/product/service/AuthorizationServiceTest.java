package com.familyti.product.service;

import com.familyti.product.config.TokenService;
import com.familyti.product.dto.AuthAccountResponse;
import com.familyti.product.exception.InvalidCredentialsException;
import com.familyti.product.model.UserAccount;
import com.familyti.product.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthorizationService.login()")
class AuthorizationServiceTest {

    private static final String EMAIL = "anderson@test.com";
    private static final String RAW_PASSWORD = "12345678";
    private static final String ENCODED_PASSWORD = "encoded-password";
    private static final String ACCESS_TOKEN = "jwt-token-123";
    private static final long EXPIRATION = 3_600_000L;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private AuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authorizationService, "expiration", EXPIRATION);
    }

    @Test
    @DisplayName("should return the access token when the credentials are valid")
    void shouldLoginSuccessfully() {
        UserAccount user = existingUser();

        when(userAccountRepository.findByEmail(EMAIL)).thenReturn(user);
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        when(tokenService.generateToken(user)).thenReturn(ACCESS_TOKEN);

        AuthAccountResponse response = authorizationService.login(EMAIL, RAW_PASSWORD);

        assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
    }

    @Test
    @DisplayName("should throw InvalidCredentialsException when the email is not registered")
    void shouldThrowWhenUserNotFound() {
        when(userAccountRepository.findByEmail(EMAIL)).thenReturn(null);

        assertThatThrownBy(() -> authorizationService.login(EMAIL, RAW_PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    @DisplayName("should throw InvalidCredentialsException when the password does not match")
    void shouldThrowWhenPasswordDoesNotMatch() {
        when(userAccountRepository.findByEmail(EMAIL)).thenReturn(existingUser());
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

        assertThatThrownBy(() -> authorizationService.login(EMAIL, RAW_PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    private UserAccount existingUser() {
        UserAccount user = new UserAccount();
        user.setEmail(EMAIL);
        user.setPassword(ENCODED_PASSWORD);
        return user;
    }
}