package lk.sliit.electronest.admin.dto;

import lk.sliit.electronest.common.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Bound to templates/auth/register.html via th:object="${registerForm}".
 * Public registration only ever creates CUSTOMER or VENDOR accounts -
 * see AuthController for why ADMIN is excluded from the dropdown.
 */
@Data
public class RegisterForm {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;

    private String contactNumber;

    @NotNull(message = "Please select a role")
    private Role role;
}
