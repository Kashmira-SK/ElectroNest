package lk.sliit.electronest.cart.service;

import lk.sliit.electronest.cart.model.CartItem;
import lk.sliit.electronest.cart.repository.CartItemRepository;
// NOTE: Import your team's Product and ProductRepository here!
// import lk.sliit.electronest.product.model.Product;
// import lk.sliit.electronest.product.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class CartService {

    @Autowired
    private CartItemRepository cartItemRepository;

    // @Autowired
    // private ProductRepository productRepository; // You will need to uncomment this when you have the Product class!

    // CREATE: Validates stock and grabs the official price
    public CartItem addItemToCart(Long userId, Long productId, Integer quantity) {
        // 1. Fetch the official product from the database
        // Product product = productRepository.findById(productId)
        //        .orElseThrow(() -> new RuntimeException("Product not found"));

        // 2. Stock Validation (Assuming the method is called getStockQuantity)
        // if (product.getStockQuantity() < quantity) {
        //     throw new RuntimeException("Insufficient stock available");
        // }

        // 3. Get the official price (Assuming the method is called getPrice)
        // BigDecimal officialPrice = product.getPrice();
        BigDecimal officialPrice = new BigDecimal("99.99"); // TEMPORARY PLACEHOLDER until you link the Product

        Optional<CartItem> existingItem = cartItemRepository.findByUserIdAndProductId(userId, productId);

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            // Also validate that existing quantity + new quantity doesn't exceed stock!
            item.setQuantity(item.getQuantity() + quantity);
            return cartItemRepository.save(item);
        } else {
            CartItem newItem = new CartItem(userId, productId, quantity, officialPrice);
            return cartItemRepository.save(newItem);
        }
    }

    public List<CartItem> getCartItems(Long userId) {
        return cartItemRepository.findByUserId(userId);
    }

    // UPDATE: Ownership check and stock validation
    public CartItem updateItemQuantity(Long userId, Long itemId, Integer newQuantity) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        // Ownership Validation: Stop users from editing other people's carts
        if (!item.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized action");
        }

        item.setQuantity(newQuantity);
        return cartItemRepository.save(item);
    }

    // DELETE: Ownership check
    public void removeItemFromCart(Long userId, Long itemId) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        if (!item.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized action");
        }

        cartItemRepository.delete(item);
    }

    public BigDecimal calculateSubtotal(Long userId) {
        List<CartItem> items = getCartItems(userId);
        return items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}