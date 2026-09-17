package lk.sliit.electronest.vendor.service;

import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.model.AccountStatus;
import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.repository.ProductRepository;
import lk.sliit.electronest.order.repository.OrderRepository;
import java.util.List;
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

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    private VendorService vendorService;

    @BeforeEach
    void setUp() {
        vendorService = new VendorService(vendorRepository, userRepository, productRepository, orderRepository);
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

    @Test
    void approvalPromotesActiveCustomer() {
        Vendor vendor = reviewable(VendorStatus.PENDING, Role.CUSTOMER);
        vendorService.approveVendor(7L);
        assertEquals(VendorStatus.APPROVED, vendor.getStatus());
        assertEquals(Role.VENDOR, vendor.getUser().getRole());
        verify(userRepository).save(vendor.getUser());
    }

    @Test
    void approvalRequiresDocumentAndActiveCustomer() {
        Vendor vendor = reviewable(VendorStatus.PENDING, Role.CUSTOMER);
        vendor.setIdDocumentPath(null);
        assertThrows(InvalidVendorStatusTransitionException.class, () -> vendorService.approveVendor(7L));
        vendor.setIdDocumentPath("document.pdf");
        vendor.getUser().setStatus(AccountStatus.SUSPENDED);
        assertThrows(InvalidVendorStatusTransitionException.class, () -> vendorService.approveVendor(7L));
        vendor.getUser().setStatus(AccountStatus.ACTIVE);
        vendor.getUser().setRole(Role.ADMIN);
        assertThrows(InvalidVendorStatusTransitionException.class, () -> vendorService.approveVendor(7L));
        verify(userRepository, never()).save(any());
    }

    @Test
    void requestingInfoAndRejectingKeepCustomerRole() {
        Vendor vendor = reviewable(VendorStatus.PENDING, Role.CUSTOMER);
        vendorService.requestMoreInfo(7L, " Replace document ");
        assertEquals(VendorStatus.INFO_REQUESTED, vendor.getStatus());
        assertEquals("Replace document", vendor.getRejectionReason());
        assertEquals(Role.CUSTOMER, vendor.getUser().getRole());
        vendor.setStatus(VendorStatus.PENDING);
        vendorService.rejectVendor(7L, "Invalid registration");
        assertEquals(VendorStatus.REJECTED, vendor.getStatus());
        assertEquals(Role.CUSTOMER, vendor.getUser().getRole());
    }

    @Test
    void reactivationRestoresSellerRoleButNotDisabledAccount() {
        Vendor vendor = reviewable(VendorStatus.SUSPENDED, Role.CUSTOMER);
        vendor.getUser().setStatus(AccountStatus.DEACTIVATED);
        assertThrows(InvalidVendorStatusTransitionException.class, () -> vendorService.reactivateVendor(7L));
        vendor.getUser().setStatus(AccountStatus.ACTIVE);
        vendorService.reactivateVendor(7L);
        assertEquals(Role.VENDOR, vendor.getUser().getRole());
        assertEquals(VendorStatus.APPROVED, vendor.getStatus());
    }

    @Test
    void removalRevokesRoleAndDeletesEmptyProfile() {
        Vendor vendor = reviewable(VendorStatus.APPROVED, Role.VENDOR);
        vendorService.revokeVendor(7L);
        assertEquals(Role.CUSTOMER, vendor.getUser().getRole());
        verify(vendorRepository).delete(vendor);
        verify(userRepository).save(vendor.getUser());
    }

    @Test
    void removalPreservesSellerWithProductHistory() {
        Vendor vendor = reviewable(VendorStatus.APPROVED, Role.VENDOR);
        when(productRepository.findByVendorId(7L)).thenReturn(List.of(new Product()));
        assertThrows(InvalidVendorStatusTransitionException.class, () -> vendorService.revokeVendor(7L));
        assertEquals(Role.VENDOR, vendor.getUser().getRole());
        verify(vendorRepository, never()).delete(any());
    }

    @Test
    void removalChecksOrdersUsingUserIdNotVendorRecordId() {
        Vendor vendor = reviewable(VendorStatus.APPROVED, Role.VENDOR);
        vendor.getUser().setId(20L);
        when(orderRepository.findByVendorId(20L))
                .thenReturn(List.of(new lk.sliit.electronest.order.model.Order()));
        assertThrows(InvalidVendorStatusTransitionException.class, () -> vendorService.revokeVendor(7L));
        verify(vendorRepository, never()).delete(any());
        assertEquals(Role.VENDOR, vendor.getUser().getRole());
    }

    private Vendor reviewable(VendorStatus status, Role role) {
        Vendor vendor = vendor(status);
        User user = new User();
        user.setRole(role);
        user.setStatus(AccountStatus.ACTIVE);
        vendor.setUser(user);
        vendor.setIdDocumentPath("document.pdf");
        when(vendorRepository.findById(7L)).thenReturn(Optional.of(vendor));
        org.mockito.Mockito.lenient().when(vendorRepository.save(any(Vendor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return vendor;
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
