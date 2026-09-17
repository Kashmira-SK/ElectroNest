package lk.sliit.electronest.common.security;

import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String enteredEmail = email.trim();
        User user = userRepository.findByEmail(enteredEmail)
                .or(() -> userRepository.findByEmail(enteredEmail.toLowerCase(java.util.Locale.ROOT)))
                .orElseThrow(() -> new UsernameNotFoundException("No user found with email: " + email));
        return new CustomUserDetails(user);
    }
}
