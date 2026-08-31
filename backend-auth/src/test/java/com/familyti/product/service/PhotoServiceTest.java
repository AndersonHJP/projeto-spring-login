package com.familyti.product.service;

import com.familyti.product.dto.PhotoResponse;
import com.familyti.product.exception.ForbiddenOperationException;
import com.familyti.product.exception.InvalidFileException;
import com.familyti.product.exception.ResourceNotFoundException;
import com.familyti.product.exception.StorageException;
import com.familyti.product.model.Photo;
import com.familyti.product.model.UserAccount;
import com.familyti.product.repository.PhotoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PhotoService")
class PhotoServiceTest {

    private static final Long OWNER_ID = 12L;
    private static final Long INTRUDER_ID = 99L;
    private static final Long PHOTO_ID = 1L;
    private static final String EXISTING_KEY = "users/12/photos/old-uuid.jpg";
    private static final String PRESIGNED_URL = "https://bucket.s3.amazonaws.com/key?X-Amz-Signature=abc";

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private S3StorageService storageService;

    @InjectMocks
    private PhotoService photoService;

    // ------------------------------------------------------------------ upload

    @Nested
    @DisplayName("upload()")
    class Upload {

        @Test
        @DisplayName("envia ao S3 antes de persistir e devolve URL pre-assinada")
        void shouldUploadThenPersist() {
            UserAccount owner = user(OWNER_ID);
            MultipartFile file = jpeg("praia.jpg", 245_760);

            when(storageService.objectUrl(anyString())).thenReturn("https://bucket.s3.amazonaws.com/key");
            when(storageService.generatePresignedUrl(anyString())).thenReturn(PRESIGNED_URL);
            when(photoRepository.save(any(Photo.class))).thenAnswer(call -> {
                Photo saved = call.getArgument(0);
                saved.setId(PHOTO_ID);
                saved.setCreatedAt(LocalDateTime.now());
                saved.setUpdatedAt(LocalDateTime.now());
                return saved;
            });

            PhotoResponse response = photoService.upload(owner, file, "Praia", "Por do sol");

            ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
            verify(storageService).upload(key.capture(), any(InputStream.class), anyLong(), anyString());

            assertThat(key.getValue())
                    .startsWith("users/12/photos/")
                    .endsWith(".jpg");
            assertThat(response.title()).isEqualTo("Praia");
            assertThat(response.description()).isEqualTo("Por do sol");
            assertThat(response.contentType()).isEqualTo("image/jpeg");
            assertThat(response.sizeBytes()).isEqualTo(245_760L);
            assertThat(response.url()).isEqualTo(PRESIGNED_URL);
            assertThat(response.s3Key()).isEqualTo(key.getValue());
        }

        @Test
        @DisplayName("usa o nome do arquivo sem extensao como titulo padrao")
        void shouldDefaultTitleToFilename() {
            when(storageService.objectUrl(anyString())).thenReturn("https://bucket/key");
            when(storageService.generatePresignedUrl(anyString())).thenReturn(PRESIGNED_URL);
            when(photoRepository.save(any(Photo.class))).thenAnswer(call -> call.getArgument(0));

            PhotoResponse response = photoService.upload(user(OWNER_ID), jpeg("ferias.jpeg", 1024), null, null);

            assertThat(response.title()).isEqualTo("ferias");
        }

        @Test
        @DisplayName("nao persiste nada quando o upload ao S3 falha")
        void shouldNotPersistWhenS3Fails() {
            doThrow(new StorageException("S3 fora do ar", new RuntimeException()))
                    .when(storageService).upload(anyString(), any(InputStream.class), anyLong(), anyString());

            assertThatThrownBy(() -> photoService.upload(user(OWNER_ID), jpeg("a.jpg", 10), null, null))
                    .isInstanceOf(StorageException.class);

            verify(photoRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejeita arquivo cujo conteudo nao e uma imagem")
        void shouldRejectUnsupportedContentType() {
            MultipartFile pdf = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", withSignature(PDF_SIGNATURE, 32));

            assertThatThrownBy(() -> photoService.upload(user(OWNER_ID), pdf, null, null))
                    .isInstanceOf(InvalidFileException.class)
                    .hasMessageContaining("nao e uma imagem valida");

            verifyNoInteractions(storageService);
        }

        @Test
        @DisplayName("rejeita extensao incoerente com o conteudo real do arquivo")
        void shouldRejectMismatchedExtension() {
            // PNG de verdade com nome .exe: o conteudo e valido, a extensao e que nao bate.
            MultipartFile disguised = new MockMultipartFile(
                    "file", "malware.exe", "image/png", withSignature(PNG_SIGNATURE, 32));

            assertThatThrownBy(() -> photoService.upload(user(OWNER_ID), disguised, null, null))
                    .isInstanceOf(InvalidFileException.class)
                    .hasMessageContaining("nao corresponde");

            verifyNoInteractions(storageService);
        }

        @Test
        @DisplayName("rejeita arquivo acima de 5 MB")
        void shouldRejectOversizedFile() {
            MultipartFile big = new MockMultipartFile(
                    "file", "big.png", "image/png", new byte[(int) PhotoService.MAX_FILE_SIZE_BYTES + 1]);

            assertThatThrownBy(() -> photoService.upload(user(OWNER_ID), big, null, null))
                    .isInstanceOf(InvalidFileException.class)
                    .hasMessageContaining("5 MB");

            verifyNoInteractions(storageService);
        }

        @Test
        @DisplayName("rejeita arquivo vazio")
        void shouldRejectEmptyFile() {
            MultipartFile empty = new MockMultipartFile("file", "vazio.png", "image/png", new byte[0]);

            assertThatThrownBy(() -> photoService.upload(user(OWNER_ID), empty, null, null))
                    .isInstanceOf(InvalidFileException.class);
        }
    }

    // ----------------------------------------------------------------- listagem

    @Nested
    @DisplayName("listByUser()")
    class ListByUser {

        @Test
        @DisplayName("devolve apenas as fotos do usuario, ja ordenadas pelo repositorio")
        void shouldListOwnPhotos() {
            UserAccount owner = user(OWNER_ID);
            when(photoRepository.findByUserIdOrderByCreatedAtDesc(OWNER_ID))
                    .thenReturn(List.of(photo(1L, owner), photo(2L, owner)));
            when(storageService.generatePresignedUrl(anyString())).thenReturn(PRESIGNED_URL);

            List<PhotoResponse> result = photoService.listByUser(owner);

            assertThat(result).hasSize(2);
            assertThat(result).allSatisfy(item -> assertThat(item.url()).isEqualTo(PRESIGNED_URL));
            verify(photoRepository).findByUserIdOrderByCreatedAtDesc(OWNER_ID);
        }
    }

    // ------------------------------------------------------------------ detalhe

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("404 quando a foto nao existe")
        void shouldThrowNotFound() {
            when(photoRepository.findById(PHOTO_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> photoService.getById(user(OWNER_ID), PHOTO_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("403 quando a foto pertence a outro usuario")
        void shouldThrowForbidden() {
            when(photoRepository.findById(PHOTO_ID)).thenReturn(Optional.of(photo(PHOTO_ID, user(OWNER_ID))));

            assertThatThrownBy(() -> photoService.getById(user(INTRUDER_ID), PHOTO_ID))
                    .isInstanceOf(ForbiddenOperationException.class);

            verifyNoInteractions(storageService);
        }
    }

    // ------------------------------------------------------------------- edicao

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("altera apenas metadados quando nenhum arquivo e enviado")
        void shouldUpdateMetadataOnly() {
            UserAccount owner = user(OWNER_ID);
            Photo existing = photo(PHOTO_ID, owner);

            when(photoRepository.findById(PHOTO_ID)).thenReturn(Optional.of(existing));
            when(photoRepository.save(existing)).thenReturn(existing);
            when(storageService.generatePresignedUrl(EXISTING_KEY)).thenReturn(PRESIGNED_URL);

            PhotoResponse response = photoService.update(owner, PHOTO_ID, "Novo titulo", "Nova descricao", null);

            assertThat(response.title()).isEqualTo("Novo titulo");
            assertThat(response.description()).isEqualTo("Nova descricao");
            assertThat(response.s3Key()).isEqualTo(EXISTING_KEY);
            verify(storageService, never()).upload(anyString(), any(InputStream.class), anyLong(), anyString());
            verify(storageService, never()).delete(anyString());
        }

        @Test
        @DisplayName("sobe o novo objeto e remove o antigo quando o arquivo e substituido")
        void shouldReplaceBinary() {
            UserAccount owner = user(OWNER_ID);
            Photo existing = photo(PHOTO_ID, owner);

            when(photoRepository.findById(PHOTO_ID)).thenReturn(Optional.of(existing));
            when(photoRepository.save(existing)).thenReturn(existing);
            when(storageService.objectUrl(anyString())).thenReturn("https://bucket/new");
            when(storageService.generatePresignedUrl(anyString())).thenReturn(PRESIGNED_URL);

            PhotoResponse response = photoService.update(owner, PHOTO_ID, null, null, png(2048));

            assertThat(response.s3Key()).isNotEqualTo(EXISTING_KEY).endsWith(".png");
            assertThat(response.contentType()).isEqualTo("image/png");
            assertThat(response.sizeBytes()).isEqualTo(2048L);
            verify(storageService).delete(EXISTING_KEY);
        }

        @Test
        @DisplayName("mantem a foto atual quando o upload do substituto falha")
        void shouldKeepCurrentPhotoWhenNewUploadFails() {
            UserAccount owner = user(OWNER_ID);
            Photo existing = photo(PHOTO_ID, owner);

            when(photoRepository.findById(PHOTO_ID)).thenReturn(Optional.of(existing));
            doThrow(new StorageException("falhou", new RuntimeException()))
                    .when(storageService).upload(anyString(), any(InputStream.class), anyLong(), anyString());

            assertThatThrownBy(() -> photoService.update(owner, PHOTO_ID, null, null, png(10)))
                    .isInstanceOf(StorageException.class);

            assertThat(existing.getS3Key()).isEqualTo(EXISTING_KEY);
            verify(storageService, never()).delete(EXISTING_KEY);
            verify(photoRepository, never()).save(any());
        }

        @Test
        @DisplayName("403 quando a foto pertence a outro usuario")
        void shouldRejectForeignPhoto() {
            when(photoRepository.findById(PHOTO_ID)).thenReturn(Optional.of(photo(PHOTO_ID, user(OWNER_ID))));

            assertThatThrownBy(() -> photoService.update(user(INTRUDER_ID), PHOTO_ID, "x", null, null))
                    .isInstanceOf(ForbiddenOperationException.class);

            verify(photoRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------- exclusao

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("remove a linha e o objeto do S3")
        void shouldDeleteBoth() {
            UserAccount owner = user(OWNER_ID);
            Photo existing = photo(PHOTO_ID, owner);
            when(photoRepository.findById(PHOTO_ID)).thenReturn(Optional.of(existing));

            photoService.delete(owner, PHOTO_ID);

            verify(photoRepository).delete(existing);
            verify(storageService).delete(EXISTING_KEY);
        }

        @Test
        @DisplayName("propaga o erro do S3 para que a transacao faca rollback do delete no banco")
        void shouldPropagateS3Failure() {
            UserAccount owner = user(OWNER_ID);
            when(photoRepository.findById(PHOTO_ID)).thenReturn(Optional.of(photo(PHOTO_ID, owner)));
            doThrow(new StorageException("S3 recusou o delete", new RuntimeException()))
                    .when(storageService).delete(EXISTING_KEY);

            assertThatThrownBy(() -> photoService.delete(owner, PHOTO_ID))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("S3");
        }

        @Test
        @DisplayName("403 quando a foto pertence a outro usuario")
        void shouldRejectForeignPhoto() {
            when(photoRepository.findById(PHOTO_ID)).thenReturn(Optional.of(photo(PHOTO_ID, user(OWNER_ID))));

            assertThatThrownBy(() -> photoService.delete(user(INTRUDER_ID), PHOTO_ID))
                    .isInstanceOf(ForbiddenOperationException.class);

            verify(photoRepository, never()).delete(any());
            verifyNoInteractions(storageService);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static UserAccount user(Long id) {
        UserAccount account = new UserAccount();
        account.setId(id);
        account.setEmail("user" + id + "@test.com");
        return account;
    }

    private static Photo photo(Long id, UserAccount owner) {
        return Photo.builder()
                .id(id)
                .user(owner)
                .originalFilename("praia.jpg")
                .storedFilename("old-uuid.jpg")
                .contentType("image/jpeg")
                .sizeBytes(1024L)
                .s3Key(EXISTING_KEY)
                .s3Url("https://bucket.s3.amazonaws.com/" + EXISTING_KEY)
                .title("Praia")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /** Assinaturas reais dos formatos: a validacao le os magic bytes, nao o content-type declarado. */
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};

    private static MultipartFile jpeg(String name, int size) {
        return new MockMultipartFile("file", name, "image/jpeg", withSignature(JPEG_SIGNATURE, size));
    }

    private static MultipartFile png(int size) {
        return new MockMultipartFile("file", "nova.png", "image/png", withSignature(PNG_SIGNATURE, size));
    }

    /** Conteudo do tamanho pedido, comecando pela assinatura do formato. */
    private static byte[] withSignature(byte[] signature, int size) {
        byte[] content = new byte[Math.max(size, signature.length)];
        System.arraycopy(signature, 0, content, 0, signature.length);
        return content;
    }
}