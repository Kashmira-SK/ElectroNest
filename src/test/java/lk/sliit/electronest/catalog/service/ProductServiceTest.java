package lk.sliit.electronest.catalog.service;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.repository.ProductRepository;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.VendorStatus;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private VendorRepository vendorRepository;

    @InjectMocks
    private ProductService productService;

    private Product sampleProduct;
    private Vendor approvedVendor;
    private Vendor pendingVendor;

    @BeforeEach
    void setUp() {
        approvedVendor = new Vendor();
        approvedVendor.setId(1L);
        approvedVendor.setStatus(VendorStatus.APPROVED);

        pendingVendor = new Vendor();
        pendingVendor.setId(2L);
        pendingVendor.setStatus(VendorStatus.PENDING);

        sampleProduct = new Product();
        sampleProduct.setId(1L);
        sampleProduct.setName("Test Laptop");
        sampleProduct.setBrand("TestBrand");
        sampleProduct.setCategory("Electronics");
        sampleProduct.setPrice(new BigDecimal("1000"));
        sampleProduct.setStockQuantity(10);
        sampleProduct.setVendorId(1L);
    }

    @Test
    void invalidProductFieldsFailBeforePersistence() {
        sampleProduct.setName("X");
        assertThrows(IllegalArgumentException.class, () -> productService.createProduct(sampleProduct));
        sampleProduct.setName("Valid name");
        sampleProduct.setBrand("X".repeat(256));
        assertThrows(IllegalArgumentException.class, () -> productService.createProduct(sampleProduct));
        sampleProduct.setBrand("Brand");
        sampleProduct.setPrice(new BigDecimal("0.001"));
        assertThrows(IllegalArgumentException.class, () -> productService.createProduct(sampleProduct));
        verifyNoInteractions(productRepository, vendorRepository);
    }

    @Test
    void bulkPriceCannotSilentlyRoundFractionalCents() {
        assertThrows(IllegalArgumentException.class,
                () -> productService.bulkUpdatePriceForVendor(List.of(1L), new BigDecimal("10.999"), 1L));
        verifyNoInteractions(productRepository);
    }

    @Test
    void createProduct_shouldSucceed_whenVendorIsApproved() {
        when(vendorRepository.findById(1L)).thenReturn(Optional.of(approvedVendor));
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        Product result = productService.createProduct(sampleProduct);

        assertNotNull(result);
        assertEquals("Test Laptop", result.getName());
        verify(productRepository, times(1)).save(sampleProduct);
    }

    @Test
    void createProduct_shouldThrow_whenVendorIsNotApproved() {
        sampleProduct.setVendorId(2L);
        when(vendorRepository.findById(2L)).thenReturn(Optional.of(pendingVendor));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> productService.createProduct(sampleProduct));

        assertEquals("Only approved vendors can create product listings", exception.getMessage());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_shouldThrow_whenVendorDoesNotExist() {
        when(vendorRepository.findById(99L)).thenReturn(Optional.empty());
        sampleProduct.setVendorId(99L);

        assertThrows(RuntimeException.class, () -> productService.createProduct(sampleProduct));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_shouldMarkOutOfStock_whenStockIsZero() {
        sampleProduct.setStockQuantity(0);
        when(vendorRepository.findById(1L)).thenReturn(Optional.of(approvedVendor));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.createProduct(sampleProduct);

        assertTrue(result.getOutOfStock());
    }

    @Test
    void getProductById_shouldReturnProduct_whenExists() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        Product result = productService.getProductById(1L);

        assertEquals("Test Laptop", result.getName());
    }

    @Test
    void getProductById_shouldThrow_whenNotFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> productService.getProductById(999L));
    }

    @Test
    void deleteProduct_shouldSucceed_whenProductExists() {
        when(productRepository.existsById(1L)).thenReturn(true);

        productService.deleteProduct(1L);

        verify(productRepository, times(1)).deleteById(1L);
    }

    @Test
    void deleteProduct_shouldThrow_whenProductDoesNotExist() {
        when(productRepository.existsById(999L)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> productService.deleteProduct(999L));
        verify(productRepository, never()).deleteById(anyLong());
    }

    @Test
    void bulkPriceRejectsMixedOwnershipBeforeChangingAnyProduct() {
        Product other = new Product();
        other.setId(2L);
        other.setVendorId(2L);
        other.setPrice(new BigDecimal("500"));
        when(productRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(sampleProduct, other));
        assertThrows(SecurityException.class,
                () -> productService.bulkUpdatePriceForVendor(List.of(1L, 2L), new BigDecimal("20"), 1L));
        assertEquals(new BigDecimal("1000"), sampleProduct.getPrice());
        assertEquals(new BigDecimal("500"), other.getPrice());
        verify(productRepository, never()).saveAll(any());
    }

    @Test
    void purchasedProductCannotBeDeleted() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.hasOrderHistory(1L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> productService.deleteOwnedProduct(1L, 1L));
        verify(productRepository, never()).delete(any());
    }

    @Test
    void reviewedProductCannotBeDeleted() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.hasReviewHistory(1L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> productService.deleteOwnedProduct(1L, 1L));
        verify(productRepository, never()).delete(any());
    }

    @Test
    void purchaseAndRestorationUseLockedInventory() {
        when(productRepository.findForUpdate(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(sampleProduct)).thenReturn(sampleProduct);
        productService.decreaseStockForOrder(1L, 10);
        assertEquals(0, sampleProduct.getStockQuantity());
        assertTrue(sampleProduct.getOutOfStock());
        productService.restoreStockForOrder(1L, 10);
        assertEquals(10, sampleProduct.getStockQuantity());
        verify(productRepository, times(2)).findForUpdate(1L);
    }

    @Test
    void restoringStockCannotOverflowIntoNegativeInventory() {
        sampleProduct.setStockQuantity(Integer.MAX_VALUE);
        when(productRepository.findForUpdate(1L)).thenReturn(Optional.of(sampleProduct));
        assertThrows(IllegalStateException.class, () -> productService.restoreStockForOrder(1L, 1));
        assertEquals(Integer.MAX_VALUE, sampleProduct.getStockQuantity());
        verify(productRepository, never()).save(any());
    }

    @Test
    void getLowStockProducts_shouldReturnFilteredList() {
        when(productRepository.findByStockQuantityLessThan(5))
                .thenReturn(List.of(sampleProduct));

        List<Product> result = productService.getLowStockProducts(5);

        assertEquals(1, result.size());
    }

    @Test
    void updateStockForVendor_shouldUpdateQuantityAndAvailability() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(sampleProduct)).thenReturn(sampleProduct);

        Product result = productService.updateStockForVendor(1L, 0, 1L);

        assertEquals(0, result.getStockQuantity());
        assertTrue(result.getOutOfStock());
        verify(productRepository).save(sampleProduct);
    }

    @Test
    void updateStockForVendor_shouldRejectAnotherVendor() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        assertThrows(
                SecurityException.class,
                () -> productService.updateStockForVendor(1L, 4, 2L)
        );

        verify(productRepository, never()).save(any(Product.class));
    }
}
