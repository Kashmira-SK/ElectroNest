package lk.sliit.electronest.common.controller;

import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.payment.service.SavedCardService;
import lk.sliit.electronest.vendor.service.VendorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountSettingsTest {
    private final UserRepository users = mock(UserRepository.class);
    private final AccountViewController controller = new AccountViewController(
            users, mock(VendorService.class), mock(SavedCardService.class));
    private final User user = new User();
    private final CustomUserDetails principal = new CustomUserDetails(user);

    @BeforeEach
    void setup() {
        user.setId(1L);
        user.setDeliveryName("Original");
        when(users.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void localPhoneAndDeliveryAreSavedToTheSharedAccount() {
        var redirect = new RedirectAttributesModelMap();
        assertEquals("redirect:/settings#delivery", controller.updateAddress(principal,
                " Recipient ", "077 123 4567", " Street ", null, "Colombo", null, "Sri Lanka", redirect));
        assertEquals("0771234567", user.getDeliveryPhone());
        assertEquals("Recipient", user.getDeliveryName());
        assertEquals("Street", user.getDeliveryAddressLine1());
        verify(users).save(user);
    }

    @Test
    void invalidAddressKeepsDraftWithoutPartiallyChangingAccount() {
        var redirect = new RedirectAttributesModelMap();
        controller.updateAddress(principal, "New recipient", "0771234567",
                "Street", null, "", null, "Sri Lanka", redirect);
        assertEquals("Original", user.getDeliveryName());
        verify(users, never()).save(any());
        assertTrue(redirect.getFlashAttributes().containsKey("addressDraft"));
    }

    @Test
    void oversizedProfileIsRejectedAndPreservedForCorrection() {
        var redirect = new RedirectAttributesModelMap();
        controller.updateProfile(principal, "x".repeat(101), "user@example.com", "0771234567", redirect);
        verify(users, never()).save(any());
        assertTrue(redirect.getFlashAttributes().containsKey("profileDraft"));
    }

    @Test
    void deletingAddressReturnsToDeliveryAndClearsSharedFields() {
        var redirect = new RedirectAttributesModelMap();
        assertEquals("redirect:/settings#delivery", controller.deleteAddress(principal, redirect));
        assertNull(user.getDeliveryName());
        assertNull(user.getDeliveryPhone());
        verify(users).save(user);
    }
}
