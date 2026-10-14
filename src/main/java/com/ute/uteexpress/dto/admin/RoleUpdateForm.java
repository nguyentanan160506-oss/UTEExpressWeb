package com.ute.uteexpress.dto.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

public class RoleUpdateForm {
    @NotNull
    @Size(max = 3, message = "Yêu cầu phân quyền không hợp lệ.")
    private List<@NotNull @Positive Long> roleIds = new ArrayList<>();

    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }
}
