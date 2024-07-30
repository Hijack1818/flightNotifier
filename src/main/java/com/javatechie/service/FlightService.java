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

    @Scheduled(fixedRate = 600000) // Check every 10 minutes
    public void checkFlightStatusPriority() throws JsonProcessingException {
        List<Flight> flights = flightRepository.findAll();
        Set<Flight> filteredFlights = filterFlights(flights);
        for (Flight flight : filteredFlights) {
            processFlight(flight);
        }
    }

    /*
    method used to filter data whose scheduled time is less than estimated time
    and schedule time is after current time
    and schedule time is before 12 hours as priority notification will send
    */
    private Set<Flight> filterFlights(List<Flight> flights) {
        return flights.stream().filter(flight ->
                        flight.getScheduledTime().isBefore(flight.getEstimatedTime())
                                && flight.getScheduledTime().isAfter(LocalDateTime.now())
                                && flight.getScheduledTime().isBefore(LocalDateTime.now().plusHours(12)))
                .collect(Collectors.toSet());
    }

    private void processFlight(Flight flight) throws JsonProcessingException {
        String url = String.format(APIURL, APIKEY, flight.getFlightNumber().trim());
//        String response = new RestTemplate().getForObject(url, String.class);
        String response = """
            {
                "pagination": {
                    "limit": 100,
                    "offset": 0,
                    "count": 1,
                    "total": 1
                },
                "data": [
                    {
                        "flight_date": "2024-07-31",
                        "flight_status": "scheduled",
                        "departure": {
                            "airport": "Indira Gandhi International",
                            "timezone": "Asia/Kolkata",
                            "iata": "DEL",
                            "icao": "VIDP",
                            "terminal": "2",
                            "gate": "T28",
                            "delay": null,
                            "scheduled": "2024-07-31T08:00:00+00:00",
                            "estimated": "2024-07-31T08:50:00+00:00",
                            "actual": null,
                            "estimated_runway": null,
                            "actual_runway": null
                        },
                        "arrival": {
                            "airport": "Raja Sansi International Airport",
                            "timezone": "Asia/Kolkata",
                            "iata": "ATQ",
                            "icao": "VIAR",
                            "terminal": "1",
                            "gate": null,
                            "baggage": null,
                            "delay": null,
                            "scheduled": "2024-07-28T13:30:00+00:00",
                            "estimated": "2024-07-28T13:30:00+00:00",
                            "actual": null,
                            "estimated_runway": null,
                            "actual_runway": null
                        },
                        "airline": {
                            "name": "IndiGo",
                            "iata": "6E",
                            "icao": "IGO"
                        },
                        "flight": {
                            "number": "2016",
                            "iata": "6E2016",
                            "icao": "IGO2016",
                            "codeshared": null
                        },
                        "aircraft": null,
                        "live": null
                    }
                ]
            }
            """;

        logger.info("Response from API : " + response);

        JsonNode jsonObject = new ObjectMapper().readTree(response);
        JsonNode dataNode = jsonObject.get("data");

        if (isNodeArray(dataNode)) {
            for (JsonNode item : dataNode) {
                //getting further data from json
                processFlightData(flight, item);
            }
        }
    }

    private void processFlightData(Flight flight, JsonNode item) {
        //check if flight is canceled
        if(item.get("flight_status").asText().equalsIgnoreCase("cancelled")){
            notifyAboutTheCancellation(flight);
            return;
        }
        //extracting data from json
        Flight currentData = extractFlightData(item, flight);
        //Significant change mean more than 10 min late, gate change, terminal change
        if (hasSignificantChanges(flight, currentData)) {
            //save current updated data to data base
            updateFlightData(flight, currentData);
            //notify user if there is some change in flight
            notifyUsers(flight, currentData);
        }
    }

    private void notifyAboutTheCancellation(Flight flight) {
        List<User> users = flight.getUserList().stream().toList();
        for(User user: users) {
            String message = String.format("We regret to inform you that your flight has been canceled.\n\n" +
                            "Flight Number: %s\n" +
                            "Scheduled Time: %s\n" +
                            "We apologize for any inconvenience this may cause. Please contact our support team for assistance with rebooking or further information.",
                    flight.getFlightNumber(),
                    flight.getScheduledTime());

            notificationService.sendEmail(user.getEmail(), "Flight Cancellation Notification for Flight " + flight.getFlightNumber(), message);
        }
    }

    private Flight extractFlightData(JsonNode item, Flight flight) {
        Flight currentData = new Flight();

        JsonNode flightNode = item.get("flight");
        //getting flight number
        if (flightNode != null && flightNode.has("iata")) {
            currentData.setFlightNumber(flightNode.get("iata").asText());
        }

        JsonNode departureNode = item.get("departure");
        if (departureNode != null) {
            //getting flight schedule and estimate time and date
            setFlightTimes(currentData, departureNode);
            //getting delay, gate number, terminal
            setOptionalFields(currentData, departureNode, flight);
        }

        return currentData;
    }

    private void setFlightTimes(Flight currentData, JsonNode departureNode) {
        if (departureNode.has("scheduled")) {
            String scheduledTimeStr = departureNode.get("scheduled").asText();
            currentData.setScheduledTime(parseDateTime(scheduledTimeStr));
        }

        if (departureNode.has("estimated")) {
            String estimatedTimeStr = departureNode.get("estimated").asText();
            currentData.setEstimatedTime(parseDateTime(estimatedTimeStr));
        }
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        return LocalDateTime.parse(dateTimeStr.split("\\+")[0], DATE_FORMATTER);
    }

    //getting gate number, terminal, delay from updated json
    private void setOptionalFields(Flight currentData, JsonNode departureNode, Flight flight) {
        if (departureNode.has("gate") && !departureNode.get("gate").asText().equalsIgnoreCase("null")) {
            currentData.setGate(departureNode.get("gate").asText());
        }else{
            currentData.setGate(flight.getGate());
        }

        if (departureNode.has("terminal") && !departureNode.get("terminal").asText().equalsIgnoreCase("null")) {
            currentData.setTerminal(departureNode.get("terminal").asText());
        }else{
            currentData.setTerminal(flight.getTerminal());
        }

        if (departureNode.has("delay") && !departureNode.get("delay").asText().equalsIgnoreCase("null")) {
            currentData.setDelay(Long.parseLong(departureNode.get("delay").asText()));
        }else{
            currentData.setDelay(0L);
        }
    }

    private boolean hasSignificantChanges(Flight flight, Flight currentData) {
        //as it might get other flight that is scheduled for other day but having same number
        if(!currentData.getScheduledTime().isEqual(flight.getScheduledTime())){
            return false;
        }
        //if flight gate changes
        //if delay in flight exceeds 10 min both pospone and prepone by 10 min
        //if terminal of flight gets changes
        return Math.abs(flight.getDelay() - currentData.getDelay()) > 10
                || !currentData.getGate().equalsIgnoreCase(flight.getGate())
                || !currentData.getTerminal().equalsIgnoreCase(flight.getTerminal());
    }

    private void updateFlightData(Flight flight, Flight currentData) {
        flight.setGate(currentData.getGate());
        flight.setDelay(currentData.getDelay());
        flight.setTerminal(currentData.getTerminal());
        flight.setEstimatedTime(currentData.getEstimatedTime());
        flight.setScheduledTime(currentData.getScheduledTime());
        //saving update values of flight into database
        flightRepository.save(flight);
    }

    private void notifyUsers(Flight flight, Flight currentData) {
        List<User> allUsersWithFlight = flight.getUserList().stream().toList();
        //get list of all user and send email to each and every one if changes are significant
        for (User user : allUsersWithFlight) {
            //building message accordingly
            String message = buildNotificationMessage(flight, currentData);
            notificationService.sendEmail(user.getEmail(), "Notification: Flight Details for " + currentData.getFlightNumber(), message);
            //notificationService.sendSMS(user.getPhoneNumber(), message);
        }
    }

    private String buildNotificationMessage(Flight flight, Flight currentData) {
        return String.format("We regret to inform you that your flight bording details has been changed.\n\n" +
                        "Flight Number: %s\n" +
                        "Scheduled Time: %s\n" +
                        "Estimated Time: %s\n" +
                        "Gate No: %s\n" +
                        "Terminal: %s\n\n" +
                        "We apologize for any inconvenience this may cause. Please contact our support team for assistance further information.",
                flight.getFlightNumber(),
                currentData.getScheduledTime(),
                currentData.getEstimatedTime(),
                currentData.getGate(),
                currentData.getTerminal());
    }

    public Flight findAllFlightByNumber(Flight flight) {
        return flightRepository.findByFlightNumber(flight.getFlightNumber());
    }

    //send email to user who just subscribed
    public void firstTimeEmailConfirmation(Flight data) {
        User user = data.getUserList().stream().toList().get(0);
        String message = String.format("Subscription confirmation for flight: %s.\n Current status: Scheduled: %s,\n Estimated: %s\n, Gate No: %s\n, Terminal: %s \n\n Thank you for choosing us",
                data.getFlightNumber(),
                data.getScheduledTime(),
                data.getEstimatedTime(),
                data.getGate(),
                data.getTerminal());
        notificationService.sendEmail(user.getEmail(), "Subscription Confirmation for Flight " + data.getFlightNumber(), message);
    }

    public Flight saveFlightData(Flight data) {
        return flightRepository.save(data);
    }

    private boolean isNodeArray(JsonNode dataNode) {
        return dataNode != null && dataNode.isArray();
    }
}