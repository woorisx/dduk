package com.dduk.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnomalyLogStatusUpdateDto {
    @NotBlank
    private String status;

    private String reviewNote;

    private String reviewedBy;
}
