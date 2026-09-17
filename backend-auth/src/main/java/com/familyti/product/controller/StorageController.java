package com.familyti.product.controller;

import com.familyti.product.dto.StorageInfoResponse;
import com.familyti.product.storage.StorageProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/storage")
public class StorageController {

    private final StorageProperties properties;

    public StorageController(StorageProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public StorageInfoResponse current() {
        return new StorageInfoResponse(properties.provider(), properties.enabled());
    }
}