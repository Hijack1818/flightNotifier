package com.javatechie.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.javatechie.model.Flight;
import com.javatechie.model.User;
import com.javatechie.repository.FlightRepository;
import com.javatechie.repository.UserRepository;

@Service
public class FlightService {

    @Autowired
    private FlightRepository flightRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NotificationService notificationService;

    @Scheduled(fixedRate = 60000) // Check every minute
    public void checkFlightStatus() {
        List<Flight> flights = flightRepository.findAll();
        for (Flight flight : flights) {
            if (flight.getScheduledTime().isBefore(flight.getEstimatedTime())) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime start = now.minusHours(24);
                LocalDateTime end = now.plusHours(24);
                List<User> users = userRepository.findByTravelDateBetween(start, end);
                for (User user : users) {
                    String message = "Flight " + flight.getFlightNumber() + " is delayed. Scheduled: " +
                            flight.getScheduledTime() + ", Estimated: " + flight.getEstimatedTime();
                    notificationService.sendEmail(user.getEmail(), "Flight Delay Notification", message);
                    notificationService.sendSMS(user.getPhoneNumber(), message);
                }
            }
        }
    }
}