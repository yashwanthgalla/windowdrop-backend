package com.example.dropcode_backend.security;

import com.example.dropcode_backend.entity.UserEntity;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Getter
public class FirebaseUserDetails implements UserDetails {

    private final UserEntity userEntity;
    private final String firebaseUid;
    private final String email;
    private final String displayName;
    private final boolean emailVerified;

    public FirebaseUserDetails(UserEntity userEntity, String firebaseUid, String email, String displayName, boolean emailVerified) {
        this.userEntity = userEntity;
        this.firebaseUid = firebaseUid;
        this.email = email;
        this.displayName = displayName;
        this.emailVerified = emailVerified;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return firebaseUid;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return userEntity == null || !"SUSPENDED".equalsIgnoreCase(userEntity.getAccountStatus());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return userEntity == null || "ACTIVE".equalsIgnoreCase(userEntity.getAccountStatus());
    }
}
