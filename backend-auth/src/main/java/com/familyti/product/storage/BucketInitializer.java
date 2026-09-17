package com.familyti.product.storage;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BucketInitializer implements ApplicationRunner {

    private final StorageRegistry registry;

    public BucketInitializer(StorageRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void run(ApplicationArguments args) {
        registry.all().forEach(StorageStrategy::ensureBucketExists);
    }
}
