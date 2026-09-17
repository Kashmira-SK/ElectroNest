package lk.sliit.electronest.admin.dto;

import lk.sliit.electronest.common.model.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateRoleForm {

    @NotNull(message = "Please select a new role")
    private Role newRole;

    @Size(max = 255, message = "Keep the reason within 255 characters")
    private String reason;
}
