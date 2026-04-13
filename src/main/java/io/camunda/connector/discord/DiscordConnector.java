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
import io.camunda.connector.discord.model.request.CreateChannelRequest;
import io.camunda.connector.discord.model.request.ManageRolesRequest;
import io.camunda.connector.discord.model.request.SendMessageRequest;
import io.camunda.connector.discord.model.request.SendWebhookMessageRequest;
import io.camunda.connector.discord.model.response.ChannelResponse;
import io.camunda.connector.discord.model.response.MessageResponse;
import io.camunda.connector.discord.model.response.RoleResponse;
import io.camunda.connector.discord.model.response.WebhookResponse;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(name = "Discord Outbound Connector", type = "io.camunda:discord:1")
@ElementTemplate(id = "io.camunda.connector.discord.v1", name = "Discord Outbound Connector", version = 2, description = "Send messages and manage channels in Discord", icon = "icon.svg", documentationRef = "https://github.com/camunda/camunda-8-connector-discord", propertyGroups = {
        @PropertyGroup(id = "operation", label = "Operation"),
        @PropertyGroup(id = "authentication", label = "Authentication"),
        @PropertyGroup(id = "message", label = "Message"),
        @PropertyGroup(id = "channel", label = "Channel"),
        @PropertyGroup(id = "role", label = "Role management")
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
     * @param request the send-message input containing channelId, content/embeds,
     *                and botToken
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

    /**
     * Sends a message to a Discord channel via a webhook URL.
     *
     * @param request the webhook input containing webhookUrl, content/embeds, and
     *                optional overrides
     * @return a {@link WebhookResponse} indicating success
     * @throws ConnectorException on validation failure or Discord API errors
     */
    @Operation(id = "sendWebhookMessage", name = "Send webhook message", description = "Send a message via a Discord webhook URL")
    public WebhookResponse sendWebhookMessage(@Variable SendWebhookMessageRequest request) {
        request.validate();

        String payload = buildWebhookPayload(request);

        LOGGER.info("Sending webhook message");

        try {
            apiClient.sendWebhookRequest(request.webhookUrl(), payload);
            return new WebhookResponse(true);
        } catch (ConnectorException e) {
            if ("NOT_FOUND".equals(e.getErrorCode())) {
                throw new ConnectorException("INVALID_WEBHOOK_URL",
                        "Discord webhook URL is invalid or expired", e);
            }
            throw e;
        }
    }

    /**
     * Creates a new channel in a Discord guild.
     *
     * @param request the create-channel input containing guildId, channelName,
     *                type, and botToken
     * @return a {@link ChannelResponse} with the created channel details
     * @throws ConnectorException on validation failure or Discord API errors
     */
    @Operation(id = "createChannel", name = "Create channel", description = "Create a new channel in a Discord guild")
    public ChannelResponse createChannel(@Variable CreateChannelRequest request) {
        String endpoint = "/guilds/" + request.guildId() + "/channels";
        String payload = buildChannelPayload(request);

        LOGGER.info("Creating channel '{}' in guild {}", request.channelName(), request.guildId());

        try {
            JsonNode response = apiClient.sendBotRequest("POST", endpoint, payload, request.botToken());
            return new ChannelResponse(
                    response.path("id").asText(),
                    response.path("name").asText(),
                    response.path("type").asInt());
        } catch (ConnectorException e) {
            if ("NOT_FOUND".equals(e.getErrorCode())) {
                throw new ConnectorException("GUILD_NOT_FOUND",
                        "Discord guild not found: " + request.guildId(), e);
            }
            if ("AUTHENTICATION_FAILED".equals(e.getErrorCode())
                    && e.getMessage() != null && e.getMessage().contains("403")) {
                throw new ConnectorException("MISSING_PERMISSIONS",
                        "Bot lacks permissions to create channels in guild: " + request.guildId(), e);
            }
            throw e;
        }
    }

    /**
     * Adds or removes a role from a guild member.
     *
     * @param request the manage-roles input containing guildId, userId, roleId,
     *                action, and botToken
     * @return a {@link RoleResponse} indicating the result
     * @throws ConnectorException on validation failure or Discord API errors
     */
    @Operation(id = "manageRoles", name = "Manage roles", description = "Add or remove a role from a Discord guild member")
    public RoleResponse manageRoles(@Variable ManageRolesRequest request) {
        String action = request.action() != null ? request.action() : "Add";
        String method = "Add".equalsIgnoreCase(action) ? "PUT" : "DELETE";
        String endpoint = "/guilds/" + request.guildId()
                + "/members/" + request.userId()
                + "/roles/" + request.roleId();

        LOGGER.info("{} role {} for user {} in guild {}", action, request.roleId(),
                request.userId(), request.guildId());

        try {
            apiClient.sendBotRequest(method, endpoint, null, request.botToken());
            return new RoleResponse(true, action, request.userId(), request.roleId());
        } catch (ConnectorException e) {
            if ("NOT_FOUND".equals(e.getErrorCode())) {
                throw new ConnectorException("MEMBER_OR_ROLE_NOT_FOUND",
                        "Member or role not found in guild " + request.guildId(), e);
            }
            if ("AUTHENTICATION_FAILED".equals(e.getErrorCode())
                    && e.getMessage() != null && e.getMessage().contains("403")) {
                throw new ConnectorException("MISSING_PERMISSIONS",
                        "Bot lacks permissions to manage roles in guild: " + request.guildId(), e);
            }
            throw e;
        }
    }

    // ---- Payload builders ----

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

    /**
     * Builds a JSON payload for Discord webhook endpoints.
     *
     * <p>
     * Includes optional {@code username} and {@code avatar_url} overrides.
     *
     * @param request the webhook message request
     * @return JSON string
     */
    String buildWebhookPayload(SendWebhookMessageRequest request) {
        ObjectMapper mapper = apiClient.getObjectMapper();
        Map<String, Object> payload = new LinkedHashMap<>();
        if (request.content() != null && !request.content().isBlank()) {
            payload.put("content", request.content());
        }
        if (request.embeds() != null) {
            payload.put("embeds", request.embeds());
        }
        if (request.username() != null && !request.username().isBlank()) {
            payload.put("username", request.username());
        }
        if (request.avatarUrl() != null && !request.avatarUrl().isBlank()) {
            payload.put("avatar_url", request.avatarUrl());
        }
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new ConnectorException("DISCORD_API_ERROR",
                    "Failed to serialize webhook payload: " + e.getMessage(), e);
        }
    }

    /**
     * Builds a JSON payload for Discord channel creation.
     *
     * @param request the create-channel request
     * @return JSON string with {@code name}, {@code type}, and optional
     *         {@code topic}
     */
    String buildChannelPayload(CreateChannelRequest request) {
        ObjectMapper mapper = apiClient.getObjectMapper();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", request.channelName());
        payload.put("type", request.channelTypeAsInt());
        if (request.topic() != null && !request.topic().isBlank()) {
            payload.put("topic", request.topic());
        }
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new ConnectorException("DISCORD_API_ERROR",
                    "Failed to serialize channel payload: " + e.getMessage(), e);
        }
    }
}
