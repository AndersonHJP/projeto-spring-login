package com.familyti.product.dto;

public record AuthAccountResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
    public static AuthAccountResponse of(String accessToken, long expiresIn) {
        return new AuthAccountResponse(accessToken, "Bearer", expiresIn);
    }

}
