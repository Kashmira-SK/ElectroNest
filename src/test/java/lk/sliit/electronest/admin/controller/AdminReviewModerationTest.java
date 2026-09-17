package lk.sliit.electronest.admin.controller;

import lk.sliit.electronest.admin.dto.AdminReviewView;
import lk.sliit.electronest.admin.service.ReportService;
import lk.sliit.electronest.admin.service.UserService;
import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.service.ProductService;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.search.model.Review;
import lk.sliit.electronest.search.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminReviewModerationTest {
    private final ReviewService reviews = mock(ReviewService.class);
    private final ProductService products = mock(ProductService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AdminController controller = new AdminController(
            mock(UserService.class), mock(ReportService.class), reviews, products, users);

    @Test
    void filtersVisibilityAndSearchWhileKeepingGlobalCounts() {
        Review visible = review("ACTIVE");
        Review hidden = review("HIDDEN");
        when(reviews.getAllReviews()).thenReturn(List.of(visible, hidden));
        Product product = new Product();
        product.setName("Keyboard");
        when(products.getProductById(2L)).thenReturn(product);
        User customer = new User();
        customer.setFullName("Customer");
        when(users.findById(3L)).thenReturn(Optional.of(customer));
        var model = new ExtendedModelMap();
        assertEquals("admin/reviews", controller.reviews("HIDDEN", " KEYBOARD ", model));
        assertEquals(2, model.get("totalReviews"));
        assertEquals(1L, model.get("hiddenReviews"));
        assertEquals(1L, model.get("visibleReviews"));
        var results = (List<AdminReviewView>) model.get("reviews");
        assertEquals(1, results.size());
        assertSame(hidden, results.get(0).review());
    }

    @Test
    void moderationReusesServiceAndPreservesFilters() {
        var redirect = new RedirectAttributesModelMap();
        assertEquals("redirect:/admin/reviews",
                controller.moderateReview(1L, "hidden", "ACTIVE", "keyboard", redirect));
        verify(reviews).moderateReview(1L, "HIDDEN");
        assertEquals("ACTIVE", redirect.get("visibility"));
        assertEquals("keyboard", redirect.get("keyword"));
        assertTrue(redirect.getFlashAttributes().containsKey("successMessage"));
    }

    @Test
    void missingReviewReturnsFeedbackInsteadOfErrorPage() {
        when(reviews.moderateReview(1L, "ACTIVE")).thenThrow(new java.util.NoSuchElementException("Review not found"));
        var redirect = new RedirectAttributesModelMap();
        assertEquals("redirect:/admin/reviews",
                controller.moderateReview(1L, "ACTIVE", "invalid", "", redirect));
        assertEquals("ALL", redirect.get("visibility"));
        assertEquals("Review not found", redirect.getFlashAttributes().get("errorMessage"));
    }

    private Review review(String status) {
        Review review = new Review();
        review.setProductId(2L);
        review.setCustomerId(3L);
        review.setStatus(status);
        return review;
    }
}
