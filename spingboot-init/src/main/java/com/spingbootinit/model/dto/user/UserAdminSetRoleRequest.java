package com.spingbootinit.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserAdminSetRoleRequest {
    @NotBlank(message = "角色不能为空")
    private String role;
}
