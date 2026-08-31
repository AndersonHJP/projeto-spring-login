package com.familyti.product.controller;

import com.familyti.product.dto.PhotoMetadataRequest;
import com.familyti.product.dto.PhotoResponse;
import com.familyti.product.model.UserAccount;
import com.familyti.product.service.PhotoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoResponse upload(@AuthenticationPrincipal UserAccount user,
                                @RequestPart("file") MultipartFile file,
                                @RequestParam(value = "title", required = false) String title,
                                @RequestParam(value = "description", required = false) String description) {
        return photoService.upload(user, file, title, description);
    }

    @GetMapping
    public List<PhotoResponse> list(@AuthenticationPrincipal UserAccount user) {
        return photoService.listByUser(user);
    }

    @GetMapping("/{id}")
    public PhotoResponse detail(@AuthenticationPrincipal UserAccount user, @PathVariable Long id) {
        return photoService.getById(user, id);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PhotoResponse updateWithFile(@AuthenticationPrincipal UserAccount user,
                                        @PathVariable Long id,
                                        @RequestPart(value = "file", required = false) MultipartFile file,
                                        @RequestParam(value = "title", required = false) String title,
                                        @RequestParam(value = "description", required = false) String description) {
        return photoService.update(user, id, title, description, file);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PhotoResponse updateMetadata(@AuthenticationPrincipal UserAccount user,
                                        @PathVariable Long id,
                                        @RequestBody @Valid PhotoMetadataRequest request) {
        return photoService.update(user, id, request.title(), request.description(), null);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserAccount user, @PathVariable Long id) {
        photoService.delete(user, id);
    }
}