package com.familyti.product.service;

import com.familyti.product.dto.RegisterAccountRequest;
import com.familyti.product.dto.UserAccountResponse;
import com.familyti.product.enums.UserRole;
import com.familyti.product.exception.EmailAlreadyExistsException;
import com.familyti.product.model.UserAccount;
import com.familyti.product.repository.UserAccountRepository;
import jakarta.transaction.Transactional;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


@Service
public class UserAccountService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(@NonNull String email) {
        return userAccountRepository.findByEmail(email);
    }

    @Transactional
    public UserAccountResponse register(RegisterAccountRequest dto) {
        if (userAccountRepository.findByEmail(dto.email()) != null) {
            throw new EmailAlreadyExistsException("Email already exists");
        }
        UserAccount user = new UserAccount();
        user.setName(dto.name());
        user.setEmail(dto.email());
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setRole(UserRole.USER);

        user = userAccountRepository.save(user);

        return new UserAccountResponse(user.getId(), user.getName(), user.getEmail());
    }
}