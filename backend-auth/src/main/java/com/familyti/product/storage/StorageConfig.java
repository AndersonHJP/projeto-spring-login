package com.familyti.product.storage;

import com.familyti.product.config.S3Properties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;


@Configuration
@EnableConfigurationProperties({StorageProperties.class, MinioProperties.class, S3Properties.class})
public class StorageConfig {
}
