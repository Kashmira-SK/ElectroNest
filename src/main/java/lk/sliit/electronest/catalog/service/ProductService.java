package lk.sliit.electronest.catalog.service;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.repository.ProductRepository;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.VendorStatus;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final VendorRepository vendorRepository;

    public ProductService(ProductRepository productRepository,
                          VendorRepository vendorRepository) {
        this.productRepository = productRepository;
        this.vendorRepository = vendorRepository;
    }

    public Product createProduct(Product product) {
        validateProduct(product);

        Vendor vendor = vendorRepository.findById(product.getVendorId())
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found"));

        if (vendor.getStatus() != VendorStatus.APPROVED) {
            throw new IllegalStateException("Only approved vendors can create product listings");
        }

        product.setOutOfStock(product.getStockQuantity() <= 0);
        return productRepository.save(product);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }

    public Product updateProduct(Long id, Product updatedProduct) {
        Product existing = getProductById(id);

        if (!existing.getVendorId().equals(updatedProduct.getVendorId())) {
            throw new SecurityException("Cannot transfer product ownership");
        }

        validateProduct(updatedProduct);

        existing.setName(updatedProduct.getName().trim());
        existing.setBrand(clean(updatedProduct.getBrand()));
        existing.setCategory(clean(updatedProduct.getCategory()));
        existing.setDescription(clean(updatedProduct.getDescription()));
        existing.setPrice(updatedProduct.getPrice());
        existing.setStockQuantity(updatedProduct.getStockQuantity());
        existing.setImageUrl(clean(updatedProduct.getImageUrl()));
        existing.setOutOfStock(updatedProduct.getStockQuantity() <= 0);

        return productRepository.save(existing);
    }

    public Product updateOwnedProduct(Long id,
                                      Product updatedProduct,
                                      Long vendorId) {
        Product existing = getProductById(id);

        assertOwnedByVendor(existing, vendorId);

        updatedProduct.setVendorId(vendorId);
        return updateProduct(id, updatedProduct);
    }

    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new IllegalArgumentException("Product not found");
        }

        requireDeletable(id);
        productRepository.deleteById(id);
    }

    public void deleteOwnedProduct(Long id, Long vendorId) {
        Product product = getProductById(id);
        assertOwnedByVendor(product, vendorId);
        requireDeletable(id);
        productRepository.delete(product);
    }

    @Transactional
    public Product updateStockForVendor(Long id,
                                        Integer stockQuantity,
                                        Long vendorId) {
        if (stockQuantity == null || stockQuantity < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative");
        }

        Product product = getProductById(id);
        assertOwnedByVendor(product, vendorId);

        product.setStockQuantity(stockQuantity);
        product.setOutOfStock(stockQuantity == 0);

        return productRepository.save(product);
    }

    public List<Product> getLowStockProducts(int threshold) {
        if (threshold < 0) {
            throw new IllegalArgumentException("Threshold cannot be negative");
        }

        return productRepository.findByStockQuantityLessThan(threshold);
    }

    public List<Product> getLowStockProductsForVendor(Long vendorId,
                                                      int threshold) {
        if (threshold < 0) {
            throw new IllegalArgumentException("Threshold cannot be negative");
        }

        return productRepository.findByVendorIdAndStockQuantityLessThan(
                vendorId,
                threshold
        );
    }

    public List<Product> searchProducts(String keyword) {
        return productRepository.findByNameContainingIgnoreCase(keyword);
    }

    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    public List<Product> getProductsByVendor(Long vendorId) {
        return productRepository.findByVendorId(vendorId);
    }

    public List<Product> bulkUpdatePrice(List<Long> productIds,
                                         BigDecimal newPrice) {
        validatePrice(newPrice);

        List<Product> products = productRepository.findAllById(productIds);

        for (Product product : products) {
            product.setPrice(newPrice);
        }

        return productRepository.saveAll(products);
    }

    @Transactional
    public List<Product> bulkUpdatePriceForVendor(List<Long> productIds,
                                                  BigDecimal newPrice,
                                                  Long vendorId) {
        validatePrice(newPrice);

        List<Product> products = productRepository.findAllById(productIds);

        if (products.size() != productIds.size()) {
            throw new IllegalArgumentException("One or more products were not found");
        }

        for (Product product : products) {
            assertOwnedByVendor(product, vendorId);
        }
        for (Product product : products) {
            product.setPrice(newPrice);
        }

        return productRepository.saveAll(products);
    }

    @Transactional
    public Product decreaseStockForOrder(Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        Product product = productRepository.findForUpdate(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        int stock = product.getStockQuantity() == null
                ? 0
                : product.getStockQuantity();

        if (Boolean.TRUE.equals(product.getOutOfStock()) || stock <= 0) {
            throw new IllegalStateException(product.getName() + " is out of stock");
        }

        if (quantity > stock) {
            throw new IllegalStateException(
                    "Only " + stock + " units of " + product.getName() + " are available"
            );
        }

        int remaining = stock - quantity;

        product.setStockQuantity(remaining);
        product.setOutOfStock(remaining == 0);

        return productRepository.save(product);
    }

    private void requireDeletable(Long id) {
        if (productRepository.hasOrderHistory(id) || productRepository.hasReviewHistory(id)) {
            throw new IllegalArgumentException("This product has purchase or review history. Set stock to zero instead of deleting it.");
        }
    }

    private void validateProduct(Product product) {
        if (product.getVendorId() == null) {
            throw new IllegalArgumentException("Vendor is required");
        }

        if (product.getName() == null || product.getName().isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }

        if (product.getName().trim().length() < 2 || product.getName().length() > 150) {
            throw new IllegalArgumentException("Product name must be between 2 and 150 characters");
        }
        if (product.getBrand() == null || product.getBrand().isBlank() || product.getBrand().length() > 255) {
            throw new IllegalArgumentException("Brand is required and must be 255 characters or fewer");
        }
        if (product.getCategory() == null || product.getCategory().isBlank() || product.getCategory().length() > 255) {
            throw new IllegalArgumentException("Category is required and must be 255 characters or fewer");
        }
        if (product.getDescription() != null && product.getDescription().length() > 1000) {
            throw new IllegalArgumentException("Description cannot exceed 1000 characters");
        }

        validatePrice(product.getPrice());

        if (product.getStockQuantity() == null ||
                product.getStockQuantity() < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        if (price.stripTrailingZeros().scale() > 2 || price.precision() - price.scale() > 36) {
            throw new IllegalArgumentException("Price must have at most two decimal places and 36 integer digits");
        }
    }

    private void assertOwnedByVendor(Product product, Long vendorId) {
        if (!product.getVendorId().equals(vendorId)) {
            throw new SecurityException("Vendor does not own this product");
        }
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public Product validateStockForOrder(Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero"
            );
        }

        Product product = getProductById(productId);

        int stock = product.getStockQuantity() == null
                ? 0
                : product.getStockQuantity();

        if (Boolean.TRUE.equals(product.getOutOfStock())
                || stock < quantity) {
            throw new IllegalStateException(
                    "Only " + stock + " units of "
                            + product.getName() + " are available"
            );
        }

        return product;
    }

    @Transactional
    public Product restoreStockForOrder(Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero"
            );
        }

        Product product = productRepository.findForUpdate(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        int stock = product.getStockQuantity() == null
                ? 0
                : product.getStockQuantity();

        product.setStockQuantity(stock + quantity);
        product.setOutOfStock(false);

        return productRepository.save(product);
    }

}
