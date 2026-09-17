package com.familyti.product.dto;

import java.util.List;

public record StorageInfoResponse(String provider, List<String> enabled) {
}