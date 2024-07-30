package com.javatechie.controller;

import com.javatechie.model.Flight;
import com.javatechie.model.User;
import com.javatechie.service.FlightService;
import com.javatechie.service.UserService;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
public class FlightController {

    @Autowired
    private FlightService flightService;

    @Autowired
    private UserService userService;


    private static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);

    public static boolean isValidEmail(String email) {
        Matcher matcher = EMAIL_PATTERN.matcher(email);
        return matcher.matches();
    }


    @PostMapping("/subscribe")
    public ResponseEntity<String> saveSubscribeUser(@RequestBody Flight data) {

        Flight flight = flightService.findAllFlightByNumber(data);
        User user = data.getUserList().stream().findFirst().orElse(null);
        if (user == null || !isValidEmail(user.getEmail())) {
            return ResponseEntity.status(HttpStatus.SC_PARTIAL_CONTENT).body("Email is not valid");
        }

        if (flight == null) {
            flightService.saveFlightData(data);
        } else {
            userService.saveUser(user);
            Set<User> existingList = flight.getUserList();
            existingList.add(user);
            flight.setUserList(existingList);
            flightService.saveFlightData(flight);
        }

        flightService.firstTimeEmailConfirmation(data);

        return ResponseEntity.status(HttpStatus.SC_OK).body("Thanks for subscribing");

    }
}