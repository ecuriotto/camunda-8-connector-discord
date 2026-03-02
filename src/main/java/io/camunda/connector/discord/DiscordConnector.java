package io.camunda.connector.discord;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.annotation.Operation;
import io.camunda.connector.api.annotation.Variable;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.discord.client.DiscordApiClient;
import io.camunda.connector.discord.model.request.SendMessageRequest;
import io.camunda.connector.discord.model.response.MessageResponse;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(name = "Discord Connector", type = "io.camunda:discord:1")
@ElementTemplate(
    id = "io.camunda.connector.discord.v1",
    name = "Discord Connector",
    version = 1,
    description = "Send messages and manage channels in Discord",
    icon = "icon.svg",
    documentationRef = "https://github.com/camunda/camunda-8-connector-discord",
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "message", label = "Message"),
      @PropertyGroup(id = "channel", label = "Channel"),
      @PropertyGroup(id = "role", label = "Role Management")
    })
public class DiscordConnector implements OutboundConnectorProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(DiscordConnector.class);

  private final DiscordApiClient apiClient;

  /** Default constructor used by the Camunda runtime. */
  public DiscordConnector() {
    this(new DiscordApiClient());
  }

  /** Constructor with injectable API client (for testing). */
  public DiscordConnector(DiscordApiClient apiClient) {
    this.apiClient = apiClient;
  }

  /**
   * Sends a message to a Discord channel using a bot token.
   *
   * @param request the send-message input containing channelId, content/embeds, and botToken
   * @return a {@link MessageResponse} with the created message details
   * @throws ConnectorException on validation failure or Discord API errors
   */
  @Operation(id = "sendMessage", name = "Send message", description = "Send a message to a Discord channel using a bot token")
  public MessageResponse sendMessage(@Variable SendMessageRequest request) {
    request.validate();

    String endpoint = "/channels/" + request.channelId() + "/messages";
    String payload = buildMessagePayload(request.content(), request.embeds());

    LOGGER.info("Sending message to channel {}", request.channelId());

    try {
      JsonNode response = apiClient.sendBotRequest("POST", endpoint, payload, request.botToken());
      return new MessageResponse(
          response.path("id").asText(),
          response.path("channel_id").asText(),
          response.path("timestamp").asText());
    } catch (ConnectorException e) {
      if ("NOT_FOUND".equals(e.getErrorCode())) {
        throw new ConnectorException("CHANNEL_NOT_FOUND",
            "Discord channel not found: " + request.channelId(), e);
      }
      throw e;
    }
  }

  // ---- Payload helpers ----

  /**
   * Builds a JSON payload for Discord message endpoints.
   *
   * @param content plain text content (may be null)
   * @param embeds  embed objects (may be null)
   * @return JSON string
   */
  String buildMessagePayload(String content, Object embeds) {
    ObjectMapper mapper = apiClient.getObjectMapper();
    Map<String, Object> payload = new LinkedHashMap<>();
    if (content != null && !content.isBlank()) {
      payload.put("content", content);
    }
    if (embeds != null) {
      payload.put("embeds", embeds);
    }
    try {
      return mapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new ConnectorException("DISCORD_API_ERROR",
          "Failed to serialize message payload: " + e.getMessage(), e);
    }
  }
}
