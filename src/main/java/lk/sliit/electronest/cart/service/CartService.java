package lk.sliit.electronest.cart.service;

import lk.sliit.electronest.cart.dto.CartItemView;
import lk.sliit.electronest.cart.model.CartItem;
import lk.sliit.electronest.cart.repository.CartItemRepository;
import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.service.ProductService;
import org.springframework.stereotype.Service;

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
        int stock = availableStock(product);

        Optional<CartItem> existing =
                cartItemRepository.findByUserIdAndProductId(userId, productId);

        if (existing.isPresent()) {
            CartItem item = existing.get();
            int requestedQuantity = item.getQuantity() + quantity;

            validateStock(product, requestedQuantity, stock);

            item.setQuantity(requestedQuantity);
            item.setUnitPrice(product.getPrice());

            return cartItemRepository.save(item);
        }

        validateStock(product, quantity, stock);

        return cartItemRepository.save(
                new CartItem(userId, productId, quantity, product.getPrice())
        );
    }

    public List<CartItem> getCartItems(Long userId) {
        return cartItemRepository.findByUserId(userId);
    }

    public List<CartItemView> getCartItemViews(Long userId) {
        return getCartItems(userId).stream()
                .map(item -> {
                    Product product = productService.getProductById(item.getProductId());

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
                            product.getStockQuantity()
                    );
                })
                .toList();
    }

    public CartItem updateItemQuantity(Long userId,
                                       Long itemId,
                                       Integer quantity) {
        validateQuantity(quantity);

        CartItem item = ownedItem(userId, itemId);
        Product product = productService.getProductById(item.getProductId());

        validateStock(product, quantity, availableStock(product));

        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());

        return cartItemRepository.save(item);
    }

    public void removeItemFromCart(Long userId, Long itemId) {
        cartItemRepository.delete(ownedItem(userId, itemId));
    }

    public BigDecimal calculateSubtotal(Long userId) {
        return getCartItems(userId).stream()
                .map(item -> {
                    Product product = productService.getProductById(item.getProductId());
                    return product.getPrice()
                            .multiply(BigDecimal.valueOf(item.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private CartItem ownedItem(Long userId, Long itemId) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found"));

        if (!item.getUserId().equals(userId)) {
            throw new SecurityException("You cannot modify another customer's cart");
        }

        return item;
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
    }

    private int availableStock(Product product) {
        return product.getStockQuantity() == null ? 0 : product.getStockQuantity();
    }

    private void validateStock(Product product,
                               int requestedQuantity,
                               int stock) {
        if (Boolean.TRUE.equals(product.getOutOfStock()) || stock <= 0) {
            throw new IllegalStateException(product.getName() + " is out of stock");
        }

        if (requestedQuantity > stock) {
            throw new IllegalStateException(
                    "Only " + stock + " units of " + product.getName() + " are available"
            );
        }
    }
}
