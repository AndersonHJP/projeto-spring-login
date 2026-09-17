package com.familyti.product.storage;

import com.familyti.product.storage.StorageProperties.Backend;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O toggle de storage tem duas alavancas: {@code storage.enabled} decide quais
 * backends sobem (de onde da para ler) e {@code storage.provider} decide para
 * onde vao as fotos novas.
 */
@DisplayName("Toggle storage.provider")
class StorageProviderToggleTest {

    private static final String ENDPOINT = "http://localhost:9000";

    private static final String[] BACKENDS = {
            "storage.backends.s3.bucket=test-bucket",
            "storage.backends.s3.region=us-east-1",
            "storage.backends.s3.env-prefix=AWS_S3",
            "storage.backends.minio.endpoint=" + ENDPOINT,
            "storage.backends.minio.bucket=app-photos-bucket",
            "storage.backends.minio.access-key=admin",
            "storage.backends.minio.secret-key=admin123"};

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StorageConfig.class)
            .withPropertyValues(BACKENDS);

    // --- storage.enabled ausente: vale o storage.provider sozinho ------------

    @Test
    @DisplayName("s3 registra apenas o backend s3")
    void shouldSelectS3() {
        runner.withPropertyValues("storage.provider=s3").run(context -> {
            assertThat(context).hasNotFailed();

            StorageRegistry registry = context.getBean(StorageRegistry.class);
            assertThat(registry.providers()).containsExactly("s3");
            assertThat(registry.writeTarget().provider()).isEqualTo("s3");
        });
    }

    @Test
    @DisplayName("minio registra apenas o backend minio")
    void shouldSelectMinio() {
        runner.withPropertyValues("storage.provider=minio").run(context -> {
            assertThat(context).hasNotFailed();

            StorageRegistry registry = context.getBean(StorageRegistry.class);
            assertThat(registry.providers()).containsExactly("minio");
            assertThat(registry.writeTarget().provider()).isEqualTo("minio");
        });
    }

    @Test
    @DisplayName("MINIO em maiusculo seleciona o mesmo backend")
    void shouldIgnoreCase() {
        runner.withPropertyValues("storage.provider=MINIO").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(StorageRegistry.class).providers()).containsExactly("minio");
        });
    }

    @Test
    @DisplayName("espacos ao redor do valor sao ignorados")
    void shouldTrimValue() {
        runner.withPropertyValues("storage.provider=  MiNiO  ").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(StorageRegistry.class).providers()).containsExactly("minio");
        });
    }

    // --- storage.enabled com os dois ----------------------------------------

    @Test
    @DisplayName("os dois habilitados registram as duas strategies, gravando so em uma")
    void shouldRegisterBothStrategies() {
        runner.withPropertyValues("storage.enabled=minio,s3", "storage.provider=minio").run(context -> {
            assertThat(context).hasNotFailed();

            StorageRegistry registry = context.getBean(StorageRegistry.class);
            assertThat(registry.providers()).containsExactlyInAnyOrder("minio", "s3");
            assertThat(registry.find("s3").provider()).isEqualTo("s3");
            assertThat(registry.find("minio").provider()).isEqualTo("minio");
            assertThat(registry.writeTarget().provider()).isEqualTo("minio");
        });
    }

    @Test
    @DisplayName("a lista aceita espacos, maiusculas e valor repetido")
    void shouldNormalizeEnabledList() {
        runner.withPropertyValues("storage.enabled= MinIO , minio ,S3 ", "storage.provider=s3").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(StorageRegistry.class).providers())
                    .containsExactlyInAnyOrder("minio", "s3");
        });
    }

    @Test
    @DisplayName("backend declarado mas fora da lista de habilitados nao sobe")
    void shouldNotRegisterDeclaredButDisabledBackend() {
        runner.withPropertyValues("storage.enabled=minio", "storage.provider=minio").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(StorageRegistry.class).find("s3")).isNull();
        });
    }

    // --- falhas de configuracao ---------------------------------------------

    @Test
    @DisplayName("valor sem backend declarado nao deixa o contexto subir")
    void shouldRejectUnknownProvider() {
        runner.withPropertyValues("storage.provider=xpto").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure()
                    .hasStackTraceContaining("provedor desconhecido")
                    .hasStackTraceContaining("s3")
                    .hasStackTraceContaining("minio");
        });
    }

    @Test
    @DisplayName("valor vazio nao deixa o contexto subir")
    void shouldRejectEmptyProvider() {
        runner.withPropertyValues("storage.provider=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure()
                    .hasStackTraceContaining("Nenhum provedor de storage habilitado");
        });
    }

    @Test
    @DisplayName("destino de gravacao fora da lista de habilitados nao deixa o contexto subir")
    void shouldRejectWriteProviderOutsideEnabled() {
        runner.withPropertyValues("storage.enabled=minio", "storage.provider=s3").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure()
                    .hasStackTraceContaining("nao esta habilitado")
                    .hasStackTraceContaining("STORAGE_ENABLED");
        });
    }

    @Test
    @DisplayName("property obrigatoria de QUALQUER backend habilitado vazia nao deixa o contexto subir")
    void shouldRejectBlankPropertyOfAnyEnabledBackend() {
        // O destino da gravacao e o MinIO, mas o S3 tambem esta habilitado para
        // leitura - entao a configuracao dele tambem precisa estar completa.
        runner.withPropertyValues("storage.enabled=minio,s3", "storage.provider=minio",
                        "storage.backends.s3.bucket=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasStackTraceContaining("exige a property 'storage.backends.s3.bucket' preenchida")
                            .hasStackTraceContaining("AWS_S3_BUCKET");
                });
    }

    @Test
    @DisplayName("backend com endpoint proprio e sem chaves nao deixa o contexto subir")
    void shouldRequireCredentialsOutsideAws() {
        // Fora da AWS nao existe IAM role para cair de volta.
        runner.withPropertyValues("storage.provider=minio", "storage.backends.minio.access-key=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasStackTraceContaining("storage.backends.minio.access-key")
                            .hasStackTraceContaining("MINIO_ACCESS_KEY");
                });
    }

    // --- forma do cliente por backend ---------------------------------------

    @Test
    @DisplayName("backend com endpoint ganha endpointOverride e path-style")
    void shouldOverrideEndpointOutsideAws() {
        Backend backend = new Backend(ENDPOINT, "us-east-1", "bucket", "admin", "admin123",
                15, true, "MinIO", "MINIO");

        try (S3Client client = StorageConfig.clientFor(backend, StorageConfig.credentialsFor(backend))) {
            assertThat(client.serviceClientConfiguration().endpointOverride())
                    .contains(URI.create(ENDPOINT));
        }
    }

    @Test
    @DisplayName("backend sem endpoint fala com a AWS de verdade")
    void shouldNotOverrideEndpointOnAws() {
        Backend backend = new Backend(null, "us-east-2", "bucket", null, null,
                15, false, "Amazon S3", "AWS_S3");

        try (S3Client client = StorageConfig.clientFor(backend, StorageConfig.credentialsFor(backend))) {
            assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
        }
    }

    // --- credenciais: property quando declarada, cadeia padrao quando nao ----

    @Test
    @DisplayName("chaves declaradas viram credencial estatica")
    void shouldUseStaticCredentialsFromProperties() {
        AwsCredentialsProvider credentials = StorageConfig.credentialsFor(
                new Backend(null, "us-east-1", "bucket", "AKIATESTE", "segredo-de-teste",
                        15, false, null, null));

        assertThat(credentials).isInstanceOf(StaticCredentialsProvider.class);
        assertThat(credentials.resolveCredentials().accessKeyId()).isEqualTo("AKIATESTE");
    }

    @Test
    @DisplayName("sem as chaves declaradas, vale a cadeia padrao do SDK")
    void shouldFallBackToDefaultChain() {
        AwsCredentialsProvider credentials = StorageConfig.credentialsFor(
                new Backend(null, "us-east-1", "bucket", null, null, 15, false, null, null));

        assertThat(credentials).isInstanceOf(DefaultCredentialsProvider.class);
    }

    @Test
    @DisplayName("chave sem segredo nao vira credencial estatica pela metade")
    void shouldIgnoreHalfFilledCredentials() {
        AwsCredentialsProvider credentials = StorageConfig.credentialsFor(
                new Backend(null, "us-east-1", "bucket", "AKIATESTE", null, 15, false, null, null));

        assertThat(credentials).isInstanceOf(DefaultCredentialsProvider.class);
    }
}
