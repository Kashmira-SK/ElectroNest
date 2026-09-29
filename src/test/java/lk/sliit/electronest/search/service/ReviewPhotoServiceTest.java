package lk.sliit.electronest.search.service;

import lk.sliit.electronest.catalog.service.ProductService;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.order.model.Order;
import lk.sliit.electronest.order.model.OrderLineItem;
import lk.sliit.electronest.order.model.OrderStatus;
import lk.sliit.electronest.order.repository.OrderRepository;
import lk.sliit.electronest.search.model.Review;
import lk.sliit.electronest.search.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewPhotoServiceTest {
    @TempDir Path directory;
    ReviewRepository repository = mock(ReviewRepository.class);
    ProductService products = mock(ProductService.class);
    OrderRepository orders = mock(OrderRepository.class);
    ReviewImageStorageService storage;
    ReviewPhotoService photos;
    User customer = new User();

    @BeforeEach void setup() {
        storage = new ReviewImageStorageService(directory);
        photos = new ReviewPhotoService(new ReviewService(repository, products, orders), storage);
        customer.setId(7L);
        customer.setRole(Role.CUSTOMER);
        when(repository.save(any(Review.class))).thenAnswer(call -> call.getArgument(0));
        Order order = new Order();
        order.setStatus(OrderStatus.DELIVERED);
        OrderLineItem item = new OrderLineItem();
        item.setProductId(11L);
        order.addLineItem(item);
        when(orders.findByCustomer_Id(7L)).thenReturn(List.of(order));
    }

    MockMultipartFile png() {
        return new MockMultipartFile("photo", "photo.png", "image/png", ReviewImageStorageServiceTest.PNG);
    }

    Review existing(String photo) {
        Review review = new Review();
        review.setId(3L);
        review.setCustomerId(7L);
        review.setProductId(11L);
        review.setPhotoPath(photo);
        review.setStatus("HIDDEN");
        review.setVerified(true);
        when(repository.findById(3L)).thenReturn(Optional.of(review));
        return review;
    }

    Path file(String url) { return directory.resolve(url.substring(ReviewImageStorageService.URL_PREFIX.length())); }
    long files() throws Exception { try (var stream = Files.list(directory)) { return stream.count(); } }

    @Test void createWithoutPhoto() throws Exception {
        Review review = photos.create(11L, 5, "Good", null, customer);
        assertNull(review.getPhotoPath());
        assertTrue(review.isVerified());
        assertEquals("ACTIVE", review.getStatus());
        assertEquals(0, files());
    }

    @Test void createWithPhotoPersistsOnlyManagedPath() {
        Review review = photos.create(11L, 5, "Good", png(), customer);
        assertTrue(review.getPhotoPath().startsWith(ReviewImageStorageService.URL_PREFIX));
        assertTrue(Files.exists(file(review.getPhotoPath())));
    }

    @Test void invalidImageNeverSavesReview() {
        assertThrows(IllegalArgumentException.class, () -> photos.create(11L, 5, "Good",
                new MockMultipartFile("photo", "bad.gif", "image/gif", new byte[]{1}), customer));
        verify(repository, never()).save(any());
    }

    @Test void editingWithoutReplacementPreservesPhotoAndModeration() {
        String old = storage.store(png());
        existing(old);
        Review review = photos.update(11L, 3L, 4, "Edited", new MockMultipartFile("photo", new byte[0]), customer);
        assertEquals(old, review.getPhotoPath());
        assertTrue(Files.exists(file(old)));
        assertEquals("HIDDEN", review.getStatus());
        assertTrue(review.isVerified());
    }

    @Test void replacementSavesNewPhotoBeforeDeletingOld() {
        String old = storage.store(png());
        existing(old);
        when(repository.save(any(Review.class))).thenAnswer(call -> {
            assertTrue(Files.exists(file(old)));
            assertTrue(Files.exists(file(((Review) call.getArgument(0)).getPhotoPath())));
            return call.getArgument(0);
        });
        Review review = photos.update(11L, 3L, 4, "Edited", png(), customer);
        assertNotEquals(old, review.getPhotoPath());
        assertFalse(Files.exists(file(old)));
        assertTrue(Files.exists(file(review.getPhotoPath())));
    }

    @Test void failedUploadPreservesOldPhoto() {
        String old = storage.store(png());
        Review review = existing(old);
        assertThrows(IllegalArgumentException.class, () -> photos.update(11L, 3L, 4, "Edited",
                new MockMultipartFile("photo", "bad.png", "image/png", new byte[]{1}), customer));
        assertEquals(old, review.getPhotoPath());
        assertTrue(Files.exists(file(old)));
        verify(repository, never()).save(any());
    }

    @Test void failedSaveCleansNewUploadAndPreservesOldFile() throws Exception {
        String old = storage.store(png());
        existing(old);
        when(repository.save(any(Review.class))).thenThrow(new IllegalStateException("Save failed"));
        assertThrows(IllegalStateException.class, () -> photos.update(11L, 3L, 4, "Edited", png(), customer));
        assertTrue(Files.exists(file(old)));
        assertEquals(1, files());
    }

    @Test void ownershipAndProductMismatchRejectBeforeUploading() throws Exception {
        Review review = existing(null);
        review.setCustomerId(99L);
        assertThrows(SecurityException.class, () -> photos.update(11L, 3L, 4, "Edited", png(), customer));
        review.setCustomerId(7L);
        assertThrows(IllegalArgumentException.class, () -> photos.update(12L, 3L, 4, "Edited", png(), customer));
        customer.setRole(Role.VENDOR);
        assertThrows(SecurityException.class, () -> photos.update(11L, 3L, 4, "Edited", png(), customer));
        assertEquals(0, files());
        verify(repository, never()).save(any());
    }

    @Test void purchaseDuplicateRoleAndRatingValidationStillApplyAndCleanUploads() throws Exception {
        when(orders.findByCustomer_Id(7L)).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> photos.create(11L, 5, "Good", png(), customer));
        when(repository.findByCustomerIdAndProductId(7L, 11L)).thenReturn(Optional.of(new Review()));
        assertThrows(IllegalStateException.class, () -> photos.create(11L, 5, "Good", png(), customer));
        assertThrows(IllegalArgumentException.class, () -> photos.create(11L, 6, "Good", png(), customer));
        customer.setRole(Role.VENDOR);
        assertThrows(SecurityException.class, () -> photos.create(11L, 5, "Good", png(), customer));
        assertEquals(0, files());
        verify(repository, never()).save(any());
    }

    @Test void deletionCleansFileOnlyAfterSuccessfulReviewDeletion() {
        String old = storage.store(png());
        Review review = existing(old);
        doAnswer(call -> { assertTrue(Files.exists(file(old))); return null; }).when(repository).delete(review);
        photos.delete(3L, customer);
        assertFalse(Files.exists(file(old)));
    }

    @Test void externalPhotoCanBeReplacedWithoutDeletingOutsideStorage() throws Exception {
        existing("https://example.com/photo.png");
        Review review = photos.update(11L, 3L, 4, "Edited", png(), customer);
        assertTrue(Files.exists(file(review.getPhotoPath())));
        assertEquals(1, files());
    }
}
