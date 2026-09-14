package com.electronest.admin.dto;

import com.electronest.admin.entity.AccountStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStatusForm {

    @NotNull(message = "Please select a new status")
    private AccountStatus newStatus;

    private String reason;
}
