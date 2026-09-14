package lk.sliit.electronest.catalog.controller;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.service.ProductService;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final VendorRepository vendorRepository;

    public ProductController(ProductService productService,
                             VendorRepository vendorRepository) {
        this.productService = productService;
        this.vendorRepository = vendorRepository;
    }

    @PostMapping
    @PreAuthorize("hasRole('VENDOR')")
    public Product createProduct(
            @RequestBody Product product,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        Vendor vendor = currentVendor(currentUser);
        product.setVendorId(vendor.getId());

        return productService.createProduct(product);
    }

    @GetMapping
    public List<Product> getAllProducts() {
        return productService.getAllProducts();
    }

    @GetMapping("/{id}")
    public Product getProduct(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public Product updateProduct(
            @PathVariable Long id,
            @RequestBody Product product,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        Vendor vendor = currentVendor(currentUser);

        return productService.updateOwnedProduct(
                id,
                product,
                vendor.getId()
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public void deleteProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        Vendor vendor = currentVendor(currentUser);
        productService.deleteOwnedProduct(id, vendor.getId());
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasRole('VENDOR')")
    public List<Product> getLowStockProducts(
            @RequestParam(defaultValue = "5") int threshold,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        Vendor vendor = currentVendor(currentUser);

        return productService.getLowStockProductsForVendor(
                vendor.getId(),
                threshold
        );
    }

    @GetMapping("/search")
    public List<Product> searchProducts(@RequestParam String keyword) {
        return productService.searchProducts(keyword);
    }

    @GetMapping("/category/{category}")
    public List<Product> getProductsByCategory(
            @PathVariable String category) {
        return productService.getProductsByCategory(category);
    }

    @GetMapping("/vendor/{vendorId}")
    public List<Product> getProductsByVendor(
            @PathVariable Long vendorId) {
        return productService.getProductsByVendor(vendorId);
    }

    @GetMapping("/vendor/my-products")
    @PreAuthorize("hasRole('VENDOR')")
    public List<Product> getMyProducts(
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        Vendor vendor = currentVendor(currentUser);
        return productService.getProductsByVendor(vendor.getId());
    }

    @PutMapping("/bulk-price")
    @PreAuthorize("hasRole('VENDOR')")
    public List<Product> bulkUpdatePrice(
            @RequestParam List<Long> productIds,
            @RequestParam BigDecimal newPrice,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        Vendor vendor = currentVendor(currentUser);

        return productService.bulkUpdatePriceForVendor(
                productIds,
                newPrice,
                vendor.getId()
        );
    }

    private Vendor currentVendor(CustomUserDetails currentUser) {
        return vendorRepository.findByUser_Id(currentUser.getUser().getId())
                .orElseThrow(() ->
                        new IllegalStateException("Vendor profile not found"));
    }
}
