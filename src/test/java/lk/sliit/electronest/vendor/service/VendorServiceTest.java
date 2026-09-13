package lk.sliit.electronest.vendor.service;

import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.vendor.exception.InvalidVendorStatusTransitionException;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.VendorStatus;
import lk.sliit.electronest.vendor.model.dto.VendorProfileUpdateRequest;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorServiceTest {

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private UserRepository userRepository;

    private VendorService vendorService;

    @BeforeEach
    void setUp() {
        vendorService = new VendorService(vendorRepository, userRepository);
    }

    @Test
    void approvedVendorCanUpdateProfile() {
        Vendor vendor = vendor(VendorStatus.APPROVED);
        VendorProfileUpdateRequest request = request();

        when(vendorRepository.findByUser_Id(7L))
                .thenReturn(Optional.of(vendor));
        when(vendorRepository.save(any(Vendor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Vendor updated = vendorService.updateVendorDetails(7L, request);

        assertEquals("Updated Store", updated.getBusinessName());
        assertEquals("Updated Address", updated.getBusinessAddress());
        assertEquals("+94 77 123 4567", updated.getContactPhone());
        assertEquals(VendorStatus.APPROVED, updated.getStatus());
        verify(vendorRepository).save(vendor);
    }

    @Test
    void requestedInformationIsResubmittedForReview() {
        Vendor vendor = vendor(VendorStatus.INFO_REQUESTED);
        vendor.setRejectionReason("Please update the address");

        when(vendorRepository.findByUser_Id(7L))
                .thenReturn(Optional.of(vendor));
        when(vendorRepository.save(any(Vendor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Vendor updated = vendorService.updateVendorDetails(7L, request());

        assertEquals(VendorStatus.PENDING, updated.getStatus());
        assertNull(updated.getRejectionReason());
        verify(vendorRepository).save(vendor);
    }

    @Test
    void pendingVendorCannotEditDetails() {
        Vendor vendor = vendor(VendorStatus.PENDING);

        when(vendorRepository.findByUser_Id(7L))
                .thenReturn(Optional.of(vendor));

        assertThrows(
                InvalidVendorStatusTransitionException.class,
                () -> vendorService.updateVendorDetails(7L, request())
        );

        verify(vendorRepository, never()).save(any());
    }

    private Vendor vendor(VendorStatus status) {
        Vendor vendor = new Vendor();
        vendor.setBusinessName("Original Store");
        vendor.setBusinessAddress("Original Address");
        vendor.setContactPhone("0712345678");
        vendor.setStatus(status);
        return vendor;
    }

    private VendorProfileUpdateRequest request() {
        VendorProfileUpdateRequest request = new VendorProfileUpdateRequest();
        request.setBusinessName(" Updated Store ");
        request.setBusinessAddress(" Updated Address ");
        request.setContactPhone(" +94 77 123 4567 ");
        return request;
    }
}
