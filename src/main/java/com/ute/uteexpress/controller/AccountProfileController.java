package com.ute.uteexpress.controller;

import com.ute.uteexpress.dto.account.AccountForms;
import com.ute.uteexpress.service.AccountProfileService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/account/profile")
@PreAuthorize("hasRole('USER')")
public class AccountProfileController {
    private final AccountProfileService service;
    public AccountProfileController(AccountProfileService service){this.service=service;}
    @GetMapping
    public String page(Model model) {
        var account=service.view();model.addAttribute("profile",new AccountForms.Profile(account.fullName(),account.phoneNumber()));
        return render(model);
    }
    private String render(Model model) {
        model.addAttribute("account",service.view());return "auth/profile";
    }
    @PostMapping
    public String update(@Valid @ModelAttribute("profile") AccountForms.Profile form,BindingResult errors,Model model) {
        if(errors.hasErrors())return render(model);
        try{service.update(form);}catch(DataIntegrityViolationException conflict){
            errors.rejectValue("phoneNumber","account.phone","Không thể sử dụng số điện thoại này.");return render(model);
        }
        return "redirect:/account/profile?saved";
    }
    @PostMapping({"/addresses","/addresses/{id}"})
    public String address(@PathVariable(required=false) Long id,@Valid @ModelAttribute("address") AccountForms.Address form,
            BindingResult errors,Model model) {
        if(errors.hasErrors()){
            var account=service.view();model.addAttribute("profile",new AccountForms.Profile(account.fullName(),account.phoneNumber()));
            model.addAttribute("addressError","Địa chỉ chưa được lưu. Kiểm tra các trường bắt buộc, độ dài và số điện thoại.");
            model.addAttribute("editAddressId",id);return render(model);
        }
        service.saveAddress(id,form);return "redirect:/account/profile?saved";
    }
    @PostMapping("/addresses/{id}/delete")
    public String delete(@PathVariable Long id){service.deleteAddress(id);return "redirect:/account/profile?saved";}
}
