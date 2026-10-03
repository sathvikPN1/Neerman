package com.nirmaan.reimburse.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final PrincipalLoader loader;

    public AppUserDetailsService(PrincipalLoader loader) {
        this.loader = loader;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return loader.loadByEmail(username).orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
