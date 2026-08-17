package lk.sliit.electronest.admin.config;

import lk.sliit.electronest.admin.entity.AccountStatus;
import lk.sliit.electronest.admin.entity.Role;
import lk.sliit.electronest.admin.entity.User;
import lk.sliit.electronest.admin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Runs once on startup. If no admin account exists yet, creates one so you
 * have a way to log in and test the /admin/** pages immediately without
 * manually inserting rows into PostgreSQL.
 *
 * Default login (CHANGE THE PASSWORD before your demo/viva):
 *   email:    admin@electronest.lk
 *   password: Admin@123
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.countByRole(Role.ADMIN) == 0) {
            User admin = User.builder()
                    .fullName("System Administrator")
                    .email("admin@electronest.lk")
                    .password(passwordEncoder.encode("Admin@123"))
                    .contactNumber("0770000000")
                    .role(Role.ADMIN)
                    .status(AccountStatus.ACTIVE)
                    .build();

            userRepository.save(admin);
            System.out.println("=========================================================");
            System.out.println(" Default admin account created:");
            System.out.println("   email:    admin@electronest.lk");
            System.out.println("   password: Admin@123");
            System.out.println(" -> Log in at http://localhost:8080/login");
            System.out.println("=========================================================");
        }
    }
}
