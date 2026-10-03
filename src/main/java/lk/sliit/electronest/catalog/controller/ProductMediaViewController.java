package lk.sliit.electronest.catalog.controller;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.dto.ProductForm;
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
import org.springframework.validation.BindingResult;
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
            @ModelAttribute("product") ProductForm form,
            BindingResult errors,
            @RequestParam(value = "images", required = false) MultipartFile[] images,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {

        Long id = form.getId();
        boolean editing = id != null;
        String name = form.getName();
        String brand = form.getBrand();
        String category = form.getCategory();
        String description = form.getDescription();
        BigDecimal price = form.getPrice();
        Integer stockQuantity = form.getStockQuantity();
        Integer ramGb = form.getRamGb();
        Integer storageGb = form.getStorageGb();
        String externalImageUrl = form.getExternalImageUrl();
        List<String> removeImages = form.getRemoveImages();
        List<MultipartFile> uploads = nonEmpty(images);
        String imageError = null;

        try {
            Vendor vendor = vendorService.getVendorForUser(
                    currentUser.getUser().getId()
            );

            if (vendor.getStatus() != VendorStatus.APPROVED) {
                throw new IllegalStateException(
                        "Only approved vendors can manage products"
                );
            }

            validate(form, errors);
            if (!blank(externalImageUrl)) {
                try {
                    java.net.URI external = java.net.URI.create(externalImageUrl.trim());
                    if (external.getHost() == null || !("https".equalsIgnoreCase(external.getScheme())
                            || "http".equalsIgnoreCase(external.getScheme())) || externalImageUrl.length() > 1000) {
                        errors.rejectValue("externalImageUrl", "image.url", "Enter a valid HTTPS or HTTP image URL (up to 1000 characters).");
                    }
                } catch (IllegalArgumentException ex) {
                    errors.rejectValue("externalImageUrl", "image.url", "Enter a valid HTTPS or HTTP image URL (up to 1000 characters).");
                }
            }
            if (uploads.size() > 8) imageError = "You can upload up to 8 product images.";
            if (!editing && uploads.isEmpty() && blank(externalImageUrl)) {
                imageError = "Add at least one product image or image URL.";
            }
            if (errors.hasErrors() || imageError != null) {
                return returnToForm(form, errors, uploads, imageError, redirectAttributes);
            }

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
            List<String> removals = removeImages == null ? List.of() : removeImages;
            if (!oldGallery.containsAll(removals)) {
                return returnToForm(form, errors, uploads, "You can only remove images from this product.", redirectAttributes);
            }
            List<String> remainingGallery = new ArrayList<>(oldGallery);
            remainingGallery.removeAll(removals);
            int gallerySize = !uploads.isEmpty() ? uploads.size()
                    : remainingGallery.size();
            if (!blank(externalImageUrl) && (!uploads.isEmpty() || !remainingGallery.contains(externalImageUrl.trim()))) {
                gallerySize++;
            }
            if ((!uploads.isEmpty() || !blank(externalImageUrl)) && gallerySize > 8) {
                return returnToForm(form, errors, uploads,
                        "Use up to 8 images in total, including the external URL.", redirectAttributes);
            }

            List<String> stored = new ArrayList<>();

            try {
                for (MultipartFile image : uploads) {
                    try {
                        stored.add(imageStorage.store(image));
                    } catch (IllegalArgumentException ex) {
                        imageError = switch (ex.getMessage() == null ? "" : ex.getMessage()) {
                            case "Image file is empty", "Each product image must be 8 MB or smaller",
                                 "Product images must be JPG, PNG or WEBP", "Invalid image filename" -> ex.getMessage();
                            default -> "Could not read this product image. Choose a JPG, PNG or WEBP file.";
                        };
                        throw ex;
                    } catch (IllegalStateException ex) {
                        imageError = "Could not store the product image. Please try again.";
                        throw ex;
                    }
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

                    product.setImageUrls(newGallery);
                    product.setImageUrl(newGallery.get(0));

                } else if (!blank(externalImageUrl)) {
                    String external =
                            externalImageUrl.trim();

                    List<String> existingGallery =
                            new ArrayList<>(remainingGallery);

                    existingGallery.remove(external);
                    existingGallery.add(0, external);

                    product.setImageUrls(existingGallery);
                    product.setImageUrl(external);
                } else if (!removals.isEmpty()) {
                    product.setImageUrls(remainingGallery);
                    product.setImageUrl(remainingGallery.isEmpty() ? null : remainingGallery.get(0));
                }

                Product saved =
                        productService.createProduct(product);

                if (!stored.isEmpty() || !removals.isEmpty()) {
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
            if (imageError == null) ProductFormFeedback.serviceError(errors, ex);
            return returnToForm(form, errors, uploads, imageError, redirectAttributes);
        }
    }

    private String returnToForm(ProductForm form, BindingResult errors, List<MultipartFile> uploads,
                                String imageError, RedirectAttributes redirectAttributes) {
        ProductFormFeedback.attributes(form, errors).forEach(redirectAttributes::addFlashAttribute);
        if (imageError != null) redirectAttributes.addFlashAttribute("imageValidationError", imageError);
        if (!uploads.isEmpty()) redirectAttributes.addFlashAttribute("reselectProductImages", true);
        return form.getId() == null ? "redirect:/vendor/products/new"
                : "redirect:/vendor/products/" + form.getId() + "/edit";
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

    private void validate(ProductForm form, BindingResult errors) {
        // Collect the existing multipart/service rules without stopping at the first error.
        if (blank(form.getName())) {
            errors.rejectValue("name", "required", "Product name is required");
        } else if (form.getName().trim().length() < 2 || form.getName().trim().length() > 150) {
            errors.rejectValue("name", "size", "Product name must be between 2 and 150 characters");
        }
        if (blank(form.getBrand())) errors.rejectValue("brand", "required", "Brand is required");
        else if (form.getBrand().trim().length() > 255) errors.rejectValue("brand", "size", "Brand must be 255 characters or fewer");
        if (blank(form.getCategory())) errors.rejectValue("category", "required", "Category is required");
        else if (form.getCategory().trim().length() > 255) errors.rejectValue("category", "size", "Category must be 255 characters or fewer");
        if (form.getDescription() != null && form.getDescription().length() > 1000) {
            errors.rejectValue("description", "size", "Description cannot exceed 1000 characters");
        }
        if (!errors.hasFieldErrors("price")) {
            BigDecimal price = form.getPrice();
            if (price == null) errors.rejectValue("price", "required", "Price is required");
            else if (price.signum() <= 0) errors.rejectValue("price", "positive", "Price must be greater than zero");
            else if (price.scale() > 2 || price.precision() - price.scale() > 36) {
                errors.rejectValue("price", "digits", "Price must have at most two decimal places and 36 integer digits");
            }
        }
        if (!errors.hasFieldErrors("stockQuantity") && (form.getStockQuantity() == null || form.getStockQuantity() < 0)) {
            errors.rejectValue("stockQuantity", "range", ProductFormFeedback.STOCK_MESSAGE);
        }
        if (!errors.hasFieldErrors("ramGb") && form.getRamGb() != null && (form.getRamGb() < 1 || form.getRamGb() > 4096)) {
            errors.rejectValue("ramGb", "range", "RAM must be between 1 and 4096 GB");
        }
        if (!errors.hasFieldErrors("storageGb") && form.getStorageGb() != null && (form.getStorageGb() < 1 || form.getStorageGb() > 1048576)) {
            errors.rejectValue("storageGb", "range", "Storage must be between 1 and 1048576 GB");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
