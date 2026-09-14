package lk.sliit.electronest.catalog.controller;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.service.ProductService;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.service.VendorService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/vendor/products")
@PreAuthorize("hasRole('VENDOR')")
public class SellerProductViewController {

    private final ProductService productService;
    private final VendorService vendorService;

    public SellerProductViewController(
            ProductService productService,
            VendorService vendorService) {
        this.productService = productService;
        this.vendorService = vendorService;
    }

    @GetMapping
    public String products(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model) {

        Vendor vendor = currentVendor(currentUser);
        model.addAttribute("products",
                productService.getProductsByVendor(vendor.getId()));

        return "catalog/vendor-products";
    }

    @GetMapping("/new")
    public String newProduct(Model model) {
        model.addAttribute("product", new Product());
        return "catalog/vendor-product-form";
    }

    @PostMapping
    public String create(
            @ModelAttribute Product product,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {

        Vendor vendor = currentVendor(currentUser);
        product.setVendorId(vendor.getId());

        productService.createProduct(product);

        redirectAttributes.addFlashAttribute(
                "successMessage", "Product created successfully.");

        return "redirect:/vendor/products";
    }

    @GetMapping("/{id}/edit")
    public String edit(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model) {

        Product product = ownedProduct(id, currentUser);
        model.addAttribute("product", product);

        return "catalog/vendor-product-form";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable Long id,
            @ModelAttribute Product product,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {

        Product existing = ownedProduct(id, currentUser);
        product.setVendorId(existing.getVendorId());

        productService.updateProduct(id, product);

        redirectAttributes.addFlashAttribute(
                "successMessage", "Product updated successfully.");

        return "redirect:/vendor/products";
    }

    @PostMapping("/{id}/delete")
    public String delete(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {

        ownedProduct(id, currentUser);
        productService.deleteProduct(id);

        redirectAttributes.addFlashAttribute(
                "successMessage", "Product removed.");

        return "redirect:/vendor/products";
    }

    private Vendor currentVendor(CustomUserDetails currentUser) {
        return vendorService.getVendorForUser(
                currentUser.getUser().getId());
    }

    private Product ownedProduct(
            Long productId,
            CustomUserDetails currentUser) {

        Vendor vendor = currentVendor(currentUser);
        Product product = productService.getProductById(productId);

        if (!vendor.getId().equals(product.getVendorId())) {
            throw new IllegalArgumentException(
                    "You cannot manage another vendor's product.");
        }

        return product;
    }
}
