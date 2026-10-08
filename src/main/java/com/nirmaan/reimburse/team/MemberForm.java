package com.nirmaan.reimburse.team;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberForm(@NotBlank @Size(max = 120) String name, @NotBlank @Email @Size(max = 254) String email) {
}
