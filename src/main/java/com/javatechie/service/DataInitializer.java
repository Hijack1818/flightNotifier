package com.javatechie.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.javatechie.model.Flight;
import com.javatechie.model.User;
import com.javatechie.repository.FlightRepository;
import com.javatechie.repository.UserRepository;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FlightRepository flightRepository;

    @Override
    public void run(String... args) throws Exception {
        User user1 = new User();
        user1.setName("John Doe");
        user1.setEmail("john.doe@example.com");
        user1.setPhoneNumber("+1234567890");
        user1.setTravelDate(LocalDateTime.now().plusHours(10));
        userRepository.save(user1);
        
        User user2 = new User();
        user2.setName("Jane Smith");
        user2.setEmail("jane.smith@example.com");
        user2.setPhoneNumber("+0987654321");
        user2.setTravelDate(LocalDateTime.now().plusHours(20));
        userRepository.save(user2);
        
        Flight flight1 = new Flight();
        flight1.setFlightNumber("FL123");
        flight1.setScheduledTime(LocalDateTime.now().plusHours(12));
        flight1.setEstimatedTime(LocalDateTime.now().plusHours(14));
        flightRepository.save(flight1);
        
        Flight flight2 = new Flight();
        flight2.setFlightNumber("FL456");
        flight2.setScheduledTime(LocalDateTime.now().plusHours(18));
        flight2.setEstimatedTime(LocalDateTime.now().plusHours(20));
        flightRepository.save(flight2);
    }
}