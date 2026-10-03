package lk.sliit.electronest.catalog.controller;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.dto.ProductForm;
import org.springframework.validation.BeanPropertyBindingResult;
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
        ProductForm form = form(1L, "Product", BigDecimal.TEN);
        String result = new ProductMediaViewController(products, vendors, storage).saveProduct(
                form, new BeanPropertyBindingResult(form, "product"), null, principal, redirect);
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
        ProductForm submitted = form(null, "My listing", BigDecimal.ZERO);
        new ProductMediaViewController(products, vendors, storage).saveProduct(
                submitted, new BeanPropertyBindingResult(submitted, "product"), null, principal, redirect);
        var form = (ProductForm) redirect.getFlashAttributes().get("product");
        assertEquals("My listing", form.getName());
        verifyNoInteractions(products, storage);
    }

    private Product editableProduct(List<String> images) {
        seller();
        when(vendors.getVendorForUser(20L)).thenReturn(vendor);
        Product product = new Product();
        product.setId(1L);
        product.setVendorId(7L);
        product.setImageUrls(images);
        product.setImageUrl(images.get(0));
        when(products.getProductById(1L)).thenReturn(product);
        return product;
    }

    private String save(List<String> removals, org.springframework.web.multipart.MultipartFile[] uploads) {
        ProductForm form = form(1L, "Product", BigDecimal.TEN);
        form.setRemoveImages(removals);
        return new ProductMediaViewController(products, vendors, storage).saveProduct(
                form, new BeanPropertyBindingResult(form, "product"), uploads,
                new CustomUserDetails(user), new RedirectAttributesModelMap());
    }

    private ProductForm form(Long id, String name, BigDecimal price) {
        ProductForm form = new ProductForm();
        form.setId(id);
        form.setName(name);
        form.setBrand("Brand");
        form.setCategory("Category");
        form.setDescription("Text");
        form.setPrice(price);
        form.setStockQuantity(4);
        return form;
    }

    @Test void removingThumbnailPromotesNextImageAndDeletesOnlyRemovedFileAfterSave() {
        Product product = editableProduct(List.of("/images/products-upload/first.jpg", "https://example.com/second.png"));
        when(products.createProduct(product)).thenReturn(product);
        assertEquals("redirect:/vendor/products", save(List.of(product.getImageUrl()), null));
        assertEquals(List.of("https://example.com/second.png"), product.getImageUrls());
        assertEquals("https://example.com/second.png", product.getImageUrl());
        var order = inOrder(products, storage);
        order.verify(products).createProduct(product);
        order.verify(storage).deleteUrlQuietly("/images/products-upload/first.jpg");
        verifyNoMoreInteractions(storage);
    }

    @Test void removingNonThumbnailPreservesOrder() {
        Product product = editableProduct(List.of("first", "second", "third"));
        when(products.createProduct(product)).thenReturn(product);
        save(List.of("second"), null);
        assertEquals(List.of("first", "third"), product.getImageUrls());
        assertEquals("first", product.getImageUrl());
        verify(storage).deleteUrlQuietly("second");
        verifyNoMoreInteractions(storage);
    }

    @Test void removingLegacyThumbnailLeavesEmptyGallery() {
        Product product = editableProduct(List.of("https://example.com/only.jpg"));
        product.setImageUrls(List.of());
        when(products.createProduct(product)).thenReturn(product);
        save(List.of(product.getImageUrl()), null);
        assertNull(product.getImageUrl());
        assertTrue(product.getImageUrls().isEmpty());
    }

    @Test void anotherProductsImageCannotBeRemoved() {
        editableProduct(List.of("owned"));
        assertEquals("redirect:/vendor/products/1/edit", save(List.of("not-owned"), null));
        verify(products, never()).createProduct(any());
        verifyNoInteractions(storage);
    }

    @Test void anotherVendorCannotRemoveImages() {
        Product product = editableProduct(List.of("owned"));
        product.setVendorId(99L);
        assertEquals("redirect:/vendor/products/1/edit", save(List.of("owned"), null));
        assertEquals(List.of("owned"), product.getImageUrls());
        verify(products, never()).createProduct(any());
        verifyNoInteractions(storage);
    }

    @Test void unapprovedVendorCannotRemoveImages() {
        editableProduct(List.of("owned"));
        vendor.setStatus(VendorStatus.PENDING);
        assertEquals("redirect:/vendor/products/1/edit", save(List.of("owned"), null));
        verify(products, never()).createProduct(any());
        verifyNoInteractions(storage);
    }

    @Test void failedSaveDoesNotDeleteExistingFile() {
        Product product = editableProduct(List.of("owned"));
        when(products.createProduct(product)).thenThrow(new IllegalStateException("Save failed"));
        assertEquals("redirect:/vendor/products/1/edit", save(List.of("owned"), null));
        verifyNoInteractions(storage);
    }

    @Test void unexpectedSaveFailureDoesNotExposeExceptionText() {
        Product product = editableProduct(List.of("owned"));
        when(products.createProduct(product)).thenThrow(new IllegalStateException("java.sql.Exception: internal detail"));
        ProductForm form = form(1L, "Product", BigDecimal.TEN);
        var redirect = new RedirectAttributesModelMap();
        new ProductMediaViewController(products, vendors, storage).saveProduct(
                form, new BeanPropertyBindingResult(form, "product"), null,
                new CustomUserDetails(user), redirect);
        var errors = (org.springframework.validation.BindingResult) redirect.getFlashAttributes()
                .get(org.springframework.validation.BindingResult.MODEL_KEY_PREFIX + "product");
        assertEquals("Could not save the product. Please try again.", errors.getGlobalError().getDefaultMessage());
        assertFalse(redirect.getFlashAttributes().containsKey("errorMessage"));
    }

    @Test void uploadsStillReplaceGallery() {
        Product product = editableProduct(List.of("first", "second"));
        var upload = new org.springframework.mock.web.MockMultipartFile("images", "new.png", "image/png", new byte[]{1});
        when(storage.store(upload)).thenReturn("new");
        when(products.createProduct(product)).thenReturn(product);
        save(List.of(), new org.springframework.web.multipart.MultipartFile[]{upload});
        assertEquals(List.of("new"), product.getImageUrls());
        assertEquals("new", product.getImageUrl());
        verify(storage).deleteUrlQuietly("first");
        verify(storage).deleteUrlQuietly("second");
    }
}
