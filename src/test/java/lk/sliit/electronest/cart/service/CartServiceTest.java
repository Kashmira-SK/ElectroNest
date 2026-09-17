package lk.sliit.electronest.cart.service;

import lk.sliit.electronest.cart.model.CartItem;
import lk.sliit.electronest.cart.repository.CartItemRepository;
import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private CartService cartService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(10L);
        product.setName("Studio Headphones");
        product.setPrice(new BigDecimal("12500.00"));
        product.setStockQuantity(5);
        product.setOutOfStock(false);
    }

    @Test
    void addItemRejectsCombinedQuantityAboveCurrentStock() {
        CartItem existing = new CartItem(2L, 10L, 4, new BigDecimal("12000.00"));
        existing.setId(7L);

        when(productService.getProductById(10L)).thenReturn(product);
        when(cartItemRepository.findByUserIdAndProductId(2L, 10L))
                .thenReturn(Optional.of(existing));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> cartService.addItemToCart(2L, 10L, 2)
        );

        assertEquals("Only 5 units of Studio Headphones are available", error.getMessage());
        verify(cartItemRepository, never()).save(existing);
    }

    @Test
    void updateRejectsAnotherCustomersCartItem() {
        CartItem item = new CartItem(99L, 10L, 1, product.getPrice());
        item.setId(7L);
        when(cartItemRepository.findById(7L)).thenReturn(Optional.of(item));

        assertThrows(
                SecurityException.class,
                () -> cartService.updateItemQuantity(2L, 7L, 2)
        );

        verify(productService, never()).getProductById(10L);
        verify(cartItemRepository, never()).save(item);
    }

    @Test
    void deletedListingRemainsRemovableWithoutBreakingCart() {
        CartItem item = new CartItem(2L, 10L, 2, BigDecimal.TEN);
        item.setId(7L);
        when(cartItemRepository.findByUserId(2L)).thenReturn(List.of(item));
        when(productService.getProductById(10L)).thenThrow(new IllegalArgumentException("Product not found"));
        var view = cartService.getCartItemViews(2L).getFirst();
        assertEquals(7L, view.itemId());
        assertEquals(0, view.stockQuantity());
        assertEquals(BigDecimal.ZERO, cartService.getCartSummary(2L).subtotal());
        when(cartItemRepository.findById(7L)).thenReturn(Optional.of(item));
        cartService.removeItemFromCart(2L, 7L);
        verify(cartItemRepository).delete(item);
    }

    @Test
    void explicitlyOutOfStockListingIsUnavailableInSummary() {
        product.setOutOfStock(true);
        when(cartItemRepository.findByUserId(2L)).thenReturn(List.of(new CartItem(2L, 10L, 1, BigDecimal.TEN)));
        when(productService.getProductById(10L)).thenReturn(product);
        assertEquals(0, cartService.getCartItemViews(2L).getFirst().stockQuantity());
    }

    @Test
    void combinedQuantityCannotOverflowIntoNegativeNumber() {
        CartItem existing = new CartItem(2L, 10L, 1, BigDecimal.TEN);
        when(productService.getProductById(10L)).thenReturn(product);
        when(cartItemRepository.findByUserIdAndProductId(2L, 10L)).thenReturn(Optional.of(existing));
        assertThrows(IllegalArgumentException.class,
                () -> cartService.addItemToCart(2L, 10L, Integer.MAX_VALUE));
        verify(cartItemRepository, never()).save(existing);
    }

    @Test
    void summaryUsesCurrentServerPriceInsteadOfStoredCartPrice() {
        CartItem item = new CartItem(2L, 10L, 2, new BigDecimal("1.00"));
        item.setId(7L);

        when(cartItemRepository.findByUserId(2L)).thenReturn(List.of(item));
        when(productService.getProductById(10L)).thenReturn(product);

        var summary = cartService.getCartSummary(2L);

        assertEquals(new BigDecimal("12500.00"), summary.items().getFirst().unitPrice());
        assertEquals(new BigDecimal("25000.00"), summary.subtotal());
    }
}
