package lk.sliit.electronest.admin.dto;

import lk.sliit.electronest.common.model.AccountStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStatusForm {

    @NotNull(message = "Please select a new status")
    private AccountStatus newStatus;

    private String reason;
}
