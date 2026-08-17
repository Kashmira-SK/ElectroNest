package lk.sliit.electronest.cart.service;

import lk.sliit.electronest.cart.model.CartItem;
import lk.sliit.electronest.cart.repository.CartItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class CartService {

    @Autowired
    private CartItemRepository cartItemRepository;

    // Adds a product to the cart, or increases the quantity if it's already there
    public CartItem addItemToCart(Long userId, Long productId, Integer quantity, BigDecimal unitPrice) {
        Optional<CartItem> existingItem = cartItemRepository.findByUserIdAndProductId(userId, productId);

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + quantity);
            return cartItemRepository.save(item);
        } else {
            CartItem newItem = new CartItem(userId, productId, quantity, unitPrice);
            return cartItemRepository.save(newItem);
        }
    }

    // Fetches all items for a user's cart view
    public List<CartItem> getCartItems(Long userId) {
        return cartItemRepository.findByUserId(userId);
    }

    // Updates the quantity of a specific item
    public CartItem updateItemQuantity(Long itemId, Integer newQuantity) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));
        item.setQuantity(newQuantity);
        return cartItemRepository.save(item);
    }

    // Removes an item from the cart
    public void removeItemFromCart(Long itemId) {
        cartItemRepository.deleteById(itemId);
    }

    // Calculates the subtotal for the entire cart
    public BigDecimal calculateSubtotal(Long userId) {
        List<CartItem> items = getCartItems(userId);
        return items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}