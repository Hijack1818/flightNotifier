package com.javatechie.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.javatechie.model.Flight;
import com.javatechie.model.User;
import com.javatechie.repository.FlightRepository;
import com.javatechie.repository.UserRepository;

@Service
public class FlightService {

    private static final Logger logger = LoggerFactory.getLogger(FlightService.class);

    @Autowired
    private FlightRepository flightRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NotificationService notificationService;

    @Value("${flight.APIKEY}")
    private String APIKEY;
    @Value("${flight.APIURL}")
    private String APIURL;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Scheduled(fixedRate = 600000) // Check every 10 minutes
    public void checkFlightStatusPriority() {
        try {
            List<Flight> flights = flightRepository.findAll();
            Set<Flight> filteredFlights = filterFlights(flights);
            for (Flight flight : filteredFlights) {
                try {
                    processFlight(flight);
                } catch (JsonProcessingException e) {
                    logger.error("Error processing flight data for flight number {}: {}", flight.getFlightNumber(), e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error retrieving or processing flights: {}", e.getMessage());
        }
    }

    private Set<Flight> filterFlights(List<Flight> flights) {
        return flights.stream().filter(flight ->
                        flight.getScheduledTime().isBefore(flight.getEstimatedTime())
                                && flight.getScheduledTime().isAfter(LocalDateTime.now())
                                && flight.getScheduledTime().isBefore(LocalDateTime.now().plusHours(12)))
                .collect(Collectors.toSet());
    }

    private void processFlight(Flight flight) throws JsonProcessingException {
        String url = String.format(APIURL, APIKEY, flight.getFlightNumber().trim());
        String response;
        try {
            response = new RestTemplate().getForObject(url, String.class);
        } catch (RestClientException e) {
            logger.error("Error making API request for flight number {}: {}", flight.getFlightNumber(), e.getMessage());
            return; // Skip processing if API call fails
        }

        logger.info("Response from API for flight number {}: {}", flight.getFlightNumber(), response);

        JsonNode jsonObject;
        try {
            jsonObject = new ObjectMapper().readTree(response);
        } catch (JsonProcessingException e) {
            logger.error("Error parsing JSON response for flight number {}: {}", flight.getFlightNumber(), e.getMessage());
            return; // Skip processing if JSON parsing fails
        }

        JsonNode dataNode = jsonObject.get("data");

        if (isNodeArray(dataNode)) {
            for (JsonNode item : dataNode) {
                try {
                    processFlightData(flight, item);
                } catch (Exception e) {
                    logger.error("Error processing flight data item for flight number {}: {}", flight.getFlightNumber(), e.getMessage());
                }
            }
        }
    }

    private void processFlightData(Flight flight, JsonNode item) {
        try {
            if (item.get("flight_status").asText().equalsIgnoreCase("cancelled")) {
                notifyAboutTheCancellation(flight);
                return;
            }
            Flight currentData = extractFlightData(item, flight);
            if (hasSignificantChanges(flight, currentData)) {
                updateFlightData(flight, currentData);
                notifyUsers(flight, currentData);
            }
        } catch (Exception e) {
            logger.error("Error processing flight data: {}", e.getMessage());
        }
    }

    private void notifyAboutTheCancellation(Flight flight) {
        List<User> users = flight.getUserList().stream().toList();
        for (User user : users) {
            try {
                String message = String.format("We regret to inform you that your flight has been canceled.\n\n" +
                                "Flight Number: %s\n" +
                                "Scheduled Time: %s\n" +
                                "We apologize for any inconvenience this may cause. Please contact our support team for assistance with rebooking or further information.",
                        flight.getFlightNumber(),
                        flight.getScheduledTime());

                notificationService.sendEmail(user.getEmail(), "Flight Cancellation Notification for Flight " + flight.getFlightNumber(), message);
            } catch (Exception e) {
                logger.error("Error sending cancellation notification to user {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }

    private Flight extractFlightData(JsonNode item, Flight flight) {
        Flight currentData = new Flight();

        JsonNode flightNode = item.get("flight");
        if (flightNode != null && flightNode.has("iata")) {
            currentData.setFlightNumber(flightNode.get("iata").asText());
        }

        JsonNode departureNode = item.get("departure");
        if (departureNode != null) {
            setFlightTimes(currentData, departureNode);
            setOptionalFields(currentData, departureNode, flight);
        }

        return currentData;
    }

    private void setFlightTimes(Flight currentData, JsonNode departureNode) {
        try {
            if (departureNode.has("scheduled")) {
                String scheduledTimeStr = departureNode.get("scheduled").asText();
                currentData.setScheduledTime(parseDateTime(scheduledTimeStr));
            }

            if (departureNode.has("estimated")) {
                String estimatedTimeStr = departureNode.get("estimated").asText();
                currentData.setEstimatedTime(parseDateTime(estimatedTimeStr));
            }
        } catch (Exception e) {
            logger.error("Error setting flight times from JSON: {}", e.getMessage());
        }
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        try {
            return LocalDateTime.parse(dateTimeStr.split("\\+")[0], DATE_FORMATTER);
        } catch (Exception e) {
            logger.error("Error parsing date time string '{}': {}", dateTimeStr, e.getMessage());
            return LocalDateTime.now(); // Fallback to current time
        }
    }

    private void setOptionalFields(Flight currentData, JsonNode departureNode, Flight flight) {
        try {
            if (departureNode.has("gate") && !departureNode.get("gate").asText().equalsIgnoreCase("null")) {
                currentData.setGate(departureNode.get("gate").asText());
            } else {
                currentData.setGate(flight.getGate());
            }

            if (departureNode.has("terminal") && !departureNode.get("terminal").asText().equalsIgnoreCase("null")) {
                currentData.setTerminal(departureNode.get("terminal").asText());
            } else {
                currentData.setTerminal(flight.getTerminal());
            }

            if (departureNode.has("delay") && !departureNode.get("delay").asText().equalsIgnoreCase("null")) {
                currentData.setDelay(Long.parseLong(departureNode.get("delay").asText()));
            } else {
                currentData.setDelay(0L);
            }
        } catch (Exception e) {
            logger.error("Error setting optional fields from JSON: {}", e.getMessage());
        }
    }

    private boolean hasSignificantChanges(Flight flight, Flight currentData) {
        try {
            if (!currentData.getScheduledTime().isEqual(flight.getScheduledTime())) {
                return false;
            }
            return Math.abs(flight.getDelay() - currentData.getDelay()) > 10
                    || !currentData.getGate().equalsIgnoreCase(flight.getGate())
                    || !currentData.getTerminal().equalsIgnoreCase(flight.getTerminal());
        } catch (Exception e) {
            logger.error("Error checking for significant changes: {}", e.getMessage());
            return false;
        }
    }

    private void updateFlightData(Flight flight, Flight currentData) {
        try {
            flight.setGate(currentData.getGate());
            flight.setDelay(currentData.getDelay());
            flight.setTerminal(currentData.getTerminal());
            flight.setEstimatedTime(currentData.getEstimatedTime());
            flight.setScheduledTime(currentData.getScheduledTime());
            flightRepository.save(flight);
        } catch (Exception e) {
            logger.error("Error updating flight data in database: {}", e.getMessage());
        }
    }

    private void notifyUsers(Flight flight, Flight currentData) {
        List<User> allUsersWithFlight = flight.getUserList().stream().toList();
        for (User user : allUsersWithFlight) {
            try {
                String message = buildNotificationMessage(flight, currentData);
                notificationService.sendEmail(user.getEmail(), "Notification: Flight Details for " + currentData.getFlightNumber(), message);
                // notificationService.sendSMS(user.getPhoneNumber(), message);
            } catch (Exception e) {
                logger.error("Error sending notification to user {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }

    private String buildNotificationMessage(Flight flight, Flight currentData) {
        return String.format("We regret to inform you that your flight boarding details have changed.\n\n" +
                        "Flight Number: %s\n" +
                        "Scheduled Time: %s\n" +
                        "Estimated Time: %s\n" +
                        "Gate No: %s\n" +
                        "Terminal: %s\n\n" +
                        "We apologize for any inconvenience this may cause. Please contact our support team for further information.",
                flight.getFlightNumber(),
                currentData.getScheduledTime(),
                currentData.getEstimatedTime(),
                currentData.getGate(),
                currentData.getTerminal());
    }

    public Flight findAllFlightByNumber(Flight flight) {
        try {
            return flightRepository.findByFlightNumber(flight.getFlightNumber());
        } catch (Exception e) {
            logger.error("Error finding flight by number {}: {}", flight.getFlightNumber(), e.getMessage());
            return null;
        }
    }

    public void firstTimeEmailConfirmation(Flight data) {
        User user = data.getUserList().stream().findFirst().orElse(null);
        if (user != null) {
            try {
                String message = String.format("Subscription confirmation for flight: %s.\nCurrent status: Scheduled: %s,\nEstimated: %s\n, Gate No: %s\n, Terminal: %s \n\nThank you for choosing us",
                        data.getFlightNumber(),
                        data.getScheduledTime(),
                        data.getEstimatedTime(),
                        data.getGate(),
                        data.getTerminal());
                notificationService.sendEmail(user.getEmail(), "Subscription Confirmation for Flight " + data.getFlightNumber(), message);
            } catch (Exception e) {
                logger.error("Error sending subscription confirmation email to user {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }

    public Flight saveFlightData(Flight data) {
        try {
            return flightRepository.save(data);
        } catch (Exception e) {
            logger.error("Error saving flight data to database: {}", e.getMessage());
            return null;
        }
    }

    private boolean isNodeArray(JsonNode dataNode) {
        return dataNode != null && dataNode.isArray();
    }
}
