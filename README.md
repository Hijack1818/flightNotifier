# Flight Notifier Backend

The Flight Notifier Backend is a Spring Boot application designed to support the Flight Notifier web application. It provides backend services for managing flight information, handling user subscriptions, and sending notifications via email and SMS.


### Notification on email for subscritption
![Screenshot_2024-07-30-00-41-37-51_e307a3f9df9f380ebaf106e1dc980bb6](https://github.com/user-attachments/assets/ad048806-5cbc-4c20-bae5-eedfe1cc945e)



### Alert Email if there is some change in flight
![Screenshot_2024-07-30-00-41-49-26_e307a3f9df9f380ebaf106e1dc980bb6](https://github.com/user-attachments/assets/1e76db19-7a4b-4aa9-bb25-30e9283815b4)




## Table of Contents

- [Overview](#overview)
- [Technologies](#technologies)
- [Setup](#setup)
- [API Endpoints](#api-endpoints)
- [Database Schema](#database-schema)
- [Notification Service](#notification-service)
- [Error Handling](#error-handling)
- [Contributing](#contributing)
- [License](#license)

## Overview

The backend service is responsible for:
- Storing user subscription data.
- Fetching real-time flight data from external APIs.
- Sending notifications (email and SMS) to users regarding flight updates.

## Technologies

- **Spring Boot**: For creating the RESTful API.
- **JPA (Java Persistence API)**: For managing and accessing database entities.
- **SQL**: For storing user subscription data.
- **Spring Mail**: For sending email notifications.
- **Twilio**: For sending SMS notifications.

## Setup

### Prerequisites

- Java 17 or higher
- Maven or Gradle (for dependency management)
- MySQL or PostgreSQL database (or any SQL database of your choice)
- Twilio account credentials
- Email server configuration for Spring Mail

### Installation

1. **Clone the Repository**

   ```bash
   git clone https://github.com/yourusername/flight-notifier-backend.git
   cd flight-notifier-backend

# Database configuration
spring.datasource.url=jdbc:mysql://localhost:3306/flight_notifier
spring.datasource.username=your_db_username
spring.datasource.password=your_db_password

# Email configuration
spring.mail.host=smtp.example.com
spring.mail.port=587
spring.mail.username=your_email@example.com
spring.mail.password=your_email_password

# Twilio configuration
twilio.account.sid=your_twilio_account_sid
twilio.auth.token=your_twilio_auth_token
twilio.phone.number=your_twilio_phone_number

# User Subscription
** POST /api/subscriptions **
```json
{
  "flightNumber": "AA123",
  "estimatedTime": "2024-07-29T14:45:00",
  "scheduledTime": "2024-07-29T14:00:00",
  "terminal": "T1",
  "gate": "G12",
  "delay": "45 minutes",
  "timeZone": "America/New_York",
  "userList": [
    {
      "email": "user@example.com",
      "phoneNumber": "+1234567890",
      "travelDate": "2024-07-29",
      "userid": null,
      "flight": null
    }
  ]
}


