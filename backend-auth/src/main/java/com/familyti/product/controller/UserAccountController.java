package com.familyti.product.controller;

import com.familyti.product.dto.RegisterAccountRequest;
import com.familyti.product.dto.UserAccountResponse;
import com.familyti.product.model.UserAccount;
import com.familyti.product.service.UserAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserAccountController {

    private final UserAccountService service;

    public UserAccountController(UserAccountService service) {
        this.service = service;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserAccountResponse register(@RequestBody @Valid RegisterAccountRequest dto) {
        return service.register(dto);
    }

    @GetMapping("/me")
    public ResponseEntity<UserAccountResponse> getMe(@AuthenticationPrincipal UserAccount user) {
        UserAccountResponse response = new UserAccountResponse(user.getId(), user.getName(), user.getEmail());
        return ResponseEntity.ok(response);
    }
}
