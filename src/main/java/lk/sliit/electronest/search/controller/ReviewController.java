package lk.sliit.electronest.search.controller;

import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.search.dto.ReviewRequest;
import lk.sliit.electronest.search.model.Review;
import lk.sliit.electronest.search.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<Review>> productReviews(
            @PathVariable Long productId) {

        return ResponseEntity.ok(
                reviewService.getProductReviews(productId)
        );
    }

    @GetMapping("/product/{productId}/summary")
    public ResponseEntity<Map<String, Object>> productReviewSummary(
            @PathVariable Long productId) {

        return ResponseEntity.ok(
                Map.of(
                        "productId", productId,
                        "averageRating",
                        reviewService.getAverageRating(productId),
                        "reviewCount",
                        reviewService.getReviewCount(productId)
                )
        );
    }

    @GetMapping("/my-reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<Review>> myReviews(
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return ResponseEntity.ok(
                reviewService.getMyReviews(currentUser.getUser())
        );
    }

    @PostMapping("/product/{productId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Review> createReview(
            @PathVariable Long productId,
            @RequestBody ReviewRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return ResponseEntity.ok(
                reviewService.createReview(
                        productId,
                        request,
                        currentUser.getUser()
                )
        );
    }

    @PutMapping("/{reviewId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Review> updateReview(
            @PathVariable Long reviewId,
            @RequestBody ReviewRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return ResponseEntity.ok(
                reviewService.updateReview(
                        reviewId,
                        request,
                        currentUser.getUser()
                )
        );
    }

    @DeleteMapping("/{reviewId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long reviewId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        reviewService.deleteOwnReview(
                reviewId,
                currentUser.getUser()
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Review>> allReviews() {
        return ResponseEntity.ok(reviewService.getAllReviews());
    }

    @PatchMapping("/admin/{reviewId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Review> moderateReview(
            @PathVariable Long reviewId,
            @RequestParam String status) {

        return ResponseEntity.ok(
                reviewService.moderateReview(
                        reviewId,
                        status.toUpperCase()
                )
        );
    }
}
