package com.ute.uteexpress.controller;

import com.ute.uteexpress.dto.admin.RoleUpdateForm;
import com.ute.uteexpress.enums.UserStatus;
import com.ute.uteexpress.service.AdminRuleException;
import com.ute.uteexpress.service.AdminUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AdminUserService admin;

    public AdminController(AdminUserService admin) { this.admin = admin; }

    @InitBinder("roleForm")
    void roleFields(WebDataBinder binder) {
        // MVC also offers the URI variable id to the binder; the form has no id property.
        // The target remains the separately validated @PathVariable, never a form field.
        binder.setAllowedFields("roleIds", "_roleIds", "_csrf", "id");
    }

    @GetMapping({"", "/"})
    public String dashboard(Model model) {
        model.addAttribute("stats", admin.dashboard());
        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String users(@RequestParam(defaultValue = "") @Size(max = 150) String q,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, Model model) {
        model.addAttribute("users", admin.list(q, status, PageRequest.of(page, size)));
        model.addAttribute("q", q);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", UserStatus.values());
        model.addAttribute("size", size);
        return "admin/users";
    }

    @GetMapping("/users/{id}")
    public String detail(@PathVariable @Positive Long id, Model model) {
        var user = admin.detail(id);
        var available = admin.editableRoles();
        RoleUpdateForm form = new RoleUpdateForm();
        Set<Long> availableIds = available.stream().map(role -> role.id())
            .collect(java.util.stream.Collectors.toSet());
        form.setRoleIds(user.roles().stream().map(role -> role.id()).filter(availableIds::contains).toList());
        model.addAttribute("user", user);
        model.addAttribute("availableRoles", available);
        model.addAttribute("roleForm", form);
        return "admin/user-detail";
    }

    @PostMapping("/users/{id}/lock")
    public String lock(@PathVariable @Positive Long id, RedirectAttributes flash) {
        return mutate(id, flash, () -> admin.lock(id), "Tài khoản đã được khóa.");
    }

    @PostMapping("/users/{id}/unlock")
    public String unlock(@PathVariable @Positive Long id, RedirectAttributes flash) {
        return mutate(id, flash, () -> admin.unlock(id), "Tài khoản đã được mở khóa.");
    }

    @PostMapping("/users/{id}/roles")
    public String roles(@PathVariable @Positive Long id, @Valid @ModelAttribute("roleForm") RoleUpdateForm form,
            BindingResult errors, RedirectAttributes flash) {
        if (errors.hasErrors() || errors.getSuppressedFields().length > 0) {
            flash.addFlashAttribute("error", "Yêu cầu phân quyền không hợp lệ.");
            return "redirect:/admin/users/" + id;
        }
        return mutate(id, flash, () -> admin.updateRoles(id, form), "Quyền tài khoản đã được cập nhật.");
    }

    private String mutate(Long id, RedirectAttributes flash, Runnable action, String message) {
        try {
            action.run();
            flash.addFlashAttribute("notice", message);
        } catch (AdminRuleException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        } catch (DataAccessException exception) {
            flash.addFlashAttribute("error", "Không thể cập nhật dữ liệu lúc này. Vui lòng thử lại sau.");
        }
        return "redirect:/admin/users/" + id;
    }
}
