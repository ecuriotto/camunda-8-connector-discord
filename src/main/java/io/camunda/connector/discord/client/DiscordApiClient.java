package io.camunda.connector.discord.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorRetryExceptionBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP client wrapper for the Discord REST API v10.
 *
 * <p>
 * Provides methods for bot-token-authenticated requests and webhook requests.
 * Handles Discord-specific error responses including rate limiting (429).
 */
public class DiscordApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiscordApiClient.class);

    private static final String BASE_URL = "https://discord.com/api/v10";
    private static final String USER_AGENT = "DiscordBot (https://github.com/camunda/camunda-8-connector-discord, 1.0)";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates a client with default {@link HttpClient} and {@link ObjectMapper}.
     */
    public DiscordApiClient() {
        this(HttpClient.newHttpClient(), new ObjectMapper());
    }

    /** Creates a client with the given dependencies (useful for testing). */
    public DiscordApiClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends a bot-token-authenticated request to the Discord API.
     *
     * @param method   HTTP method (GET, POST, PUT, DELETE)
     * @param endpoint API endpoint path (e.g. {@code /channels/123/messages})
     * @param body     JSON request body, or {@code null} for body-less requests
     * @param botToken the bot token for authorization
     * @return the parsed JSON response as a {@link JsonNode}
     * @throws ConnectorException on non-retryable errors (4xx except 429, 5xx)
     */
    public JsonNode sendBotRequest(String method, String endpoint, String body, String botToken) {
        LOGGER.debug("Sending {} request to {}", method, endpoint);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Authorization", "Bot " + botToken)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json");

        builder = applyMethod(builder, method, body);

        return executeAndHandle(builder.build());
    }

    /**
     * Sends a request to a Discord webhook URL.
     *
     * <p>
     * Webhook URLs contain the authentication token in the URL itself,
     * so no {@code Authorization} header is sent.
     *
     * @param webhookUrl the full webhook URL
     * @param body       JSON request body
     * @return the parsed JSON response as a {@link JsonNode}, or an empty node for
     *         204 responses
     * @throws ConnectorException on non-retryable errors
     */
    public JsonNode sendWebhookRequest(String webhookUrl, String body) {
        LOGGER.debug("Sending webhook request");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return executeAndHandle(request);
    }

    /**
     * Returns the {@link ObjectMapper} used by this client for JSON processing.
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    // ---- Private helpers ----

    private HttpRequest.Builder applyMethod(HttpRequest.Builder builder, String method, String body) {
        return switch (method.toUpperCase()) {
            case "GET" -> builder.GET();
            case "POST" -> builder.POST(bodyPublisher(body));
            case "PUT" -> builder.PUT(bodyPublisher(body));
            case "DELETE" -> builder.DELETE();
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };
    }

    private HttpRequest.BodyPublisher bodyPublisher(String body) {
        return body != null
                ? HttpRequest.BodyPublishers.ofString(body)
                : HttpRequest.BodyPublishers.noBody();
    }

    private JsonNode executeAndHandle(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ConnectorException("DISCORD_API_ERROR", "Failed to connect to Discord API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectorException("DISCORD_API_ERROR", "Request to Discord API was interrupted", e);
        }

        return handleResponse(response);
    }

    /**
     * Processes the HTTP response, mapping status codes to appropriate exceptions.
     *
     * <ul>
     * <li>2xx → return parsed JSON body</li>
     * <li>401/403 → {@code AUTHENTICATION_FAILED}</li>
     * <li>404 → {@code NOT_FOUND} (callers map to specific codes like
     * CHANNEL_NOT_FOUND)</li>
     * <li>429 → retryable exception with {@code retry_after} from Discord
     * response</li>
     * <li>Other 4xx/5xx → {@code DISCORD_API_ERROR}</li>
     * </ul>
     */
    JsonNode handleResponse(HttpResponse<String> response) {
        int status = response.statusCode();
        String body = response.body();

        LOGGER.debug("Discord API response: status={}", status);

        // Success
        if (status >= 200 && status < 300) {
            if (body == null || body.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return parseJson(body);
        }

        // Extract Discord error message for better diagnostics
        String discordMessage = extractDiscordErrorMessage(body);

        // Authentication failure
        if (status == 401 || status == 403) {
            throw new ConnectorException(
                    "AUTHENTICATION_FAILED",
                    "Discord authentication failed (HTTP " + status + "): " + discordMessage);
        }

        // Not found
        if (status == 404) {
            throw new ConnectorException(
                    "NOT_FOUND",
                    "Discord resource not found (HTTP 404): " + discordMessage);
        }

        // Rate limited — retryable
        if (status == 429) {
            Duration retryAfter = extractRetryAfter(body);
            LOGGER.warn("Discord rate limited. Retry after: {}", retryAfter);
            throw new ConnectorRetryExceptionBuilder()
                    .errorCode("RATE_LIMITED")
                    .message("Discord rate limited. Retry after " + retryAfter.toMillis() + "ms: " + discordMessage)
                    .backoffDuration(retryAfter)
                    .build();
        }

        // All other errors
        throw new ConnectorException(
                "DISCORD_API_ERROR",
                "Discord API error (HTTP " + status + "): " + discordMessage);
    }

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new ConnectorException(
                    "DISCORD_API_ERROR", "Failed to parse Discord API response: " + e.getMessage(), e);
        }
    }

    private String extractDiscordErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "no details";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("message")) {
                return node.get("message").asText();
            }
            return body;
        } catch (JsonProcessingException e) {
            return body;
        }
    }

    private Duration extractRetryAfter(String body) {
        if (body == null || body.isBlank()) {
            return Duration.ofSeconds(5); // default fallback
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("retry_after")) {
                // Discord sends retry_after as seconds (can be a decimal)
                double seconds = node.get("retry_after").asDouble(5.0);
                return Duration.ofMillis((long) (seconds * 1000));
            }
        } catch (JsonProcessingException e) {
            LOGGER.warn("Failed to parse retry_after from Discord response", e);
        }
        return Duration.ofSeconds(5); // default fallback
    }
}
