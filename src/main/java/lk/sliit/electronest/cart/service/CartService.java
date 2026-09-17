package lk.sliit.electronest.cart.service;

import lk.sliit.electronest.cart.dto.CartItemResponse;
import lk.sliit.electronest.cart.dto.CartItemView;
import lk.sliit.electronest.cart.dto.CartSummaryResponse;
import lk.sliit.electronest.cart.model.CartItem;
import lk.sliit.electronest.cart.repository.CartItemRepository;
import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.service.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductService productService;

    public CartService(CartItemRepository cartItemRepository,
                       ProductService productService) {
        this.cartItemRepository = cartItemRepository;
        this.productService = productService;
    }

    public CartItem addItemToCart(Long userId, Long productId, Integer quantity) {
        validateQuantity(quantity);

        Product product = productService.getProductById(productId);
        validateAvailable(product);

        Optional<CartItem> existing =
                cartItemRepository.findByUserIdAndProductId(userId, productId);

        int requestedQuantity = quantity;

        if (existing.isPresent()) {
            try {
                requestedQuantity = Math.addExact(quantity, existing.get().getQuantity());
            } catch (ArithmeticException ex) {
                throw new IllegalArgumentException("Requested quantity is too large");
            }
        }

        validateStock(product, requestedQuantity);

        CartItem item = existing.orElseGet(
                () -> new CartItem(userId, productId, 0, product.getPrice())
        );

        item.setQuantity(requestedQuantity);
        item.setUnitPrice(product.getPrice());

        return cartItemRepository.save(item);
    }

    public CartItem updateItemQuantity(Long userId,
                                       Long itemId,
                                       Integer quantity) {
        validateQuantity(quantity);

        CartItem item = getOwnedItem(userId, itemId);
        Product product = productService.getProductById(item.getProductId());

        validateAvailable(product);
        validateStock(product, quantity);

        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());

        return cartItemRepository.save(item);
    }

    public void removeItemFromCart(Long userId, Long itemId) {
        CartItem item = getOwnedItem(userId, itemId);
        cartItemRepository.delete(item);
    }

    public List<CartItem> getCartItems(Long userId) {
        return cartItemRepository.findByUserId(userId);
    }


    public List<CartItemView> getCartItemViews(Long userId) {
        return getCartItems(userId).stream()
                .map(item -> {
                    Product product = productForCart(item);

                    BigDecimal lineTotal = product.getPrice()
                            .multiply(BigDecimal.valueOf(item.getQuantity()));

                    return new CartItemView(
                            item.getId(),
                            product.getId(),
                            product.getName(),
                            product.getBrand(),
                            product.getImageUrl(),
                            item.getQuantity(),
                            product.getPrice(),
                            lineTotal,
                            availableStock(product)
                    );
                })
                .toList();
    }

    public CartSummaryResponse getCartSummary(Long userId) {
        List<CartItemResponse> items = getCartItems(userId).stream()
                .map(this::toResponse)
                .toList();

        int totalItems = items.stream()
                .mapToInt(CartItemResponse::quantity)
                .sum();

        BigDecimal subtotal = items.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartSummaryResponse(items, totalItems, subtotal);
    }

    public BigDecimal calculateSubtotal(Long userId) {
        return getCartSummary(userId).subtotal();
    }

    @Transactional
    public void clearCart(Long userId) {
        List<CartItem> items = cartItemRepository.findByUserId(userId);
        cartItemRepository.deleteAll(items);
    }

    private CartItemResponse toResponse(CartItem item) {
        Product product = productForCart(item);

        BigDecimal currentPrice = product.getPrice();
        BigDecimal lineTotal = currentPrice.multiply(
                BigDecimal.valueOf(item.getQuantity())
        );

        return new CartItemResponse(
                item.getId(),
                product.getId(),
                product.getName(),
                product.getBrand(),
                product.getImageUrl(),
                item.getQuantity(),
                currentPrice,
                lineTotal,
                availableStock(product)
        );
    }

    private Product productForCart(CartItem item) {
        try {
            return productService.getProductById(item.getProductId());
        } catch (IllegalArgumentException ex) {
            Product unavailable = new Product();
            unavailable.setName("Unavailable product");
            unavailable.setPrice(BigDecimal.ZERO);
            unavailable.setStockQuantity(0);
            unavailable.setOutOfStock(true);
            return unavailable;
        }
    }

    private int availableStock(Product product) {
        return Boolean.TRUE.equals(product.getOutOfStock()) || product.getStockQuantity() == null
                ? 0 : product.getStockQuantity();
    }

    private CartItem getOwnedItem(Long userId, Long itemId) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Cart item not found"));

        if (!item.getUserId().equals(userId)) {
            throw new SecurityException(
                    "You cannot modify another customer's cart"
            );
        }

        return item;
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero"
            );
        }
    }

    private void validateAvailable(Product product) {
        int stock = product.getStockQuantity() == null
                ? 0
                : product.getStockQuantity();

        if (Boolean.TRUE.equals(product.getOutOfStock()) || stock <= 0) {
            throw new IllegalStateException(
                    product.getName() + " is out of stock"
            );
        }
    }

    private void validateStock(Product product, int quantity) {
        int stock = product.getStockQuantity() == null
                ? 0
                : product.getStockQuantity();

        if (quantity > stock) {
            throw new IllegalStateException(
                    "Only " + stock + " units of "
                            + product.getName() + " are available"
            );
        }
    }
}
