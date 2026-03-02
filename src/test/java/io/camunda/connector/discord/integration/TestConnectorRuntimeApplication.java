package io.camunda.connector.discord.integration;

import io.camunda.client.annotation.Deployment;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Deployment(resources = "classpath*:/bpmn/**/*.bpmn")
public class TestConnectorRuntimeApplication {
}
