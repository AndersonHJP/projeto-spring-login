package com.familyti.product.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ConfigurationProperties(prefix = "storage")
public record StorageProperties(

        String provider,

        List<String> enabled,

        Map<String, Backend> backends
) {

    public StorageProperties {
        provider = normalize(provider);
        backends = normalizeKeys(backends);
        enabled = normalizeAll(enabled);

        if (enabled.isEmpty() && !provider.isEmpty()) {
            enabled = List.of(provider);
        }
    }

    public Map<String, Backend> activeBackends() {
        Map<String, Backend> active = new LinkedHashMap<>();
        for (String name : enabled) {
            Backend backend = backends.get(name);
            if (backend != null) {
                active.put(name, backend);
            }
        }
        return active;
    }

    public List<String> undeclared() {
        return enabled.stream().filter(name -> !backends.containsKey(name)).toList();
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> normalizeAll(List<String> raw) {
        return raw == null ? List.of() : raw.stream()
                .map(StorageProperties::normalize)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private static Map<String, Backend> normalizeKeys(Map<String, Backend> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Backend> normalized = new LinkedHashMap<>();
        raw.forEach((name, backend) -> normalized.put(normalize(name), backend));
        return Map.copyOf(normalized);
    }

    public record Backend(

            String endpoint,

            @DefaultValue("us-east-1")
            String region,

            String bucket,

            String accessKey,

            String secretKey,

            @DefaultValue("15")
            int presignExpirationMinutes,

            @DefaultValue("false")
            boolean createBucketIfMissing,

            String displayName,

            String envPrefix
    ) {

        public boolean isAws() {
            return isBlank(endpoint);
        }

        public boolean hasStaticCredentials() {
            return !isBlank(accessKey) && !isBlank(secretKey);
        }

        String label(String provider) {
            return isBlank(displayName) ? provider : displayName;
        }

        String envPrefix(String provider) {
            return isBlank(envPrefix)
                    ? provider.toUpperCase(Locale.ROOT).replace('-', '_')
                    : envPrefix;
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }
}
