package lk.sliit.electronest.search.controller;

import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.search.service.ReviewPhotoService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class ReviewViewController {

    private final ReviewPhotoService reviewService;

    public ReviewViewController(ReviewPhotoService reviewService) {
        this.reviewService = reviewService;
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/products/{productId}/reviews")
    public String createReview(
            @PathVariable Long productId,
            @RequestParam int rating,
            @RequestParam(required = false) String reviewText,
            @RequestParam(required = false) MultipartFile photo,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {
        try {
            reviewService.create(
                    productId, rating, reviewText, photo,
                    currentUser.getUser()
            );
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Your verified-purchase review has been published."
            );
        } catch (RuntimeException ex) {
            String photoError = photoError(ex, photo);
            if (photoError != null) {
                redirectAttributes.addFlashAttribute("reviewDraftRating", rating);
                redirectAttributes.addFlashAttribute("reviewDraftText", reviewText);
                redirectAttributes.addFlashAttribute("reviewPhotoError", photoError);
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", reviewError(ex));
            }
        }

        return "redirect:/products/" + productId;
    }

    private String photoError(RuntimeException ex, MultipartFile photo) {
        if (!(ex instanceof IllegalArgumentException) || photo == null || photo.isEmpty()) return null;
        // Only the existing storage service's known photo failures belong beside the file input.
        return switch (ex.getMessage() == null ? "" : ex.getMessage()) {
            case "Review photo is empty", "Review photo must be 8 MB or smaller",
                 "Review photos must be JPG, JPEG, PNG or WEBP with a matching content type",
                 "Review photo contents do not match the selected image format" -> ex.getMessage();
            case "Could not read the review photo. Select the file again.",
                 "Could not store the review photo. Select the file again." ->
                    "The review photo could not be uploaded. Please try again.";
            default -> null;
        };
    }

    private String reviewError(RuntimeException ex) {
        return switch (ex.getMessage() == null ? "" : ex.getMessage()) {
            case "Only customers can create reviews", "Product not found", "Review details are required",
                 "You have already reviewed this product", "You can review this product after a delivered purchase",
                 "Rating must be between 1 and 5", "Review text cannot exceed 2000 characters" -> ex.getMessage();
            default -> "Could not publish your review. Please try again.";
        };
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/products/{productId}/reviews/{reviewId}")
    public String updateReview(
            @PathVariable Long productId,
            @PathVariable Long reviewId,
            @RequestParam int rating,
            @RequestParam(required = false) String reviewText,
            @RequestParam(required = false) MultipartFile photo,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {
        try {
            reviewService.update(
                    productId, reviewId, rating, reviewText, photo,
                    currentUser.getUser()
            );
            redirectAttributes.addFlashAttribute("successMessage", "Review updated.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/products/" + productId;
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/products/{productId}/reviews/{reviewId}/delete")
    public String deleteReview(
            @PathVariable Long productId,
            @PathVariable Long reviewId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {
        try {
            reviewService.delete(reviewId, currentUser.getUser());
            redirectAttributes.addFlashAttribute("successMessage", "Review deleted.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/products/" + productId;
    }
}
