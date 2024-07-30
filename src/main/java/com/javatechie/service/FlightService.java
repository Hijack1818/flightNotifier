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

import com.javatechie.model.Flight;
import com.javatechie.model.User;
import com.javatechie.repository.FlightRepository;
import com.javatechie.repository.UserRepository;
import org.springframework.web.client.RestTemplate;

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


    @Scheduled(fixedRate = 600000) // Check every minute
    public void checkFlightStatusPriority() throws JsonProcessingException {

        //fetching all flight from database
        List<Flight> flights = flightRepository.findAll();

        Set<Flight> filteredFlight = fliterFlight(flights);

        for (Flight flight : filteredFlight) {
            //request list of all flights
            String url = String.format(APIURL,APIKEY,flight.getFlightNumber().trim());

            String response = new RestTemplate().getForObject(url, String.class);
            logger.info("Response from API : "+response);
//                String response = """
//                        {
//                            "pagination": {
//                                "limit": 100,
//                                "offset": 0,
//                                "count": 1,
//                                "total": 1
//                            },
//                            "data": [
//                                {
//                                    "flight_date": "2024-07-28",
//                                    "flight_status": "scheduled",
//                                    "departure": {
//                                        "airport": "Indira Gandhi International",
//                                        "timezone": "Asia/Kolkata",
//                                        "iata": "DEL",
//                                        "icao": "VIDP",
//                                        "terminal": "2",
//                                        "gate": "T28",
//                                        "delay": null,
//                                        "scheduled": "2024-07-28T12:20:00+00:00",
//                                        "estimated": "2024-07-28T12:20:00+00:00",
//                                        "actual": null,
//                                        "estimated_runway": null,
//                                        "actual_runway": null
//                                    },
//                                    "arrival": {
//                                        "airport": "Raja Sansi International Airport",
//                                        "timezone": "Asia/Kolkata",
//                                        "iata": "ATQ",
//                                        "icao": "VIAR",
//                                        "terminal": "1",
//                                        "gate": null,
//                                        "baggage": null,
//                                        "delay": null,
//                                        "scheduled": "2024-07-28T13:30:00+00:00",
//                                        "estimated": "2024-07-28T13:30:00+00:00",
//                                        "actual": null,
//                                        "estimated_runway": null,
//                                        "actual_runway": null
//                                    },
//                                    "airline": {
//                                        "name": "IndiGo",
//                                        "iata": "6E",
//                                        "icao": "IGO"
//                                    },
//                                    "flight": {
//                                        "number": "2016",
//                                        "iata": "6E2016",
//                                        "icao": "IGO2016",
//                                        "codeshared": null
//                                    },
//                                    "aircraft": null,
//                                    "live": null
//                                }
//                            ]
//                        }
//                        """;
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonObject = objectMapper.readTree(response);

            JsonNode dataNode = jsonObject.get("data");

            if (dataNode != null && dataNode.isArray()) {
                for (JsonNode item : dataNode) {
                    Flight currentData = new Flight();
                    if (!item.get("flight_status").asText().equalsIgnoreCase("scheduled")) {
                        continue;
                    }
                    // Flight number
                    JsonNode flightNode = item.get("flight");
                    if (flightNode != null && flightNode.has("iata")) {
                        currentData.setFlightNumber(flightNode.get("iata").asText());
                    }

                    // Departure times
                    JsonNode departureNode = item.get("departure");
                    if (departureNode != null) {
                        if (departureNode.has("scheduled")) {
                            String scheduledTimeStr = departureNode.get("scheduled").asText();
                            if (scheduledTimeStr != null && !scheduledTimeStr.isEmpty()) {
                                currentData.setScheduledTime(LocalDateTime.parse(scheduledTimeStr.split("\\+")[0], DATE_FORMATTER));
                            }
                        }

                        if (departureNode.has("estimated")) {
                            String estimatedTimeStr = departureNode.get("estimated").asText();
                            if (estimatedTimeStr != null && !estimatedTimeStr.isEmpty()) {
                                currentData.setEstimatedTime(LocalDateTime.parse(estimatedTimeStr.split("\\+")[0], DATE_FORMATTER));
                            }
                        }

                        // Gate
                        if (departureNode.has("gate") && !departureNode.get("gate").asText().equalsIgnoreCase("null")) {
                            currentData.setGate(departureNode.get("gate").asText());
                        } else {
                            currentData.setGate(flight.getGate());
                        }

                        // Terminal
                        if (departureNode.has("terminal") && !departureNode.get("terminal").asText().equalsIgnoreCase("null")) {
                            currentData.setTerminal(departureNode.get("terminal").asText());
                        } else {
                            currentData.setTerminal(flight.getTerminal());
                        }

                        // Delay
                        if (departureNode.has("delay")) {
                            String delayStr = departureNode.get("delay").asText();
                            if (!delayStr.equalsIgnoreCase("null") && !delayStr.isEmpty()) {
                                currentData.setDelay(Long.parseLong(delayStr));
                            } else {
                                currentData.setDelay(flight.getDelay());
                            }
                        }
                        //if flight gate changes
                        //if delay in flight exceeds 10 min both pospone and prepone by 10 min
                        //if terminal of flight gets changes
                        filterData(flight,currentData);
                    }
                }
            }
        }
    }


    /*
    method used to filter data whose scheduled time is less than estimated time
    and schedule time is after current time
    and schedule time is before 12 hours as priority notification will send
    */
    private Set<Flight> fliterFlight(List<Flight> flights) {
        return flights.stream().filter(flight ->
                flight.getScheduledTime().isBefore(flight.getEstimatedTime())
                        && flight.getScheduledTime().isAfter(LocalDateTime.now())
                        && flight.getScheduledTime().isBefore(LocalDateTime.now().plusHours(12))).collect(Collectors.toSet());
    }

    //if flight gate changes
    //if delay in flight exceeds 10 min both pospone and prepone by 10 min
    //if terminal of flight gets changes
    private void filterData(Flight flight, Flight currentData) {
        if ((
                flight.getGate() != null && currentData.getGate() != null && !flight.getGate().equalsIgnoreCase(currentData.getGate()))
                || (flight.getDelay() != null && currentData.getDelay() != null && flight.getDelay() - currentData.getDelay() < -9)
                || (flight.getDelay() != null && currentData.getDelay() != null && flight.getDelay() - currentData.getDelay() > 9)
                || (flight.getTerminal() != null && currentData.getTerminal() != null && !flight.getTerminal().equalsIgnoreCase(currentData.getTerminal()))
        ) {

            //save the updated current flight data to database
            flight.setGate(currentData.getGate());
            flight.setDelay(currentData.getDelay());
            flight.setTerminal(currentData.getTerminal());
            flight.setEstimatedTime(currentData.getEstimatedTime());
            flight.setScheduledTime(currentData.getScheduledTime());
            flightRepository.save(flight);

            //send notification all user with flight if there is change
            notificationSender(currentData, flight);
        }
    }

    private void notificationSender(Flight currentData, Flight flight) {
        List<User> allUserWithFlight = flight.getUserList().stream().toList();
        for (User user : allUserWithFlight) {
            String message = "There are some changes in your flight timing, terminal or gate for the Flight Number: " + flight.getFlightNumber() + ". Scheduled: " +
                    currentData.getScheduledTime() + ", Estimated: " + currentData.getEstimatedTime() + ", Gate No: " + currentData.getGate() + ", Terminal: " + currentData.getTerminal();
            notificationService.sendEmail(user.getEmail(), "Notification: Flight Details for " + currentData.getFlightNumber(), message);
            //notificationService.sendSMS(user.getPhoneNumber(), message);
        }
    }


    public void firstTimeEmailConfirmation(Flight data) {
        User user = data.getUserList().stream().toList().get(0);
        notificationService.sendEmail(user.getEmail(), "Subscription conformation for flight: " + data.getFlightNumber(), "Current status your flight with Flight Number: " + data.getFlightNumber() + ". Scheduled: " +
                data.getScheduledTime() + ", Estimated: " + data.getEstimatedTime() + ", Gate No: " + data.getGate() + ", Terminal: " + data.getTerminal());
    }
}