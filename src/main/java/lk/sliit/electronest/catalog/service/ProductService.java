package lk.sliit.electronest.catalog.service;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.repository.ProductRepository;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.VendorStatus;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private VendorRepository vendorRepository;

    public Product createProduct(Product product) {
        Vendor vendor = vendorRepository.findById(product.getVendorId())
                .orElseThrow(() -> new RuntimeException("Vendor not found with id: " + product.getVendorId()));

        if (vendor.getStatus() != VendorStatus.APPROVED) {
            throw new RuntimeException("Only approved vendors can create product listings");
        }

        product.setOutOfStock(product.getStockQuantity() == null || product.getStockQuantity() <= 0);
        return productRepository.save(product);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
    }

    public Product updateProduct(Long id, Product updatedProduct) {
        Product existing = getProductById(id);

        // Ensure the vendor updating is still the owner and still approved
        if (!existing.getVendorId().equals(updatedProduct.getVendorId())) {
            throw new RuntimeException("Cannot transfer a product to a different vendor");
        }

        existing.setName(updatedProduct.getName());
        existing.setBrand(updatedProduct.getBrand());
        existing.setCategory(updatedProduct.getCategory());
        existing.setDescription(updatedProduct.getDescription());
        existing.setPrice(updatedProduct.getPrice());
        existing.setStockQuantity(updatedProduct.getStockQuantity());
        existing.setImageUrl(updatedProduct.getImageUrl());
        existing.setOutOfStock(updatedProduct.getStockQuantity() == null || updatedProduct.getStockQuantity() <= 0);

        return productRepository.save(existing);
    }

    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new RuntimeException("Product not found with id: " + id);
        }
        productRepository.deleteById(id);
    }

    public List<Product> getLowStockProducts(int threshold) {
        return productRepository.findByStockQuantityLessThan(threshold);
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

    public List<Product> bulkUpdatePrice(List<Long> productIds, BigDecimal newPrice) {
        List<Product> products = productRepository.findAllById(productIds);
        for (Product p : products) {
            p.setPrice(newPrice);
        }
        return productRepository.saveAll(products);
    }
}