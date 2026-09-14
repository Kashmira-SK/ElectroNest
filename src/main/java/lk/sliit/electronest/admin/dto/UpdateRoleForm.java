package lk.sliit.electronest.admin.dto;

import lk.sliit.electronest.common.model.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateRoleForm {

    @NotNull(message = "Please select a new role")
    private Role newRole;

    private String reason;
}
