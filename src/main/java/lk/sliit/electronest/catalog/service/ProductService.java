package lk.sliit.electronest.catalog.service;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.repository.ProductRepository;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.VendorStatus;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import org.springframework.stereotype.Service;

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

        productRepository.deleteById(id);
    }

    public void deleteOwnedProduct(Long id, Long vendorId) {
        Product product = getProductById(id);
        assertOwnedByVendor(product, vendorId);
        productRepository.delete(product);
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
            product.setPrice(newPrice);
        }

        return productRepository.saveAll(products);
    }

    private void validateProduct(Product product) {
        if (product.getVendorId() == null) {
            throw new IllegalArgumentException("Vendor is required");
        }

        if (product.getName() == null || product.getName().isBlank()) {
            throw new IllegalArgumentException("Product name is required");
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
}
