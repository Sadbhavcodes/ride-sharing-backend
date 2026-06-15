package com.rideshare.userservice.repository;

import com.rideshare.userservice.entity.Role;
import com.rideshare.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository
        extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    List<User> findByRole(Role role);
    boolean existsByEmail(String email);
}