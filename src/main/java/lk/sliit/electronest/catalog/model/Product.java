package lk.sliit.electronest.catalog.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Data
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String brand;
    private String category;

    @Column(length = 1000)
    private String description;

    private BigDecimal price;
    private Integer stockQuantity;
    private String imageUrl;
    private Boolean outOfStock = false;
    private Long vendorId;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}