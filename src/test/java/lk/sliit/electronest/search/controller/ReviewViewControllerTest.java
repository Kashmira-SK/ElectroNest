package lk.sliit.electronest.search.controller;

import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.search.service.ReviewPhotoService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewViewControllerTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"java.sql.Exception: internal detail", "Cannot write /private/storage/photo.png"})
    void unknownFailuresStayFormLevelWithoutRawDetails(String message) {
        var service = mock(ReviewPhotoService.class);
        var customer = new User();
        var photo = new MockMultipartFile("photo", "photo.png", "image/png", new byte[]{1});
        when(service.create(1L, 4, "Draft", photo, customer)).thenThrow(new IllegalArgumentException(message));
        var redirect = new RedirectAttributesModelMap();

        assertEquals("redirect:/products/1", new ReviewViewController(service).createReview(
                1L, 4, "Draft", photo, new CustomUserDetails(customer), redirect));

        assertEquals("Could not publish your review. Please try again.", redirect.getFlashAttributes().get("errorMessage"));
        assertFalse(redirect.getFlashAttributes().containsKey("reviewPhotoError"));
    }
}
