package lk.sliit.electronest.vendor.service;

import lk.sliit.electronest.catalog.controller.ProductController;
import lk.sliit.electronest.catalog.service.ProductService;
import lk.sliit.electronest.common.model.AccountStatus;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.order.repository.OrderRepository;
import lk.sliit.electronest.order.service.OrderService;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.VendorStatus;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SellerAccessTest {
    @Test
    void lifecycleControlsProductAndOrderAccessDespiteStaleVendorRole() {
        var vendors = mock(VendorRepository.class);
        var products = mock(ProductService.class);
        var orders = mock(OrderRepository.class);
        var productController = new ProductController(products, vendors);
        var orderService = new OrderService(orders, products, vendors,
                mock(lk.sliit.electronest.payment.service.PaymentWorkflowService.class));
        User user = new User();
        user.setId(20L);
        user.setRole(Role.VENDOR);
        user.setStatus(AccountStatus.ACTIVE);
        Vendor vendor = new Vendor();
        vendor.setId(7L);
        vendor.setUser(user);
        when(vendors.findByUser_Id(20L)).thenReturn(Optional.of(vendor));
        var principal = new CustomUserDetails(user);
        var order = new lk.sliit.electronest.order.model.Order();
        when(orders.findForUpdate(1L)).thenReturn(Optional.of(order));
        for (VendorStatus status : VendorStatus.values()) {
            vendor.setStatus(status);
            if (status == VendorStatus.APPROVED) continue;
            assertThrows(AccessDeniedException.class, () -> productController.deleteProduct(1L, principal));
            assertThrows(AccessDeniedException.class, () -> orderService.getOrderQueueForVendor(user));
            assertThrows(AccessDeniedException.class, () -> orderService.updateFulfilmentStatus(
                    1L, user, lk.sliit.electronest.order.model.OrderStatus.DELIVERED, false));
        }
        verifyNoInteractions(products);
        verify(orders, never()).save(any());
        verify(orders, never()).findByVendorId(any());
        vendor.setStatus(VendorStatus.APPROVED);
        productController.deleteProduct(1L, principal);
        orderService.getOrderQueueForVendor(user);
        verify(products).deleteOwnedProduct(1L, 7L);
        verify(orders).findByVendorId(20L);
        when(vendors.findByUser_Id(20L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> productController.deleteProduct(1L, principal));
        assertThrows(AccessDeniedException.class, () -> orderService.getOrderQueueForVendor(user));
    }
}
