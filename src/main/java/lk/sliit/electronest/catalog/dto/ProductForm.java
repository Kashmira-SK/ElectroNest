package lk.sliit.electronest.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductForm {

    private Long id;

    @NotBlank(message = "Product name is required")
    @Size(min = 2, max = 150, message = "Product name must be between 2 and 150 characters")
    private String name;

    @Size(max = 255, message = "Brand must be 255 characters or fewer")
    @NotBlank(message = "Brand is required")
    private String brand;

    @Size(max = 255, message = "Category must be 255 characters or fewer")
    @NotBlank(message = "Category is required")
    private String category;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be greater than zero")
    @jakarta.validation.constraints.Digits(integer = 36, fraction = 2, message = "Price must have at most two decimal places")
    private BigDecimal price;

    @NotNull(message = "Stock quantity is required")
    @Min(value = 0, message = "Stock quantity cannot be negative")
    private Integer stockQuantity;

    private String imageUrl;
}
