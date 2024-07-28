package com.javatechie.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.javatechie.model.Flight;

public interface FlightRepository extends JpaRepository<Flight, Long> {
}