package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.account.AccountForms;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.UserStatus;
import com.ute.uteexpress.repository.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@PreAuthorize("hasRole('USER')")
public class AccountProfileService {
    private final UserRepository users;
    private final UserAddressRepository addresses;
    private final UserRoleRepository roles;
    public AccountProfileService(UserRepository users, UserAddressRepository addresses,UserRoleRepository roles) {
        this.users=users; this.addresses=addresses;this.roles=roles;
    }
    public record AddressView(Long id,String contactName,String contactPhone,String province,String district,
            String ward,String detailAddress,boolean defaultAddress) { }
    public record View(String email,String fullName,String phoneNumber,List<AddressView> addresses) { }
    private User current(boolean lock) {
        String email=SecurityContextHolder.getContext().getAuthentication().getName();
        User user=(lock?users.findByEmailForUpdate(email):users.findByEmail(email))
            .filter(u->u.getStatus()==UserStatus.ACTIVE)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN));
        if(!roles.findRoleNamesByUserId(user.getId()).contains("ROLE_USER"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return user;
    }
    @Transactional(readOnly=true)
    public View view() {
        User user=current(false);
        return new View(user.getEmail(),user.getFullName(),user.getPhoneNumber(),
            addresses.findByUserIdOrderByIdAsc(user.getId()).stream().map(a->new AddressView(a.getId(),
                a.getContactName(),a.getContactPhone(),a.getProvince(),a.getDistrict(),a.getWard(),
                a.getDetailAddress(),a.isDefault())).toList());
    }
    @Transactional
    public void update(AccountForms.Profile form) {
        User user=current(true);
        user.setFullName(form.fullName().trim()); user.setPhoneNumber(form.phoneNumber().trim());
        users.flush();
    }
    @Transactional
    public void saveAddress(Long id,AccountForms.Address form) {
        User user=current(true); // Serialize default changes and address count for this owner.
        List<UserAddress> owned=addresses.findByUserIdOrderByIdAsc(user.getId());
        if(id==null&&owned.size()>=50)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tối đa 50 địa chỉ.");
        UserAddress address=id==null?new UserAddress():owned.stream().filter(a->a.getId().equals(id)).findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean makeDefault=Boolean.TRUE.equals(form.defaultAddress()); // Unchecked HTML checkbox is absent, not "false".
        if(makeDefault)owned.forEach(a->a.setDefault(false));
        address.setUser(user);address.setContactName(form.contactName().trim());address.setContactPhone(form.contactPhone().trim());
        address.setProvince(form.province().trim());address.setDistrict(form.district().trim());address.setWard(form.ward().trim());
        address.setDetailAddress(form.detailAddress().trim());address.setDefault(makeDefault);addresses.save(address);
    }
    @Transactional
    public void deleteAddress(Long id) {
        User user=current(true);
        addresses.delete(addresses.findByIdAndUserId(id,user.getId())
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }
}
