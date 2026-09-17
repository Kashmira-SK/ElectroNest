package lk.sliit.electronest.common.repository;

import lk.sliit.electronest.common.model.AccountStatus;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);

    List<User> findByRole(Role role);

    List<User> findByStatus(AccountStatus status);

    // Used by the admin "search users" screen (searches name or email)
    List<User> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email);

    long countByRole(Role role);

    long countByStatus(AccountStatus status);
}
