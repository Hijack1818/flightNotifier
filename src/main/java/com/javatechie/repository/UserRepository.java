package com.javatechie.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.javatechie.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
}
