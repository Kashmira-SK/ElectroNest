package lk.sliit.electronest.payment.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.payment.model.PaymentMethod;
import lk.sliit.electronest.payment.model.SavedCard;
import lk.sliit.electronest.payment.repository.SavedCardRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.YearMonth;
import java.util.List;

@Service
public class SavedCardService {
    private final SavedCardRepository repository;
    private final EntityManager entityManager;
    private final BCryptPasswordEncoder fingerprints = new BCryptPasswordEncoder();

    public SavedCardService(SavedCardRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    public List<SavedCard> list(User user) {
        return repository.findByCustomerIdOrderByIdAsc(user.getId());
    }

    public SavedCard owned(Long id, User user) {
        return repository.findByIdAndCustomerId(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Saved card is no longer available. Choose another payment method."));
    }

    @Transactional
    public SavedCard save(User user, PaymentMethod method, String holder, String number, String expiry) {
        if (method != PaymentMethod.CREDIT_CARD && method != PaymentMethod.DEBIT_CARD) {
            throw new IllegalArgumentException("Choose Credit Card or Debit Card.");
        }
        if (holder == null || holder.isBlank() || holder.trim().length() > 100) {
            throw new IllegalArgumentException("Enter the name on the card (up to 100 characters).");
        }
        String digits = number == null ? "" : number.replaceAll("[\\s-]", "");
        if (!digits.matches("\\d{12,19}")) {
            throw new IllegalArgumentException("Enter a valid card number.");
        }
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (doubleDigit) { digit *= 2; if (digit > 9) digit -= 9; }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        if (sum % 10 != 0) throw new IllegalArgumentException("Enter a valid card number.");
        validateExpiry(expiry);
        // Serialize saves for an account so repeated submissions cannot create duplicates.
        entityManager.find(User.class, user.getId(), LockModeType.PESSIMISTIC_WRITE);
        SavedCard card = list(user).stream()
                .filter(existing -> fingerprints.matches(digits, existing.getFingerprint()))
                .findFirst().orElseGet(SavedCard::new);
        card.setCustomerId(user.getId());
        card.setHolder(holder.trim());
        card.setMethod(method);
        card.setExpiry(expiry);
        card.setLast4(digits.substring(digits.length() - 4));
        if (card.getFingerprint() == null) card.setFingerprint(fingerprints.encode(digits));
        return repository.save(card);
    }

    public void validateExpiry(String expiry) {
        try {
            if (expiry == null || !expiry.matches("\\d{2}/\\d{2}")) throw new IllegalArgumentException();
            YearMonth date = YearMonth.of(2000 + Integer.parseInt(expiry.substring(3)),
                    Integer.parseInt(expiry.substring(0, 2)));
            if (date.isBefore(YearMonth.now())) throw new IllegalArgumentException();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Enter a current or future expiry as MM/YY.");
        }
    }

    @Transactional
    public void remove(Long id, User user) {
        repository.delete(owned(id, user));
    }
}
