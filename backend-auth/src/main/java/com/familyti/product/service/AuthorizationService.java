package com.familyti.product.service;

import com.familyti.product.config.TokenService;
import com.familyti.product.dto.AuthAccountResponse;
import com.familyti.product.exception.InvalidCredentialsException;
import com.familyti.product.model.UserAccount;
import com.familyti.product.repository.UserAccountRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


@Service
public class AuthorizationService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Value("${jwt.expiration}")
    private long expiration;

    public AuthorizationService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional
    public AuthAccountResponse login(String email, String password) {
        UserAccount user = (UserAccount) userAccountRepository.findByEmail(email);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        String accessToken = tokenService.generateToken(user);
        return AuthAccountResponse.of(accessToken, expiration);
    }
}