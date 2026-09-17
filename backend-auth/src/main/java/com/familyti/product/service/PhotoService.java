package com.familyti.product.service;

import com.familyti.product.dto.PhotoResponse;
import com.familyti.product.exception.ForbiddenOperationException;
import com.familyti.product.exception.InvalidFileException;
import com.familyti.product.exception.InvalidStorageProviderException;
import com.familyti.product.exception.ResourceNotFoundException;
import com.familyti.product.exception.StorageException;
import com.familyti.product.model.Photo;
import com.familyti.product.model.UserAccount;
import com.familyti.product.repository.PhotoRepository;
import com.familyti.product.storage.StorageProperties;
import com.familyti.product.storage.StorageRegistry;
import com.familyti.product.storage.StorageStrategy;
import com.familyti.product.util.LoggerUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PhotoService {

    private static final Map<String, Set<String>> ALLOWED_TYPES = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "image/gif", Set.of("gif"),
            "image/webp", Set.of("webp")
    );

    private final PhotoRepository photoRepository;

    private final StorageRegistry storage;

    public PhotoService(PhotoRepository photoRepository, StorageRegistry storage) {
        this.photoRepository = photoRepository;
        this.storage = storage;
    }

    private StorageStrategy strategyFor(Photo photo) {
        String provider = photo.resolveProvider();
        if (provider == null) {
            LoggerUtil.logWarn(getClass(), "strategyFor",
                    "Foto {} sem provedor identificavel; usando o destino de gravacao '{}'.",
                    photo.getId(), storage.writeTarget().provider());
            return storage.writeTarget();
        }

        StorageStrategy strategy = storage.find(provider);
        if (strategy == null) {
            throw new StorageException("Esta foto esta no provedor '" + provider
                    + "', que nao esta habilitado nesta execucao (habilitados: " + storage.providers()
                    + "). Inclua-o em STORAGE_ENABLED para operar sobre ela.");
        }
        return strategy;
    }

    private StorageStrategy destinationFor(String requestedProvider) {
        if (requestedProvider == null || requestedProvider.isBlank()) {
            return storage.writeTarget();
        }

        String provider = StorageProperties.normalize(requestedProvider);
        StorageStrategy strategy = storage.find(provider);
        if (strategy == null) {
            throw new InvalidStorageProviderException("Destino de upload invalido: '" + requestedProvider
                    + "'. Disponiveis nesta execucao: " + storage.providers() + ".");
        }
        return strategy;
    }

    @Transactional
    public PhotoResponse upload(UserAccount user, MultipartFile file, String title, String description,
                                String requestedProvider) {
        String contentType = validate(file);
        StorageStrategy destination = destinationFor(requestedProvider);

        String extension = extensionOf(file.getOriginalFilename());
        String storedFilename = UUID.randomUUID() + "." + extension;
        String s3Key = buildKey(user.getId(), storedFilename);

        transfer(destination, file, s3Key, contentType);

        Photo photo = Photo.builder()
                .user(user)
                .originalFilename(file.getOriginalFilename())
                .storedFilename(storedFilename)
                .contentType(contentType)
                .sizeBytes(file.getSize())
                .s3Key(s3Key)
                .s3Url(destination.objectUrl(s3Key))
                .provider(destination.provider())
                .title(resolveTitle(title, file.getOriginalFilename()))
                .description(description)
                .build();

        Photo saved = photoRepository.save(photo);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PhotoResponse> listByUser(UserAccount user) {
        return photoRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PhotoResponse getById(UserAccount user, Long photoId) {
        return toResponse(findOwned(user, photoId));
    }

    @Transactional
    public PhotoResponse update(UserAccount user, Long photoId, String title, String description, MultipartFile file) {
        Photo photo = findOwned(user, photoId);

        if (title != null) {
            photo.setTitle(title);
        }
        if (description != null) {
            photo.setDescription(description);
        }

        if (file != null && !file.isEmpty()) {
            String contentType = validate(file);

            StorageStrategy strategy = strategyFor(photo);

            String previousKey = photo.getS3Key();
            String extension = extensionOf(file.getOriginalFilename());
            String storedFilename = UUID.randomUUID() + "." + extension;
            String newKey = buildKey(user.getId(), storedFilename);

            transfer(strategy, file, newKey, contentType);

            photo.setOriginalFilename(file.getOriginalFilename());
            photo.setStoredFilename(storedFilename);
            photo.setContentType(contentType);
            photo.setSizeBytes(file.getSize());
            photo.setS3Key(newKey);
            photo.setS3Url(strategy.objectUrl(newKey));
            photo.setProvider(strategy.provider());

            deleteQuietly(strategy, previousKey);
        }

        Photo saved = photoRepository.save(photo);
        return toResponse(saved);
    }

    @Transactional
    public void delete(UserAccount user, Long photoId) {
        Photo photo = findOwned(user, photoId);
        String s3Key = photo.getS3Key();
        StorageStrategy strategy = strategyFor(photo);

        photoRepository.delete(photo);
        photoRepository.flush();

        strategy.delete(s3Key);
    }

    private Photo findOwned(UserAccount user, Long photoId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> ResourceNotFoundException.of("Foto", photoId));

        if (!photo.belongsTo(user)) {
            throw new ForbiddenOperationException("Esta foto pertence a outro usuario.");
        }
        return photo;
    }

    private PhotoResponse toResponse(Photo photo) {
        return PhotoResponse.from(photo, presignQuietly(photo));
    }

    private String presignQuietly(Photo photo) {
        String provider = photo.resolveProvider();
        if (provider == null) {
            return null;
        }

        StorageStrategy strategy = storage.find(provider);
        if (strategy == null) {
            return null;
        }

        try {
            return strategy.generateUrl(photo.getS3Key());
        } catch (RuntimeException e) {
            LoggerUtil.logWarn(getClass(), "toResponse",
                    "Nao foi possivel assinar a URL da foto {} em '{}'; ela vai como indisponivel. Causa: {}",
                    photo.getId(), strategy.provider(), e.getMessage());
            return null;
        }
    }

    private String buildKey(Long userId, String storedFilename) {
        return "users/" + userId + "/photos/" + storedFilename;
    }

    private void transfer(StorageStrategy strategy, MultipartFile file, String s3Key, String contentType) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new StorageException("Nao foi possivel ler o arquivo enviado.", e);
        }
        strategy.upload(s3Key, contentType, bytes);
    }

    private void deleteQuietly(StorageStrategy strategy, String s3Key) {
        try {
            strategy.delete(s3Key);
        } catch (RuntimeException e) {
            LoggerUtil.logWarn(getClass(), "update",
                    "Objeto antigo nao pode ser removido de '{}' (key={}); ficara orfao no bucket. Causa: {}",
                    strategy.provider(), s3Key, e.getMessage());
        }
    }

    private String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Nenhum arquivo foi enviado.");
        }
        String contentType = detectContentType(file);
        if (contentType == null) {
            throw new InvalidFileException(
                    "O conteudo do arquivo nao e uma imagem valida. Aceitos: image/jpeg, image/png, image/gif, image/webp.");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_TYPES.get(contentType).contains(extension)) {
            throw new InvalidFileException(
                    "A extensao ." + extension + " nao corresponde ao conteudo do arquivo (" + contentType + ").");
        }
        return contentType;
    }

    private String detectContentType(MultipartFile file) {
        byte[] header = new byte[12];
        int read;
        try (InputStream content = file.getInputStream()) {
            read = content.readNBytes(header, 0, header.length);
        } catch (IOException e) {
            throw new StorageException("Nao foi possivel ler o arquivo enviado.", e);
        }

        if (read >= 8 && matches(header, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (read >= 3 && matches(header, 0, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        // GIF87a e GIF89a
        if (read >= 6 && matches(header, 0, 'G', 'I', 'F', '8') && (header[4] == '7' || header[4] == '9')
                && header[5] == 'a') {
            return "image/gif";
        }
        // RIFF....WEBP
        if (read >= 12 && matches(header, 0, 'R', 'I', 'F', 'F') && matches(header, 8, 'W', 'E', 'B', 'P')) {
            return "image/webp";
        }
        return null;
    }

    private boolean matches(byte[] data, int offset, int... expected) {
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            throw new InvalidFileException("O arquivo enviado nao possui nome.");
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new InvalidFileException("O arquivo enviado nao possui extensao.");
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveTitle(String title, String originalFilename) {
        if (title != null && !title.isBlank()) {
            return title;
        }
        int dot = originalFilename.lastIndexOf('.');
        return dot > 0 ? originalFilename.substring(0, dot) : originalFilename;
    }
}