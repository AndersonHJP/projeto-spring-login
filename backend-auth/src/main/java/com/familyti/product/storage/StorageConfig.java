package com.familyti.product.storage;

import com.familyti.product.exception.StorageConfigurationException;
import com.familyti.product.storage.StorageProperties.Backend;
import com.familyti.product.util.LoggerUtil;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.utils.SdkAutoCloseable;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    private final List<SdkAutoCloseable> openClients = new ArrayList<>();

    @Bean
    StorageRegistry storageRegistry(StorageProperties properties) {
        Map<String, Backend> active = validated(properties);

        Map<String, StorageStrategy> byProvider = new LinkedHashMap<>();
        active.forEach((provider, backend) -> byProvider.put(provider, open(provider, backend)));

        LoggerUtil.logInfo(getClass(), "storageRegistry",
                "Storage habilitado: {}. Gravacoes novas vao para '{}'.",
                byProvider.keySet(), properties.provider());

        return new StorageRegistry(byProvider, byProvider.get(properties.provider()));
    }

    private StorageStrategy open(String provider, Backend backend) {
        AwsCredentialsProvider credentials = credentialsFor(backend);
        S3Client client = clientFor(backend, credentials);
        S3Presigner presigner = presignerFor(backend, credentials);

        openClients.add(client);
        openClients.add(presigner);
        return new S3CompatibleStorageStrategy(provider, backend, client, presigner);
    }

    @PreDestroy
    void closeClients() {
        openClients.forEach(SdkAutoCloseable::close);
        openClients.clear();
    }

    // --- clientes ------------------------------------------------------------

    static AwsCredentialsProvider credentialsFor(Backend backend) {
        if (backend.hasStaticCredentials()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(backend.accessKey(), backend.secretKey()));
        }
        return DefaultCredentialsProvider.create();
    }

    static S3Client clientFor(Backend backend, AwsCredentialsProvider credentials) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(backend.region()))
                .credentialsProvider(credentials);

        if (!backend.isAws()) {
            builder.endpointOverride(URI.create(backend.endpoint())).forcePathStyle(true);
        }
        return builder.build();
    }

    static S3Presigner presignerFor(Backend backend, AwsCredentialsProvider credentials) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(backend.region()))
                .credentialsProvider(credentials);

        if (!backend.isAws()) {
            builder.endpointOverride(URI.create(backend.endpoint()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }

    // --- validacao da configuracao ------------------------------------------

    private static Map<String, Backend> validated(StorageProperties properties) {
        if (properties.enabled().isEmpty()) {
            throw new StorageConfigurationException(
                    "Nenhum provedor de storage habilitado. Defina STORAGE_PROVIDER (ou STORAGE_ENABLED, "
                            + "separado por virgula) com um ou mais dos backends declarados em storage.backends: "
                            + declared(properties) + ".");
        }

        List<String> undeclared = properties.undeclared();
        if (!undeclared.isEmpty()) {
            throw new StorageConfigurationException(
                    "storage.enabled contem provedor desconhecido: " + undeclared + ". Declarados em "
                            + "storage.backends: " + declared(properties)
                            + " (a comparacao ignora maiusculas e espacos ao redor). "
                            + "Corrija a variavel de ambiente STORAGE_ENABLED.");
        }

        String provider = properties.provider();
        if (provider.isEmpty()) {
            throw new StorageConfigurationException(
                    "storage.provider nao foi definido. Ele diz para onde vao as fotos novas e precisa ser um dos "
                            + "provedores habilitados (" + properties.enabled() + "). Defina a variavel de ambiente "
                            + "STORAGE_PROVIDER.");
        }
        if (!properties.enabled().contains(provider)) {
            throw new StorageConfigurationException(
                    "storage.provider='" + provider + "' nao esta habilitado. Habilitados: " + properties.enabled()
                            + ". Inclua-o em STORAGE_ENABLED ou aponte STORAGE_PROVIDER para um deles.");
        }

        Map<String, Backend> active = properties.activeBackends();
        active.forEach(StorageConfig::requireComplete);
        return active;
    }

    private static void requireComplete(String provider, Backend backend) {
        requireFilled(provider, backend, "bucket", backend.bucket(), "_BUCKET");
        requireFilled(provider, backend, "region", backend.region(), "_REGION");

        if (!backend.isAws() && !backend.hasStaticCredentials()) {
            // Fora da AWS nao existe IAM role: sem chave e segredo nao da para assinar.
            requireFilled(provider, backend, "access-key", backend.accessKey(), "_ACCESS_KEY");
            requireFilled(provider, backend, "secret-key", backend.secretKey(), "_SECRET_KEY");
        }
    }

    private static void requireFilled(String provider, Backend backend, String field, String value, String envSuffix) {
        if (value == null || value.isBlank()) {
            throw new StorageConfigurationException(
                    "O provedor '" + provider + "' esta habilitado e exige a property 'storage.backends."
                            + provider + "." + field + "' preenchida, mas o valor esta vazio. Defina a variavel de "
                            + "ambiente " + backend.envPrefix(provider) + envSuffix + ".");
        }
    }

    private static String declared(StorageProperties properties) {
        return properties.backends().isEmpty()
                ? "(nenhum)"
                : String.join(", ", properties.backends().keySet());
    }
}
