package lk.sliit.electronest.admin.repository;

import com.electronest.admin.entity.AccountStatus;
import com.electronest.admin.entity.Role;
import com.electronest.admin.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRole(Role role);

    List<User> findByStatus(AccountStatus status);

    // Used by the admin "search users" screen (searches name or email)
    List<User> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email);

    long countByRole(Role role);

    long countByStatus(AccountStatus status);
}
