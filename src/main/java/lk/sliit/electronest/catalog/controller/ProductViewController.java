package lk.sliit.electronest.catalog.controller;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.service.ProductService;
import lk.sliit.electronest.common.model.AccountStatus;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.search.dto.ReviewDisplay;
import lk.sliit.electronest.search.model.Review;
import lk.sliit.electronest.search.service.ReviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lk.sliit.electronest.vendor.service.VendorService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.LinkedHashSet;
import java.util.List;

@Controller
public class ProductViewController {
    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NOT_FOUND)
    public String unavailableProduct() {
        return "catalog/product-unavailable";
    }

    private final ProductService productService;
    private final ReviewService reviewService;
    private final UserRepository userRepository;
    private final VendorService vendorService;

    public ProductViewController(
            ProductService productService,
            ReviewService reviewService,
            UserRepository userRepository,
            VendorService vendorService) {

        this.productService = productService;
        this.reviewService = reviewService;
        this.userRepository = userRepository;
        this.vendorService = vendorService;
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
        boolean suspended = currentUser != null
                && currentUser.getUser().getStatus() == AccountStatus.SUSPENDED;
        boolean alreadyReviewed = customer
                && reviewService.hasReviewed(id, currentUser.getUser());
        boolean eligible = customer && !suspended
                && reviewService.isEligibleToReview(id, currentUser.getUser());

        model.addAttribute("product", product);
        model.addAttribute("reviews", reviewDisplays);
        model.addAttribute("averageRating", averageRating);
        model.addAttribute("reviewCount", reviews.size());
        model.addAttribute("customerAccount", customer);
        model.addAttribute("alreadyReviewed", alreadyReviewed);
        model.addAttribute("reviewEligible", eligible);

        LinkedHashSet<String> gallery = new LinkedHashSet<>();

        if (product.getImageUrl() != null
                && !product.getImageUrl().isBlank()) {
            gallery.add(product.getImageUrl());
        }

        if (product.getImageUrls() != null) {
            product.getImageUrls().stream()
                    .filter(image ->
                            image != null
                                    && !image.isBlank())
                    .forEach(gallery::add);
        }

        model.addAttribute(
                "gallery",
                List.copyOf(gallery)
        );

        model.addAttribute(
                "loggedIn",
                currentUser != null
        );

        if (currentUser != null) {
            model.addAttribute(
                    "currentUser",
                    currentUser.getUser()
            );
        }

        try {
            model.addAttribute(
                    "seller",
                    vendorService.getVendorOrThrow(
                            product.getVendorId()
                    )
            );
        } catch (RuntimeException ex) {
            model.addAttribute("seller", null);
        }

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
