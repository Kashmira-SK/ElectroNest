package lk.sliit.electronest.catalog.controller;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.service.ProductImageStorageService;
import lk.sliit.electronest.catalog.service.ProductService;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.VendorStatus;
import lk.sliit.electronest.vendor.service.VendorService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Controller
public class ProductMediaViewController {

    private final ProductService productService;
    private final VendorService vendorService;
    private final ProductImageStorageService imageStorage;

    public ProductMediaViewController(
            ProductService productService,
            VendorService vendorService,
            ProductImageStorageService imageStorage) {
        this.productService = productService;
        this.vendorService = vendorService;
        this.imageStorage = imageStorage;
    }

    @PreAuthorize("hasRole('VENDOR')")
    @PostMapping("/vendor/products/save-media")
    public String saveProduct(
            @RequestParam(required = false) Long id,
            @RequestParam String name,
            @RequestParam String brand,
            @RequestParam String category,
            @RequestParam(required = false) String description,
            @RequestParam BigDecimal price,
            @RequestParam Integer stockQuantity,
            @RequestParam(required = false) String externalImageUrl,
            @RequestParam(value = "images", required = false)
            MultipartFile[] images,
            @RequestParam(required = false) Integer ramGb,
            @RequestParam(required = false) Integer storageGb,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {

        boolean editing = id != null;

        try {
            Vendor vendor = vendorService.getVendorForUser(
                    currentUser.getUser().getId()
            );

            if (vendor.getStatus() != VendorStatus.APPROVED) {
                throw new IllegalStateException(
                        "Only approved vendors can manage products"
                );
            }

            ProductService.validateHardware(ramGb, storageGb);
            validate(
                    name,
                    brand,
                    category,
                    description,
                    price,
                    stockQuantity
            );

            Product product;

            if (editing) {
                product = productService.getProductById(id);

                if (!vendor.getId().equals(product.getVendorId())) {
                    throw new IllegalStateException(
                            "You can only edit your own products"
                    );
                }
            } else {
                product = new Product();
            }

            List<String> oldGallery = gallery(product);
            List<MultipartFile> uploads = nonEmpty(images);
            if (!blank(externalImageUrl)) {
                java.net.URI external;
                try {
                    external = java.net.URI.create(externalImageUrl.trim());
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Enter a valid HTTPS or HTTP image URL.");
                }
                if (external.getHost() == null || !("https".equalsIgnoreCase(external.getScheme())
                        || "http".equalsIgnoreCase(external.getScheme())) || externalImageUrl.length() > 1000) {
                    throw new IllegalArgumentException("Enter a valid HTTPS or HTTP image URL (up to 1000 characters).");
                }
            }

            if (uploads.size() > 8) {
                throw new IllegalArgumentException(
                        "You can upload up to 8 product images"
                );
            }

            if (!editing
                    && uploads.isEmpty()
                    && blank(externalImageUrl)) {
                throw new IllegalArgumentException(
                        "Add at least one product image or image URL"
                );
            }

            List<String> stored = new ArrayList<>();

            try {
                for (MultipartFile image : uploads) {
                    stored.add(imageStorage.store(image));
                }

                product.setName(name.trim());
                product.setBrand(brand.trim());
                product.setCategory(category.trim());

                product.setDescription(
                        blank(description)
                                ? null
                                : description.trim()
                );

                product.setRamGb(ramGb);
                product.setStorageGb(storageGb);
                product.setPrice(price);
                product.setStockQuantity(stockQuantity);
                product.setVendorId(vendor.getId());

                if (!stored.isEmpty()) {
                    List<String> newGallery =
                            new ArrayList<>(stored);

                    if (!blank(externalImageUrl)) {
                        String external =
                                externalImageUrl.trim();

                        if (!newGallery.contains(external)) {
                            newGallery.add(external);
                        }
                    }

                    if (newGallery.size() > 8) {
                        throw new IllegalArgumentException("Use up to 8 images in total, including the external URL.");
                    }
                    product.setImageUrls(newGallery);
                    product.setImageUrl(newGallery.get(0));

                } else if (!blank(externalImageUrl)) {
                    String external =
                            externalImageUrl.trim();

                    List<String> existingGallery =
                            new ArrayList<>(
                                    product.getImageUrls() == null
                                            ? List.of()
                                            : product.getImageUrls()
                            );

                    existingGallery.remove(external);
                    existingGallery.add(0, external);
                    if (existingGallery.size() > 8) {
                        throw new IllegalArgumentException("Use up to 8 images in total. Upload a replacement gallery to change the images.");
                    }

                    product.setImageUrls(existingGallery);
                    product.setImageUrl(external);
                }

                Product saved =
                        productService.createProduct(product);

                if (!stored.isEmpty()) {
                    for (String old : oldGallery) {
                        if (!saved.getImageUrls().contains(old)) {
                            imageStorage.deleteUrlQuietly(old);
                        }
                    }
                }

                redirectAttributes.addFlashAttribute(
                        "successMessage",
                        editing
                                ? "Product updated successfully."
                                : "Product added successfully."
                );

                return "redirect:/vendor/products";

            } catch (RuntimeException ex) {
                stored.forEach(
                        imageStorage::deleteUrlQuietly
                );

                throw ex;
            }

        } catch (RuntimeException ex) {
            var submitted = new lk.sliit.electronest.catalog.dto.ProductForm();
            submitted.setId(id);
            submitted.setName(name);
            submitted.setBrand(brand);
            submitted.setCategory(category);
            submitted.setDescription(description);
            submitted.setRamGb(ramGb);
            submitted.setStorageGb(storageGb);
            submitted.setPrice(price);
            submitted.setStockQuantity(stockQuantity);
            redirectAttributes.addFlashAttribute("product", submitted);
            redirectAttributes.addFlashAttribute("externalImageUrl", externalImageUrl);
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    ex.getMessage() + " Your text fields were kept; reselect any image files before retrying."
            );

            if (editing) {
                return "redirect:/vendor/products/"
                        + id
                        + "/edit";
            }

            return "redirect:/vendor/products/new";
        }
    }

    @GetMapping("/images/products-upload/{filename:.+}")
    public ResponseEntity<Resource> image(
            @PathVariable String filename) {

        Resource resource =
                imageStorage.load(filename);

        String lower = filename.toLowerCase();

        MediaType type =
                MediaType.APPLICATION_OCTET_STREAM;

        if (lower.endsWith(".png")) {
            type = MediaType.IMAGE_PNG;
        } else if (
                lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
        ) {
            type = MediaType.IMAGE_JPEG;
        } else if (lower.endsWith(".webp")) {
            type = MediaType.parseMediaType(
                    "image/webp"
            );
        }

        return ResponseEntity.ok()
                .contentType(type)
                .header(
                        HttpHeaders.CACHE_CONTROL,
                        "public, max-age=86400"
                )
                .body(resource);
    }

    private List<MultipartFile> nonEmpty(
            MultipartFile[] files) {

        if (files == null) {
            return List.of();
        }

        List<MultipartFile> result =
                new ArrayList<>();

        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                result.add(file);
            }
        }

        return result;
    }

    private List<String> gallery(Product product) {
        LinkedHashSet<String> result =
                new LinkedHashSet<>();

        if (!blank(product.getImageUrl())) {
            result.add(product.getImageUrl());
        }

        if (product.getImageUrls() != null) {
            for (String image : product.getImageUrls()) {
                if (!blank(image)) {
                    result.add(image);
                }
            }
        }

        return new ArrayList<>(result);
    }

    private void validate(
            String name,
            String brand,
            String category,
            String description,
            BigDecimal price,
            Integer stockQuantity) {

        if (blank(name)
                || name.trim().length() < 2
                || name.trim().length() > 150) {
            throw new IllegalArgumentException(
                    "Product name must be between 2 and 150 characters"
            );
        }

        if (blank(brand)) {
            throw new IllegalArgumentException(
                    "Brand is required"
            );
        }

        if (blank(category)) {
            throw new IllegalArgumentException(
                    "Category is required"
            );
        }

        if (description != null
                && description.length() > 1000) {
            throw new IllegalArgumentException(
                    "Description cannot exceed 1000 characters"
            );
        }

        if (price == null
                || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Price must be greater than zero"
            );
        }

        if (stockQuantity == null
                || stockQuantity < 0) {
            throw new IllegalArgumentException(
                    "Stock quantity cannot be negative"
            );
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
