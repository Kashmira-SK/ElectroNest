package lk.sliit.electronest.catalog.controller;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.service.ProductService;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.search.dto.ReviewDisplay;
import lk.sliit.electronest.search.model.Review;
import lk.sliit.electronest.search.service.ReviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class ProductViewController {

    private final ProductService productService;
    private final ReviewService reviewService;
    private final UserRepository userRepository;

    public ProductViewController(ProductService productService,
                                 ReviewService reviewService,
                                 UserRepository userRepository) {
        this.productService = productService;
        this.reviewService = reviewService;
        this.userRepository = userRepository;
    }

    @GetMapping("/products")
    public String showProducts(Model model) {
        List<Product> products = productService.getAllProducts();

        model.addAttribute("categories",
                products.stream()
                        .map(Product::getCategory)
                        .filter(value -> value != null && !value.isBlank())
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList());

        model.addAttribute("brands",
                products.stream()
                        .map(Product::getBrand)
                        .filter(value -> value != null && !value.isBlank())
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList());

        return "catalog/products";
    }

    @GetMapping("/products/{id}")
    public String showProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model) {
        Product product = productService.getProductById(id);
        List<Review> reviews = reviewService.getProductReviews(id);
        Long viewerId = currentUser == null
                ? null
                : currentUser.getUser().getId();

        List<ReviewDisplay> reviewDisplays = reviews.stream()
                .map(review -> toDisplay(review, viewerId))
                .toList();

        double averageRating = reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);

        boolean customer = currentUser != null
                && currentUser.getUser().getRole() == Role.CUSTOMER;
        boolean alreadyReviewed = customer
                && reviewService.hasReviewed(id, currentUser.getUser());
        boolean eligible = customer
                && reviewService.isEligibleToReview(id, currentUser.getUser());

        model.addAttribute("product", product);
        model.addAttribute("reviews", reviewDisplays);
        model.addAttribute("averageRating", averageRating);
        model.addAttribute("reviewCount", reviews.size());
        model.addAttribute("customerAccount", customer);
        model.addAttribute("alreadyReviewed", alreadyReviewed);
        model.addAttribute("reviewEligible", eligible);

        return "catalog/product-detail";
    }

    private ReviewDisplay toDisplay(Review review, Long viewerId) {
        String customerName = userRepository.findById(review.getCustomerId())
                .map(user -> user.getFullName())
                .orElse("ElectroNest customer");

        return new ReviewDisplay(
                review.getId(),
                review.getRating(),
                review.getReviewText(),
                review.isVerified(),
                review.getCreatedAt(),
                customerName,
                viewerId != null && viewerId.equals(review.getCustomerId())
        );
    }
}
