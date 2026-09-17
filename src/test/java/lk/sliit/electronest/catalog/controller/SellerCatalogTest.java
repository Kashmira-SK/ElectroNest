package lk.sliit.electronest.catalog.controller;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.service.ProductService;
import lk.sliit.electronest.catalog.service.ProductImageStorageService;
import lk.sliit.electronest.common.model.*;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.vendor.model.*;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import lk.sliit.electronest.vendor.service.VendorService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SellerCatalogTest {
    private final ProductService products = mock(ProductService.class);
    private final VendorService vendors = mock(VendorService.class);
    private final ProductImageStorageService storage = mock(ProductImageStorageService.class);
    private final User user = new User();
    private final Vendor vendor = new Vendor();

    private CustomUserDetails seller() {
        user.setId(20L);
        user.setRole(Role.VENDOR);
        user.setStatus(AccountStatus.ACTIVE);
        vendor.setId(7L);
        vendor.setUser(user);
        vendor.setStatus(VendorStatus.APPROVED);
        return new CustomUserDetails(user);
    }

    @Test
    void createEndpointCannotOverwriteAnExistingId() {
        var principal = seller();
        var repository = mock(VendorRepository.class);
        when(repository.findByUser_Id(20L)).thenReturn(Optional.of(vendor));
        Product payload = new Product();
        payload.setId(100L);
        assertThrows(ResponseStatusException.class,
                () -> new ProductController(products, repository).createProduct(payload, principal));
        verifyNoInteractions(products);
    }

    @Test
    void editingWithoutUploadsPreservesGallery() {
        var principal = seller();
        when(vendors.getVendorForUser(20L)).thenReturn(vendor);
        Product product = new Product();
        product.setId(1L);
        product.setVendorId(7L);
        product.setImageUrl("/images/first.jpg");
        product.setImageUrls(List.of("/images/first.jpg", "/images/second.jpg"));
        when(products.getProductById(1L)).thenReturn(product);
        when(products.createProduct(product)).thenReturn(product);
        var redirect = new RedirectAttributesModelMap();
        String result = new ProductMediaViewController(products, vendors, storage).saveProduct(
                1L, "Product", "Brand", "Category", "Text", BigDecimal.TEN, 4, "", null, principal, redirect);
        assertEquals("redirect:/vendor/products", result);
        assertEquals(List.of("/images/first.jpg", "/images/second.jpg"), product.getImageUrls());
        assertEquals("/images/first.jpg", product.getImageUrl());
        verifyNoInteractions(storage);
    }

    @Test
    void invalidSubmissionKeepsEnteredFields() {
        var principal = seller();
        when(vendors.getVendorForUser(20L)).thenReturn(vendor);
        var redirect = new RedirectAttributesModelMap();
        new ProductMediaViewController(products, vendors, storage).saveProduct(
                null, "My listing", "Brand", "Category", "Text", BigDecimal.ZERO, 4, "", null, principal, redirect);
        var form = (lk.sliit.electronest.catalog.dto.ProductForm) redirect.getFlashAttributes().get("product");
        assertEquals("My listing", form.getName());
        verifyNoInteractions(products, storage);
    }
}
