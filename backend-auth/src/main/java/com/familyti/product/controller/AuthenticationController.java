package com.familyti.product.controller;

import com.familyti.product.dto.AuthAccountResponse;
import com.familyti.product.dto.LoginRequestDTO;
import com.familyti.product.service.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {


    private final AuthorizationService service;

    public AuthenticationController(AuthorizationService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public AuthAccountResponse login(@RequestBody @Valid LoginRequestDTO dto) {
        return service.login(dto.email(), dto.password());
    }
}