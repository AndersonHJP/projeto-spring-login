package com.familyti.product.storage;

import com.familyti.product.exception.StorageConfigurationException;
import com.familyti.product.util.LoggerUtil;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.util.PlaceholderResolutionException;

import java.util.Arrays;
import java.util.Map;


public class StorageStartupValidator implements BeanFactoryPostProcessor {

    private static final Map<String, String> ENV_VAR_OF = Map.of(
            "aws.s3.bucket", "AWS_S3_BUCKET",
            "aws.s3.region", "AWS_S3_REGION",
            "minio.endpoint", "MINIO_ENDPOINT",
            "minio.bucket", "MINIO_BUCKET",
            "minio.access-key", "MINIO_ACCESS_KEY",
            "minio.secret-key", "MINIO_SECRET_KEY");

    private final Environment environment;

    public StorageStartupValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        String provider = requireValidProvider();
        requireSingleStrategy(beanFactory, provider);

        if (StorageProperties.S3.equals(provider)) {
            String bucket = requireFilled("aws.s3.bucket", provider);
            String region = requireFilled("aws.s3.region", provider);
            LoggerUtil.logInfo(getClass(), "validate",
                    "Storage ativo: s3 (bucket={}, region={}). Credenciais resolvidas pela cadeia padrao do SDK.",
                    bucket, region);
        } else {
            String endpoint = requireFilled("minio.endpoint", provider);
            String bucket = requireFilled("minio.bucket", provider);
            requireFilled("minio.access-key", provider);
            requireFilled("minio.secret-key", provider);
            LoggerUtil.logInfo(getClass(), "validate",
                    "Storage ativo: minio (endpoint={}, bucket={}). Credenciais estaticas configuradas.",
                    endpoint, bucket);
        }
    }

    private String requireValidProvider() {
        String raw = read(StorageProperties.PROPERTY);
        String provider = StorageProperties.normalize(raw);

        if (provider.isEmpty()) {
            throw new StorageConfigurationException(
                    "storage.provider nao foi definido. Valores aceitos: " + StorageProperties.validValues()
                            + ". Defina a variavel de ambiente STORAGE_PROVIDER ou a property storage.provider.");
        }
        if (!StorageProperties.isValid(provider)) {
            throw new StorageConfigurationException(
                    "storage.provider invalido: '" + raw + "'. Valores aceitos: " + StorageProperties.validValues()
                            + " (a comparacao ignora maiusculas e espacos ao redor). "
                            + "Corrija a variavel de ambiente STORAGE_PROVIDER.");
        }
        return provider;
    }

    private void requireSingleStrategy(ConfigurableListableBeanFactory beanFactory, String provider) {
        String[] names = beanFactory.getBeanNamesForType(StorageStrategy.class, true, false);

        if (names.length == 0) {
            throw new StorageConfigurationException(
                    "Nenhum bean de StorageStrategy foi registrado para storage.provider='" + provider + "'. "
                            + "O provedor precisa de uma implementacao anotada com "
                            + "@ConditionalOnStorageProvider(\"" + provider + "\").");
        }
        if (names.length > 1) {
            throw new StorageConfigurationException(
                    "Esperado exatamente 1 bean de StorageStrategy para storage.provider='" + provider
                            + "', encontrados " + names.length + ": " + Arrays.toString(names) + ". "
                            + "Com mais de uma implementacao ativa nao ha como decidir o destino da gravacao. "
                            + "Toda implementacao de StorageStrategy deve ser condicional a storage.provider.");
        }
    }

    private String requireFilled(String key, String provider) {
        String value = read(key);
        if (value == null || value.isBlank()) {
            throw new StorageConfigurationException(
                    "storage.provider='" + provider + "' exige a property '" + key + "' preenchida, "
                            + "mas o valor esta vazio. Defina a variavel de ambiente " + envVarOf(key) + ".");
        }
        return value;
    }

    private String read(String key) {
        try {
            return environment.getProperty(key);
        } catch (PlaceholderResolutionException e) {
            throw new StorageConfigurationException(
                    "A property '" + key + "' nao pode ser resolvida: a variavel de ambiente "
                            + envVarOf(key) + " nao esta definida.");
        }
    }

    private String envVarOf(String key) {
        return ENV_VAR_OF.getOrDefault(key, key.toUpperCase().replace('.', '_').replace('-', '_'));
    }
}