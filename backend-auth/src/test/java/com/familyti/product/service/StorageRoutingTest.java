package com.familyti.product.service;

import com.familyti.product.dto.PhotoResponse;
import com.familyti.product.exception.InvalidStorageProviderException;
import com.familyti.product.exception.StorageException;
import com.familyti.product.model.Photo;
import com.familyti.product.model.UserAccount;
import com.familyti.product.repository.PhotoRepository;
import com.familyti.product.storage.StorageRegistry;
import com.familyti.product.storage.StorageStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PhotoService: roteamento por provedor")
class StorageRoutingTest {

    private static final Long OWNER_ID = 7L;

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private StorageStrategy s3;

    @Mock
    private StorageStrategy minio;

    @BeforeEach
    void stubProviders() {
        when(s3.provider()).thenReturn("s3");
        when(minio.provider()).thenReturn("minio");
    }

    /** Grava no MinIO, mas le dos dois. */
    private PhotoService serviceWithBoth() {
        return new PhotoService(photoRepository, StorageRegistry.of("minio", s3, minio));
    }

    /** So o MinIO habilitado: o cenario de antes, com fotos antigas na AWS. */
    private PhotoService serviceWithMinioOnly() {
        return new PhotoService(photoRepository, StorageRegistry.of("minio", minio));
    }

    @Test
    @DisplayName("a listagem assina cada foto com o provedor dela")
    void shouldSignEachPhotoWithItsOwnProvider() {
        when(photoRepository.findByUserIdOrderByCreatedAtDesc(OWNER_ID))
                .thenReturn(List.of(photo(1L, "minio"), photo(2L, "s3")));
        when(minio.generateUrl("key-1")).thenReturn("http://localhost:9000/assinada-1");
        when(s3.generateUrl("key-2")).thenReturn("https://bucket.s3.amazonaws.com/assinada-2");

        List<PhotoResponse> photos = serviceWithBoth().listByUser(user());

        assertThat(photos).extracting(PhotoResponse::provider).containsExactly("minio", "s3");
        assertThat(photos).extracting(PhotoResponse::url)
                .containsExactly("http://localhost:9000/assinada-1", "https://bucket.s3.amazonaws.com/assinada-2");
    }

    @Test
    @DisplayName("provedor nao habilitado devolve url nula em vez de link assinado pelo outro")
    void shouldReturnNullUrlWhenProviderIsDisabled() {
        when(photoRepository.findByUserIdOrderByCreatedAtDesc(OWNER_ID))
                .thenReturn(List.of(photo(2L, "s3")));

        List<PhotoResponse> photos = serviceWithMinioOnly().listByUser(user());

        assertThat(photos).singleElement()
                .satisfies(response -> {
                    assertThat(response.provider()).isEqualTo("s3");
                    assertThat(response.url()).isNull();
                });
        verify(minio, never()).generateUrl("key-2");
    }

    @Test
    @DisplayName("uma foto que nao assina nao derruba a listagem das outras")
    void shouldNotLetOneBrokenProviderBreakTheWholeListing() {
        when(photoRepository.findByUserIdOrderByCreatedAtDesc(OWNER_ID))
                .thenReturn(List.of(photo(1L, "minio"), photo(2L, "s3")));
        when(minio.generateUrl("key-1")).thenReturn("http://localhost:9000/assinada-1");
        // S3 habilitado, mas sem credencial resolvivel - o caso de quem liga o s3
        // sem preencher AWS_ACCESS_KEY_ID.
        when(s3.generateUrl("key-2")).thenThrow(new StorageException("Unable to load credentials"));

        List<PhotoResponse> photos = serviceWithBoth().listByUser(user());

        assertThat(photos).extracting(PhotoResponse::url)
                .containsExactly("http://localhost:9000/assinada-1", null);
    }

    @Test
    @DisplayName("apagar uma foto do S3 remove do S3, nao do destino de gravacao")
    void shouldDeleteFromTheProviderThatHoldsThePhoto() {
        Photo photo = photo(2L, "s3");
        when(photoRepository.findById(2L)).thenReturn(Optional.of(photo));

        serviceWithBoth().delete(user(), 2L);

        verify(s3).delete("key-2");
        verify(minio, never()).delete("key-2");
    }

    @Test
    @DisplayName("recusa apagar quando o provedor da foto nao esta habilitado, e mantem a linha")
    void shouldRefuseToDeleteWhatItCannotReach() {
        Photo photo = photo(2L, "s3");
        when(photoRepository.findById(2L)).thenReturn(Optional.of(photo));

        PhotoService service = serviceWithMinioOnly();

        assertThatThrownBy(() -> service.delete(user(), 2L))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("s3")
                .hasMessageContaining("STORAGE_ENABLED");

        verify(photoRepository, never()).delete(photo);
        verify(minio, never()).delete(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("a foto nova vai para o destino de gravacao e nasce com o provedor gravado")
    void shouldUploadToTheWriteProvider() {
        when(minio.objectUrl(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("http://localhost:9000/app-photos-bucket/nova.png");
        when(minio.generateUrl(org.mockito.ArgumentMatchers.anyString())).thenReturn("http://localhost:9000/assinada");
        when(photoRepository.save(org.mockito.ArgumentMatchers.any(Photo.class)))
                .thenAnswer(call -> call.getArgument(0));

        PhotoResponse response = serviceWithBoth().upload(user(),
                new org.springframework.mock.web.MockMultipartFile("file", "nova.png", "image/png",
                        new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0}),
                null, null, null);

        assertThat(response.provider()).isEqualTo("minio");
        verify(minio).upload(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(byte[].class));
        verify(s3, never()).upload(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(byte[].class));
    }

    // --- destino escolhido no upload ----------------------------------------

    @Test
    @DisplayName("o destino pedido no upload vence o padrao, quando esta habilitado")
    void shouldHonourRequestedDestination() {
        when(s3.objectUrl(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("https://bucket.s3.amazonaws.com/nova.png");
        when(s3.generateUrl(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("https://bucket.s3.amazonaws.com/assinada");
        when(photoRepository.save(org.mockito.ArgumentMatchers.any(Photo.class)))
                .thenAnswer(call -> call.getArgument(0));

        // Grava por padrao no MinIO, mas o cliente pediu S3.
        PhotoResponse response = serviceWithBoth().upload(user(), png(), null, null, " S3 ");

        assertThat(response.provider()).isEqualTo("s3");
        verify(s3).upload(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(byte[].class));
        verify(minio, never()).upload(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(byte[].class));
    }

    @Test
    @DisplayName("destino desconhecido e recusado em vez de cair no padrao")
    void shouldRejectUnknownDestination() {
        PhotoService service = serviceWithBoth();

        assertThatThrownBy(() -> service.upload(user(), png(), null, null, "gcs"))
                .isInstanceOf(InvalidStorageProviderException.class)
                .hasMessageContaining("gcs");

        verify(photoRepository, never()).save(org.mockito.ArgumentMatchers.any(Photo.class));
    }

    @Test
    @DisplayName("destino valido mas nao habilitado tambem e recusado")
    void shouldRejectDestinationThatIsNotEnabled() {
        PhotoService service = serviceWithMinioOnly();

        assertThatThrownBy(() -> service.upload(user(), png(), null, null, "s3"))
                .isInstanceOf(InvalidStorageProviderException.class)
                .hasMessageContaining("s3");

        verify(minio, never()).upload(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(byte[].class));
    }

    private org.springframework.mock.web.MockMultipartFile png() {
        return new org.springframework.mock.web.MockMultipartFile("file", "nova.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0});
    }

    private UserAccount user() {
        UserAccount account = new UserAccount();
        account.setId(OWNER_ID);
        return account;
    }

    private Photo photo(Long id, String provider) {
        return Photo.builder()
                .id(id)
                .user(user())
                .originalFilename("foto.png")
                .storedFilename("foto.png")
                .contentType("image/png")
                .sizeBytes(10L)
                .s3Key("key-" + id)
                .s3Url("http://origem/key-" + id)
                .provider(provider)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

}
