package lk.sliit.electronest.search.service;

import lk.sliit.electronest.catalog.service.ProductService;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.order.model.Order;
import lk.sliit.electronest.order.model.OrderStatus;
import lk.sliit.electronest.order.repository.OrderRepository;
import lk.sliit.electronest.search.dto.ReviewRequest;
import lk.sliit.electronest.search.model.Review;
import lk.sliit.electronest.search.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductService productService;
    private final OrderRepository orderRepository;

    public ReviewService(ReviewRepository reviewRepository,
                         ProductService productService,
                         OrderRepository orderRepository) {
        this.reviewRepository = reviewRepository;
        this.productService = productService;
        this.orderRepository = orderRepository;
    }

    public List<Review> getProductReviews(Long productId) {
        productService.getProductById(productId);

        return reviewRepository
                .findByProductIdAndStatusOrderByCreatedAtDesc(
                        productId,
                        "ACTIVE"
                );
    }

    public List<Review> getMyReviews(User customer) {
        return reviewRepository
                .findByCustomerIdOrderByCreatedAtDesc(customer.getId());
    }

    public double getAverageRating(Long productId) {
        List<Review> reviews = getProductReviews(productId);

        if (reviews.isEmpty()) {
            return 0.0;
        }

        return reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);
    }

    public long getReviewCount(Long productId) {
        return getProductReviews(productId).size();
    }

    @Transactional
    public Review createReview(Long productId,
                               ReviewRequest request,
                               User customer) {
        if (customer.getRole() != Role.CUSTOMER) {
            throw new SecurityException(
                    "Only customers can create reviews"
            );
        }

        productService.getProductById(productId);
        validate(request);

        reviewRepository
                .findByCustomerIdAndProductId(customer.getId(), productId)
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "You have already reviewed this product"
                    );
                });

        if (!hasDeliveredPurchase(customer.getId(), productId)) {
            throw new IllegalStateException(
                    "You can review this product after a delivered purchase"
            );
        }

        Review review = new Review();

        review.setProductId(productId);
        review.setCustomerId(customer.getId());
        review.setRating(request.rating());
        review.setReviewText(clean(request.reviewText()));
        review.setPhotoPath(clean(request.photoPath()));
        review.setVerified(true);
        review.setStatus("ACTIVE");

        return reviewRepository.save(review);
    }

    public boolean hasReviewed(Long productId, User customer) {
        return reviewRepository
                .findByCustomerIdAndProductId(customer.getId(), productId)
                .isPresent();
    }

    public boolean isEligibleToReview(Long productId, User customer) {
        if (customer == null || customer.getRole() != Role.CUSTOMER) {
            return false;
        }

        productService.getProductById(productId);

        return !hasReviewed(productId, customer)
                && hasDeliveredPurchase(customer.getId(), productId);
    }

    @Transactional
    public Review updateReview(Long reviewId,
                               ReviewRequest request,
                               User customer) {
        Review review = getReview(reviewId);

        if (!review.getCustomerId().equals(customer.getId())) {
            throw new SecurityException(
                    "You cannot modify another customer's review"
            );
        }

        validate(request);

        review.setRating(request.rating());
        review.setReviewText(clean(request.reviewText()));
        review.setPhotoPath(clean(request.photoPath()));

        return reviewRepository.save(review);
    }

    @Transactional
    public void deleteOwnReview(Long reviewId, User customer) {
        Review review = getReview(reviewId);

        if (!review.getCustomerId().equals(customer.getId())) {
            throw new SecurityException(
                    "You cannot delete another customer's review"
            );
        }

        reviewRepository.delete(review);
    }

    public List<Review> getAllReviews() {
        return reviewRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public Review moderateReview(Long reviewId, String status) {
        if (!"ACTIVE".equals(status) && !"HIDDEN".equals(status)) {
            throw new IllegalArgumentException(
                    "Review status must be ACTIVE or HIDDEN"
            );
        }

        Review review = getReview(reviewId);
        review.setStatus(status);

        return reviewRepository.save(review);
    }

    private Review getReview(Long id) {
        return reviewRepository.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException("Review not found"));
    }

    private boolean hasDeliveredPurchase(Long customerId,
                                         Long productId) {
        List<Order> orders =
                orderRepository.findByCustomer_Id(customerId);

        return orders.stream()
                .flatMap(order -> order.getLineItems().stream())
                .filter(item -> item.effectiveStatus() == OrderStatus.DELIVERED)
                .anyMatch(item ->
                        item.getProductId().equals(productId));
    }

    private void validate(ReviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Review details are required"
            );
        }

        if (request.rating() < 1 || request.rating() > 5) {
            throw new IllegalArgumentException(
                    "Rating must be between 1 and 5"
            );
        }

        if (request.reviewText() != null &&
                request.reviewText().length() > 2000) {
            throw new IllegalArgumentException(
                    "Review text cannot exceed 2000 characters"
            );
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}
