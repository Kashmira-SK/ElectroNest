package lk.sliit.electronest.payment.service;

import jakarta.persistence.EntityManager;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.payment.model.*;
import lk.sliit.electronest.payment.repository.SavedCardRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class SavedCardServiceTest {
    private final SavedCardRepository repository = mock(SavedCardRepository.class);
    private final SavedCardService service = new SavedCardService(repository, mock(EntityManager.class));
    private final User user = User.builder().id(2L).build();

    @Test
    void savesMultipleCardsAndUpdatesDuplicateWithoutKeepingNumber() {
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        SavedCard first = service.save(user, PaymentMethod.CREDIT_CARD, "Customer", "4242424242424242", "12/39");
        first.setId(1L);
        assertEquals("4242", first.getLast4());
        assertNotEquals("4242424242424242", first.getFingerprint());
        when(repository.findByCustomerIdOrderByIdAsc(2L)).thenReturn(List.of(first));
        SavedCard duplicate = service.save(user, PaymentMethod.DEBIT_CARD, "Updated", "4242 4242 4242 4242", "11/39");
        assertSame(first, duplicate);
        assertEquals("Updated", duplicate.getHolder());
        SavedCard second = service.save(user, PaymentMethod.CREDIT_CARD, "Customer", "5555555555554444", "12/39");
        assertNotSame(first, second);
        assertEquals(2L, second.getCustomerId());
    }

    @Test
    void rejectsInvalidOrExpiredCards() {
        assertThrows(IllegalArgumentException.class, () -> service.save(user, PaymentMethod.CREDIT_CARD, "Customer", "4242424242424241", "12/39"));
        assertThrows(IllegalArgumentException.class, () -> service.save(user, PaymentMethod.CREDIT_CARD, "Customer", "4242424242424242", "12/20"));
        assertThrows(IllegalArgumentException.class, () -> service.save(user, PaymentMethod.CASH_ON_DELIVERY, "Customer", "4242424242424242", "12/39"));
        verify(repository, never()).save(any());
    }

    @Test
    void removalAndLookupRequireAccountOwnership() {
        when(repository.findByIdAndCustomerId(9L, 2L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.owned(9L, user));
        assertThrows(IllegalArgumentException.class, () -> service.remove(9L, user));
        verify(repository, never()).delete(any());
        SavedCard card = new SavedCard();
        when(repository.findByIdAndCustomerId(1L, 2L)).thenReturn(Optional.of(card));
        service.remove(1L, user);
        verify(repository).delete(card);
    }
}
