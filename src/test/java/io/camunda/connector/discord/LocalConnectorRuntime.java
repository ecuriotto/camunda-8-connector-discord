package io.camunda.connector.discord;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Minimal Spring Boot application to run the connector locally for testing
 * purposes.
 */
@SpringBootApplication
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "io\\.camunda\\.connector\\.discord\\.integration\\..*"))
public class LocalConnectorRuntime {

    public static void main(String[] args) {
        SpringApplication.run(LocalConnectorRuntime.class, args);
    }
}
