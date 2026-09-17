package com.familyti.product.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BucketInitializer")
class BucketInitializerTest {

    @Mock
    private StorageStrategy s3;

    @Mock
    private StorageStrategy minio;

    @Test
    @DisplayName("oferece o preparo do bucket a todo backend habilitado, nao so ao destino de gravacao")
    void shouldOfferBucketPreparationToEveryEnabledBackend() {
        when(s3.provider()).thenReturn("s3");
        when(minio.provider()).thenReturn("minio");

        new BucketInitializer(StorageRegistry.of("minio", s3, minio))
                .run(new DefaultApplicationArguments());

        // Quem decide se ha algo a fazer e cada strategy, pelo create-bucket-if-missing.
        verify(s3).ensureBucketExists();
        verify(minio).ensureBucketExists();
    }
}
