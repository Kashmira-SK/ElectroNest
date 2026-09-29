package lk.sliit.electronest.search.controller;

import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.search.model.Review;
import lk.sliit.electronest.search.service.ReviewImageStorageService;
import lk.sliit.electronest.search.service.ReviewPhotoService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ReviewPhotoController {
    private final ReviewPhotoService reviews;
    private final ReviewImageStorageService images;

    public ReviewPhotoController(ReviewPhotoService reviews, ReviewImageStorageService images) {
        this.reviews = reviews;
        this.images = images;
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping(value = "/api/review-photos/product/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Review create(@PathVariable Long productId, @RequestParam int rating,
                         @RequestParam(required = false) String reviewText,
                         @RequestParam(required = false) MultipartFile photo,
                         @AuthenticationPrincipal CustomUserDetails user) {
        return reviews.create(productId, rating, reviewText, photo, user.getUser());
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping(value = "/api/review-photos/product/{productId}/{reviewId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Review update(@PathVariable Long productId, @PathVariable Long reviewId, @RequestParam int rating,
                         @RequestParam(required = false) String reviewText,
                         @RequestParam(required = false) MultipartFile photo,
                         @AuthenticationPrincipal CustomUserDetails user) {
        return reviews.update(productId, reviewId, rating, reviewText, photo, user.getUser());
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @DeleteMapping("/api/review-photos/{reviewId}")
    public ResponseEntity<Void> delete(@PathVariable Long reviewId, @AuthenticationPrincipal CustomUserDetails user) {
        reviews.delete(reviewId, user.getUser());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/images/reviews-upload/{filename:.+}")
    public ResponseEntity<Resource> image(@PathVariable String filename) {
        Resource resource = images.load(filename);
        MediaType type = filename.endsWith(".png") ? MediaType.IMAGE_PNG
                : filename.endsWith(".webp") ? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type).header("Cache-Control", "public, max-age=86400")
                .header("X-Content-Type-Options", "nosniff").body(resource);
    }
}
