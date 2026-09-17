package lk.sliit.electronest.catalog.controller;

import jakarta.validation.Valid;
import lk.sliit.electronest.catalog.dto.ProductForm;
import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.service.ProductService;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.VendorStatus;
import lk.sliit.electronest.vendor.service.VendorService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/vendor/products")
@PreAuthorize("hasRole('VENDOR')")
public class SellerProductViewController {

    private static final int LOW_STOCK_THRESHOLD = 5;

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
            Model model,
            RedirectAttributes redirectAttributes) {

        Vendor vendor = approvedVendor(currentUser, redirectAttributes);
        if (vendor == null) {
            return "redirect:/vendor/status";
        }

        model.addAttribute("vendor", vendor);
        model.addAttribute("lowStockThreshold", LOW_STOCK_THRESHOLD);
        model.addAttribute("products",
                productService.getProductsByVendor(vendor.getId()));

        return "catalog/vendor-products";
    }

    @GetMapping("/new")
    public String newProduct(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (approvedVendor(currentUser, redirectAttributes) == null) {
            return "redirect:/vendor/status";
        }

        if (!model.containsAttribute("product")) {
            model.addAttribute("product", new ProductForm());
        }
        return "catalog/vendor-product-form";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("product") ProductForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes) {

        Vendor vendor = approvedVendor(currentUser, redirectAttributes);
        if (vendor == null) {
            return "redirect:/vendor/status";
        }

        if (bindingResult.hasErrors()) {
            return "catalog/vendor-product-form";
        }

        Product product = toProduct(form);
        product.setVendorId(vendor.getId());

        try {
            productService.createProduct(product);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "catalog/vendor-product-form";
        }

        redirectAttributes.addFlashAttribute(
                "successMessage", "Product created successfully.");

        return "redirect:/vendor/products";
    }

    @GetMapping("/{id}/edit")
    public String edit(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes) {
        Vendor vendor = approvedVendor(currentUser, redirectAttributes);
        if (vendor == null) {
            return "redirect:/vendor/status";
        }

        try {
            Product product = ownedProduct(id, vendor);
            if (!model.containsAttribute("product")) {
                model.addAttribute("product", toForm(product));
            }
            model.addAttribute("currentImages", product.getImageUrls());
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/vendor/products";
        }

        return "catalog/vendor-product-form";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable Long id,
            @Valid @ModelAttribute("product") ProductForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes) {

        Vendor vendor = approvedVendor(currentUser, redirectAttributes);
        if (vendor == null) {
            return "redirect:/vendor/status";
        }

        form.setId(id);
        if (bindingResult.hasErrors()) {
            return "catalog/vendor-product-form";
        }

        try {
            productService.updateOwnedProduct(id, toProduct(form), vendor.getId());
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "catalog/vendor-product-form";
        }

        redirectAttributes.addFlashAttribute(
                "successMessage", "Product updated successfully.");

        return "redirect:/vendor/products";
    }

    @PostMapping("/bulk-price")
    public String bulkPrice(@RequestParam(required = false) java.util.List<Long> productIds,
                            @RequestParam java.math.BigDecimal newPrice,
                            @AuthenticationPrincipal CustomUserDetails currentUser,
                            RedirectAttributes redirectAttributes) {
        Vendor vendor = approvedVendor(currentUser, redirectAttributes);
        if (vendor == null) return "redirect:/vendor/status";
        try {
            if (productIds == null || productIds.isEmpty()) {
                throw new IllegalArgumentException("Select at least one product.");
            }
            productService.bulkUpdatePriceForVendor(productIds, newPrice, vendor.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Selected product prices updated.");
        } catch (IllegalArgumentException | SecurityException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/vendor/products";
    }

    @PostMapping("/{id}/stock")
    public String updateStock(
            @PathVariable Long id,
            @RequestParam Integer stockQuantity,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {
        Vendor vendor = approvedVendor(currentUser, redirectAttributes);
        if (vendor == null) {
            return "redirect:/vendor/status";
        }

        try {
            Product product = productService.updateStockForVendor(
                    id,
                    stockQuantity,
                    vendor.getId()
            );
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    product.getName() + " stock updated to " + product.getStockQuantity() + "."
            );
        } catch (IllegalArgumentException | SecurityException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/vendor/products";
    }

    @PostMapping("/{id}/delete")
    public String delete(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {

        Vendor vendor = approvedVendor(currentUser, redirectAttributes);
        if (vendor == null) {
            return "redirect:/vendor/status";
        }

        try {
            productService.deleteOwnedProduct(id, vendor.getId());

            redirectAttributes.addFlashAttribute(
                    "successMessage", "Product removed.");
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "This product is referenced by an order or review and cannot be removed. Set its stock to zero instead."
            );
        } catch (IllegalArgumentException | SecurityException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/vendor/products";
    }

    private Vendor approvedVendor(
            CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {
        try {
            Vendor vendor = vendorService.getVendorForUser(
                    currentUser.getUser().getId()
            );

            if (vendor.getStatus() != VendorStatus.APPROVED) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage",
                        "Seller tools are available after your vendor application is approved."
                );
                return null;
            }

            return vendor;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return null;
        }
    }

    private Product ownedProduct(
            Long productId,
            Vendor vendor) {
        Product product = productService.getProductById(productId);

        if (!vendor.getId().equals(product.getVendorId())) {
            throw new IllegalArgumentException(
                    "You cannot manage another vendor's product.");
        }

        return product;
    }

    private Product toProduct(ProductForm form) {
        Product product = new Product();
        product.setName(form.getName());
        product.setBrand(form.getBrand());
        product.setCategory(form.getCategory());
        product.setDescription(form.getDescription());
        product.setPrice(form.getPrice());
        product.setStockQuantity(form.getStockQuantity());
        product.setImageUrl(form.getImageUrl());
        return product;
    }

    private ProductForm toForm(Product product) {
        ProductForm form = new ProductForm();
        form.setId(product.getId());
        form.setName(product.getName());
        form.setBrand(product.getBrand());
        form.setCategory(product.getCategory());
        form.setDescription(product.getDescription());
        form.setPrice(product.getPrice());
        form.setStockQuantity(product.getStockQuantity());
        form.setImageUrl(product.getImageUrl());
        return form;
    }
}
