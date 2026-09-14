package lk.sliit.electronest.common.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Core User entity - shared by all three roles (Admin, Vendor, Customer).
 * The "role" column is what differentiates them (RBAC).
 *
 * NOTE: table is named "users" (not "user") because USER is a reserved
 * keyword in PostgreSQL and would break every query.
 *
 * Owner: Navodya W.M.G.G.G. (IT25102263)
 * Module: Admin Dashboard, Reports & User Account Management
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    // BCrypt-hashed password - never store plain text
    @Column(nullable = false)
    private String password;

    @Column(length = 20)
    private String contactNumber;

    @Column(length = 100)
    private String deliveryName;

    @Column(length = 20)
    private String deliveryPhone;

    @Column(length = 200)
    private String deliveryAddressLine1;

    @Column(length = 200)
    private String deliveryAddressLine2;

    @Column(length = 100)
    private String deliveryCity;

    @Column(length = 20)
    private String deliveryPostalCode;

    @Column(length = 100)
    private String deliveryCountry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
