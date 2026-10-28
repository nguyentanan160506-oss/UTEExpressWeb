package com.ute.uteexpress.demo;

import com.ute.uteexpress.dto.account.AccountForms;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.UserStatus;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.AccountProfileService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountProfilePolicyTests {
    UserRepository users;UserAddressRepository addresses;UserRoleRepository roles;AccountProfileService service;User user;
    @BeforeEach void prepare(){
        users=mock(UserRepository.class);addresses=mock(UserAddressRepository.class);roles=mock(UserRoleRepository.class);
        service=new AccountProfileService(users,addresses,roles);user=new User();user.setId(1L);user.setEmail("profile@example.test");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(roles.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user.getEmail(),null,"ROLE_USER"));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    AccountForms.Address form(boolean defaults){return new AccountForms.Address("Liên hệ QA","0982300000","Đà Nẵng","Khu vực demo","Phường demo","Địa chỉ mô phỏng",defaults);}
    @Test void lockedAccountCannotReadOwnPersonalData(){user.setStatus(UserStatus.LOCKED);assertEquals(403,assertThrows(ResponseStatusException.class,service::view).getStatusCode().value());verifyNoInteractions(addresses);}
    @Test void databaseRoleRevocationOverridesStaleSessionAuthority(){when(roles.findRoleNamesByUserId(1L)).thenReturn(List.of());assertEquals(403,assertThrows(ResponseStatusException.class,()->service.saveAddress(null,form(true))).getStatusCode().value());verifyNoInteractions(addresses);}
    @Test void selectingNewDefaultClearsPreviousDefaultUnderOwnerLock(){
        UserAddress old=new UserAddress();old.setId(9L);old.setDefault(true);
        when(addresses.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(old));service.saveAddress(null,form(true));
        assertFalse(old.isDefault());verify(users).findByEmailForUpdate(user.getEmail());
        var saved=org.mockito.ArgumentCaptor.forClass(UserAddress.class);verify(addresses).save(saved.capture());
        assertTrue(saved.getValue().isDefault());assertSame(user,saved.getValue().getUser());
    }
    @Test void cannotUpdateForeignOrMissingAddress(){when(addresses.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());assertEquals(404,assertThrows(ResponseStatusException.class,()->service.saveAddress(99L,form(false))).getStatusCode().value());verify(addresses,never()).save(any());}
    @Test void cannotDeleteForeignOrMissingAddress(){when(addresses.findByIdAndUserId(99L,1L)).thenReturn(Optional.empty());assertEquals(404,assertThrows(ResponseStatusException.class,()->service.deleteAddress(99L)).getStatusCode().value());verify(addresses,never()).delete(any());}
    @Test void addressCountCannotGrowWithoutBound(){when(addresses.findByUserIdOrderByIdAsc(1L)).thenReturn(Collections.nCopies(50,new UserAddress()));assertEquals(400,assertThrows(ResponseStatusException.class,()->service.saveAddress(null,form(false))).getStatusCode().value());verify(addresses,never()).save(any());}
    @Test void profileUpdateCannotChangeLoginOrStatus(){
        service.update(new AccountForms.Profile(" Updated name ","0982300001"));assertEquals("Updated name",user.getFullName());
        assertEquals("profile@example.test",user.getEmail());assertEquals(UserStatus.ACTIVE,user.getStatus());verify(users).flush();
    }
    @Test void uncheckedDefaultCheckboxMustBindWithoutRequiringClientField(){
        var request=new org.springframework.mock.web.MockHttpServletRequest();
        request.addParameter("contactName","Liên hệ QA");request.addParameter("contactPhone","0982300000");
        request.addParameter("province","Đà Nẵng");request.addParameter("district","Khu vực demo");
        request.addParameter("ward","Phường demo");request.addParameter("detailAddress","Địa điểm mô phỏng");
        var binder=new org.springframework.web.bind.ServletRequestDataBinder(null,"address");
        binder.setTargetType(org.springframework.core.ResolvableType.forClass(AccountForms.Address.class));binder.construct(request);
        assertFalse(binder.getBindingResult().hasErrors(),"An unchecked optional checkbox sends no request parameter");
    }
}
