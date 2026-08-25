package com.familyti.product.service;

import com.familyti.product.dto.RegisterAccountRequest;
import com.familyti.product.dto.UserAccountResponse;
import com.familyti.product.exception.EmailAlreadyExistsException;
import com.familyti.product.model.UserAccount;
import com.familyti.product.repository.UserAccountRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserAccountService.register()")
class UserAccountServiceTest {

    private static final String NAME = "Anderson";
    private static final String EMAIL = "anderson@test.com";
    private static final String RAW_PASSWORD = "12345678";
    private static final String ENCODED_PASSWORD = "encoded-password";

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserAccountService userAccountService;

    @Test
    @DisplayName("should register the user and return its data when the email is available")
    void shouldRegisterUserSuccessfully() {
        RegisterAccountRequest dto = new RegisterAccountRequest(NAME, RAW_PASSWORD, EMAIL);

        when(userAccountRepository.findByEmail(dto.email())).thenReturn(null);
        when(passwordEncoder.encode(dto.password())).thenReturn(ENCODED_PASSWORD);
        when(userAccountRepository.save(any(UserAccount.class))).thenReturn(persistedUser());

        UserAccountResponse response = userAccountService.register(dto);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo(NAME);
        assertThat(response.email()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("should throw EmailAlreadyExistsException when the email is already registered")
    void shouldThrowWhenEmailAlreadyExists() {
        RegisterAccountRequest dto = new RegisterAccountRequest(NAME, RAW_PASSWORD, EMAIL);

        when(userAccountRepository.findByEmail(dto.email())).thenReturn(new UserAccount());

        assertThatThrownBy(() -> userAccountService.register(dto))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessage("Email already exists");

        verify(userAccountRepository, never()).save(any());
    }

    private UserAccount persistedUser() {
        UserAccount saved = new UserAccount();
        saved.setName(NAME);
        saved.setEmail(EMAIL);
        saved.setPassword(ENCODED_PASSWORD);
        ReflectionTestUtils.setField(saved, "id", 1L);
        return saved;
    }
}