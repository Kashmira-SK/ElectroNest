package lk.sliit.electronest.catalog.controller;

import lk.sliit.electronest.catalog.dto.ProductForm;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.LinkedHashMap;
import java.util.Map;

/** Feedback shared only by the product editor's browser endpoints. */
final class ProductFormFeedback {
    static final String STOCK_MESSAGE = "Stock quantity must be a whole number between 0 and 2147483647.";
    private static final Map<String, String> CONVERSION_MESSAGES = Map.of(
            "price", "Enter a valid price, for example 25.50.",
            "stockQuantity", STOCK_MESSAGE,
            "ramGb", "RAM must be a whole number between 1 and 4096 GB.",
            "storageGb", "Storage must be a whole number between 1 and 1048576 GB.");

    private ProductFormFeedback() {}

    static Map<String, Object> attributes(ProductForm form, BindingResult errors) {
        var safeErrors = new BeanPropertyBindingResult(form, "product");
        errors.getGlobalErrors().forEach(safeErrors::addError);
        var fieldErrors = errors.getFieldErrors().stream().sorted(java.util.Comparator.comparingInt(error ->
                error.isBindingFailure() ? 0 : "NotBlank".equals(error.getCode()) || "NotNull".equals(error.getCode()) ? 1 : 2)).toList();
        for (FieldError error : fieldErrors) {
            if (safeErrors.hasFieldErrors(error.getField())) continue;
            if (error.isBindingFailure() && !CONVERSION_MESSAGES.containsKey(error.getField())) {
                safeErrors.reject("product.invalid", "The product details could not be read. Return to your products and try again.");
                continue;
            }
            String message = error.isBindingFailure()
                    ? CONVERSION_MESSAGES.get(error.getField()) : error.getDefaultMessage();
            safeErrors.addError(new FieldError("product", error.getField(), error.getRejectedValue(),
                    error.isBindingFailure(), null, null, message));
        }
        // Number inputs otherwise discard rejected values during Thymeleaf rendering.
        Map<String, Object> values = new LinkedHashMap<>();
        CONVERSION_MESSAGES.keySet().forEach(field -> values.put(field, errors.getFieldValue(field)));
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("product", form);
        attributes.put(BindingResult.MODEL_KEY_PREFIX + "product", safeErrors);
        attributes.put("productFieldValues", values);
        return attributes;
    }

    static void serviceError(BindingResult errors, RuntimeException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        String field = switch (message) {
            case "Product name is required", "Product name must be between 2 and 150 characters" -> "name";
            case "Brand is required and must be 255 characters or fewer" -> "brand";
            case "Category is required and must be 255 characters or fewer" -> "category";
            case "Description cannot exceed 1000 characters" -> "description";
            case "Price must be greater than zero", "Price must have at most two decimal places and 36 integer digits" -> "price";
            case "Stock cannot be negative" -> "stockQuantity";
            case "RAM must be between 1 and 4096 GB" -> "ramGb";
            case "Storage must be between 1 and 1048576 GB" -> "storageGb";
            default -> null;
        };
        if (field != null) {
            errors.rejectValue(field, "product.invalid", message);
        } else {
            String safeMessage = switch (message) {
                case "Only approved vendors can manage products", "Only approved vendors can create product listings",
                     "You can only edit your own products", "Vendor does not own this product", "Product not found" -> message;
                default -> "Could not save the product. Please try again.";
            };
            errors.reject("product.save", safeMessage);
        }
    }
}
