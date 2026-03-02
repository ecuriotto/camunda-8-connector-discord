package io.camunda.connector.discord;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(name = "Discord Connector", type = "io.camunda:discord:1")
@ElementTemplate(id = "io.camunda.connector.discord.v1", name = "Discord Connector", version = 1, description = "Send messages and manage channels in Discord", icon = "icon.svg", documentationRef = "https://github.com/camunda/camunda-8-connector-discord")
public class DiscordConnector implements OutboundConnectorProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiscordConnector.class);
}
