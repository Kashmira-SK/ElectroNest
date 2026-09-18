package lk.sliit.electronest.catalog.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Data
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Product name is required")
    @Size(min = 2, max = 150, message = "Product name must be between 2 and 150 characters")
    @Column(nullable = false)
    private String name;

    @Size(max = 255, message = "Brand must be 255 characters or fewer")
    @NotBlank(message = "Brand is required")
    private String brand;

    @Size(max = 255, message = "Category must be 255 characters or fewer")
    @NotBlank(message = "Category is required")
    private String category;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    @Column(length = 1000)
    private String description;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be greater than zero")
    @jakarta.validation.constraints.Digits(integer = 36, fraction = 2, message = "Price must have at most two decimal places")
    private BigDecimal price;

    @NotNull(message = "Stock quantity is required")
    @Min(value = 0, message = "Stock quantity cannot be negative")
    private Integer stockQuantity;

    @Column(length = 1000)
    private String imageUrl;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "product_images",
            joinColumns = @JoinColumn(name = "product_id")
    )
    @OrderColumn(name = "image_order")
    @Column(name = "image_url", length = 1000)
    private List<String> imageUrls = new ArrayList<>();

    private Boolean outOfStock = false;

    @NotNull(message = "Vendor ID is required")
    private Long vendorId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls == null
                ? new ArrayList<>()
                : new ArrayList<>(imageUrls);
    }

}