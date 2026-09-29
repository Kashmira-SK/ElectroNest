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
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/products/" + productId;
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
