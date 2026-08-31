package com.familyti.product.repository;

import com.familyti.product.model.Photo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PhotoRepository extends JpaRepository<Photo, Long> {
    List<Photo> findByUserIdOrderByCreatedAtDesc(Long userId);
}