package lk.sliit.electronest.search.service;

import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.search.dto.ReviewRequest;
import lk.sliit.electronest.search.model.Review;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReviewPhotoService {
    private final ReviewService reviews;
    private final ReviewImageStorageService images;

    public ReviewPhotoService(ReviewService reviews, ReviewImageStorageService images) {
        this.reviews = reviews;
        this.images = images;
    }

    // Keep orchestration outside the ReviewService transaction: its return confirms commit.
    public Review create(Long productId, int rating, String text, MultipartFile photo, User customer) {
        String stored = upload(photo);
        try {
            return reviews.createReview(productId, new ReviewRequest(rating, text, stored), customer);
        } catch (RuntimeException ex) {
            images.deleteUrlQuietly(stored);
            throw ex;
        }
    }

    public Review update(Long productId, Long reviewId, int rating, String text, MultipartFile photo, User customer) {
        Review previous = reviews.getOwnedReview(reviewId, customer);
        if (!previous.getProductId().equals(productId)) {
            throw new IllegalArgumentException("Review does not belong to this product");
        }
        String old = previous.getPhotoPath();
        String stored = upload(photo);
        Review updated;
        try {
            updated = reviews.updateReview(reviewId, new ReviewRequest(rating, text, stored == null ? old : stored), customer);
        } catch (RuntimeException ex) {
            images.deleteUrlQuietly(stored);
            throw ex;
        }
        if (stored != null) images.deleteUrlQuietly(old);
        return updated;
    }

    public void delete(Long reviewId, User customer) {
        String old = reviews.getOwnedReview(reviewId, customer).getPhotoPath();
        reviews.deleteOwnReview(reviewId, customer);
        images.deleteUrlQuietly(old);
    }

    private String upload(MultipartFile photo) {
        return photo == null || photo.isEmpty() ? null : images.store(photo);
    }
}
