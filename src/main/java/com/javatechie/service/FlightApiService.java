package com.javatechie.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class FlightApiService {

    @Value("${flight.APIKEY}")
    private String APIKEY;
    @Value("${flight.APIURL}")
    private String APIURL;


    public String getFlightInfo(String flightNumber){
        String apiUrl=String.format(APIURL,APIKEY,flightNumber);
        return new RestTemplate().getForObject(apiUrl, String.class);
    }

}
