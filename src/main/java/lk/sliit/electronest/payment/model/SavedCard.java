package lk.sliit.electronest.payment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "saved_cards")
@Getter
@Setter
public class SavedCard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long customerId;
    @Column(nullable = false, length = 100)
    private String holder;
    @Column(nullable = false, length = 4)
    private String last4;
    @Column(nullable = false, length = 5)
    private String expiry;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;
    // Salted one-way fingerprint used only to detect duplicate cards.
    @Column(nullable = false, length = 60)
    private String fingerprint;
}
