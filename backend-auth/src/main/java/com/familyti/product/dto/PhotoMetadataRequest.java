package com.familyti.product.dto;

import jakarta.validation.constraints.Size;


public record PhotoMetadataRequest(

        @Size(max = 255, message = "The title must have a maximum of 255 characters.")
        String title,

        @Size(max = 1000, message = "The description must have a maximum of 1,000 characters.")
        String description
) {
}