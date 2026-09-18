package lk.sliit.electronest.admin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Records administrator changes to account roles and status. */
@Entity
@Table(name = "role_change_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The user whose role/status was changed
    @Column(nullable = false)
    private Long targetUserId;

    @Column(nullable = false, length = 150)
    private String targetUserEmail;

    // The admin who performed the change
    @Column(nullable = false)
    private Long performedByUserId;

    @Column(nullable = false, length = 150)
    private String performedByEmail;

    @Column(length = 30)
    private String previousRole;

    @Column(length = 30)
    private String newRole;

    @Column(length = 30)
    private String previousStatus;

    @Column(length = 30)
    private String newStatus;

    @Column(length = 255)
    private String reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @PrePersist
    protected void onCreate() {
        this.changedAt = LocalDateTime.now();
    }
}
