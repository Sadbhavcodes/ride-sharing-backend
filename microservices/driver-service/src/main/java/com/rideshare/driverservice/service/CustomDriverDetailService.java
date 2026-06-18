package com.rideshare.driverservice.service;

import com.rideshare.driverservice.entity.Driver;
import com.rideshare.driverservice.repository.DriverRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CustomDriverDetailService implements UserDetailsService {

    private final DriverRepository driverRepository;

    public CustomDriverDetailService(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    /**
     * The username here is the driver's userId (stored as a String in the JWT subject).
     */
    @Override
    public UserDetails loadUserByUsername(String userId)
            throws UsernameNotFoundException {

        Optional<Driver> driver = driverRepository.findByUserId(Long.parseLong(userId));
        if (driver.isEmpty()) {
            throw new UsernameNotFoundException(
                    "Driver not found for userId: " + userId
            );
        }
        Driver foundDriver = driver.get();
        return new User(
                foundDriver.getUserId().toString(),
                // Drivers authenticate via JWT only — no stored password needed here
                "",
                List.of(
                        new SimpleGrantedAuthority("ROLE_DRIVER")
                )
        );
    }
}
