package lk.sliit.electronest.search.controller;

import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.search.dto.ReviewRequest;
import lk.sliit.electronest.search.service.ReviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReviewViewController {

    private final ReviewService reviewService;

    public ReviewViewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/products/{productId}/reviews")
    public String createReview(
            @PathVariable Long productId,
            @RequestParam int rating,
            @RequestParam(required = false) String reviewText,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {
        try {
            reviewService.createReview(
                    productId,
                    new ReviewRequest(rating, reviewText, null),
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
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {
        try {
            reviewService.updateReview(
                    reviewId,
                    new ReviewRequest(rating, reviewText, null),
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
            reviewService.deleteOwnReview(reviewId, currentUser.getUser());
            redirectAttributes.addFlashAttribute("successMessage", "Review deleted.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/products/" + productId;
    }
}
