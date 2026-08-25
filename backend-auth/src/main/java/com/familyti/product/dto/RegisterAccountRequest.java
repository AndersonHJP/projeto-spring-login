package com.familyti.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterAccountRequest(

        @NotBlank(message = "The username is required.")
        String name,

        @NotBlank(message = "The password is required.")
        @Size(min = 8, message = "The password must be at least 8 characters long.")
        String password,

        @NotBlank(message = "The email address is required.")
        String email

) {
}
