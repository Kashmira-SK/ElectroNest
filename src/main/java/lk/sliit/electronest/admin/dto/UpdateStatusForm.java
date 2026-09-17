package lk.sliit.electronest.admin.dto;

import lk.sliit.electronest.common.model.AccountStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateStatusForm {

    @NotNull(message = "Please select a new status")
    private AccountStatus newStatus;

    @Size(max = 255, message = "Keep the reason within 255 characters")
    private String reason;
}
