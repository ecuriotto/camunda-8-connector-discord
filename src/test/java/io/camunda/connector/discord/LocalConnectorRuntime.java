package io.camunda.connector.discord;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application to run the connector locally for testing
 * purposes.
 */
@SpringBootApplication
public class LocalConnectorRuntime {

    public static void main(String[] args) {
        SpringApplication.run(LocalConnectorRuntime.class, args);
    }
}
