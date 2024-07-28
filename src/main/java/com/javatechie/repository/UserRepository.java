package com.javatechie.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.javatechie.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByTravelDateBetween(LocalDateTime start, LocalDateTime end);
}
