package io.camunda.connector.discord.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorRetryException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiscordApiClientTest {

    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpResponse<String> httpResponse;

    private DiscordApiClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        client = new DiscordApiClient(httpClient, objectMapper);
    }

    // ---- Successful requests ----

    @Test
    void shouldSendBotRequestSuccessfully() throws Exception {
        String responseBody = "{\"id\": \"12345\", \"content\": \"hello\"}";
        mockResponse(200, responseBody);

        JsonNode result = client.sendBotRequest("POST", "/channels/999/messages", "{\"content\":\"hello\"}",
                "bot-token");

        assertThat(result.get("id").asText()).isEqualTo("12345");
        assertThat(result.get("content").asText()).isEqualTo("hello");
    }

    @Test
    void shouldSendWebhookRequestSuccessfully() throws Exception {
        mockResponse(204, "");

        JsonNode result = client.sendWebhookRequest("https://discord.com/api/webhooks/123/abc", "{\"content\":\"hi\"}");

        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnEmptyNodeFor204Response() throws Exception {
        mockResponse(204, null);

        JsonNode result = client.sendBotRequest("PUT", "/guilds/1/members/2/roles/3", null, "bot-token");

        assertThat(result).isNotNull();
        assertThat(result.isEmpty()).isTrue();
    }

    // ---- Headers ----

    @Test
    void shouldSetUserAgentHeader() throws Exception {
        mockResponse(200, "{\"ok\": true}");

        client.sendBotRequest("GET", "/test", null, "test-token");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());

        HttpRequest sentRequest = captor.getValue();
        assertThat(sentRequest.headers().firstValue("User-Agent"))
                .isPresent()
                .hasValue("DiscordBot (https://github.com/camunda/camunda-8-connector-discord, 1.0)");
    }

    @Test
    void shouldSetContentTypeHeader() throws Exception {
        mockResponse(200, "{\"ok\": true}");

        client.sendBotRequest("POST", "/test", "{}", "test-token");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());

        HttpRequest sentRequest = captor.getValue();
        assertThat(sentRequest.headers().firstValue("Content-Type"))
                .isPresent()
                .hasValue("application/json");
    }

    @Test
    void shouldSetAuthorizationHeaderForBotRequests() throws Exception {
        mockResponse(200, "{\"ok\": true}");

        client.sendBotRequest("GET", "/test", null, "my-secret-token");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());

        HttpRequest sentRequest = captor.getValue();
        assertThat(sentRequest.headers().firstValue("Authorization"))
                .isPresent()
                .hasValue("Bot my-secret-token");
    }

    @Test
    void shouldNotSetAuthorizationHeaderForWebhookRequests() throws Exception {
        mockResponse(204, "");

        client.sendWebhookRequest("https://discord.com/api/webhooks/123/abc", "{\"content\":\"hi\"}");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());

        HttpRequest sentRequest = captor.getValue();
        assertThat(sentRequest.headers().firstValue("Authorization")).isEmpty();
    }

    // ---- Error handling: 401 ----

    @Test
    void shouldThrowAuthenticationFailedOn401() throws Exception {
        mockResponse(401, "{\"message\": \"401: Unauthorized\", \"code\": 0}");

        assertThatThrownBy(() -> client.sendBotRequest("GET", "/test", null, "bad-token"))
                .isInstanceOf(ConnectorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "AUTHENTICATION_FAILED")
                .hasMessageContaining("401: Unauthorized");
    }

    // ---- Error handling: 403 ----

    @Test
    void shouldThrowAuthenticationFailedOn403() throws Exception {
        mockResponse(403, "{\"message\": \"Missing Permissions\", \"code\": 50013}");

        assertThatThrownBy(() -> client.sendBotRequest("POST", "/test", "{}", "token"))
                .isInstanceOf(ConnectorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "AUTHENTICATION_FAILED")
                .hasMessageContaining("Missing Permissions");
    }

    // ---- Error handling: 404 ----

    @Test
    void shouldThrowNotFoundOn404() throws Exception {
        mockResponse(404, "{\"message\": \"Unknown Channel\", \"code\": 10003}");

        assertThatThrownBy(() -> client.sendBotRequest("POST", "/channels/999/messages", "{}", "token"))
                .isInstanceOf(ConnectorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "NOT_FOUND")
                .hasMessageContaining("Unknown Channel");
    }

    // ---- Error handling: 429 (rate limiting) ----

    @Test
    void shouldThrowRetryExceptionOn429() throws Exception {
        mockResponse(429, "{\"message\": \"You are being rate limited.\", \"retry_after\": 1.5, \"global\": false}");

        assertThatThrownBy(() -> client.sendBotRequest("POST", "/channels/999/messages", "{}", "token"))
                .isInstanceOf(ConnectorRetryException.class)
                .hasFieldOrPropertyWithValue("errorCode", "RATE_LIMITED")
                .hasMessageContaining("rate limited");
    }

    @Test
    void shouldExtractRetryAfterDuration() throws Exception {
        mockResponse(429, "{\"message\": \"You are being rate limited.\", \"retry_after\": 2.5, \"global\": false}");

        assertThatThrownBy(() -> client.sendBotRequest("POST", "/test", "{}", "token"))
                .isInstanceOf(ConnectorRetryException.class)
                .satisfies(ex -> {
                    ConnectorRetryException retryEx = (ConnectorRetryException) ex;
                    assertThat(retryEx.getBackoffDuration()).isEqualTo(java.time.Duration.ofMillis(2500));
                });
    }

    @Test
    void shouldUseFallbackRetryAfterWhenMissing() throws Exception {
        mockResponse(429, "{\"message\": \"Rate limited\"}");

        assertThatThrownBy(() -> client.sendBotRequest("POST", "/test", "{}", "token"))
                .isInstanceOf(ConnectorRetryException.class)
                .satisfies(ex -> {
                    ConnectorRetryException retryEx = (ConnectorRetryException) ex;
                    assertThat(retryEx.getBackoffDuration()).isEqualTo(java.time.Duration.ofSeconds(5));
                });
    }

    // ---- Error handling: 500 ----

    @Test
    void shouldThrowDiscordApiErrorOn500() throws Exception {
        mockResponse(500, "{\"message\": \"Internal Server Error\"}");

        assertThatThrownBy(() -> client.sendBotRequest("GET", "/test", null, "token"))
                .isInstanceOf(ConnectorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCORD_API_ERROR")
                .hasMessageContaining("Internal Server Error");
    }

    // ---- Error handling: connection failure ----

    @Test
    void shouldThrowConnectorExceptionOnIOException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new IOException("Connection refused"));

        assertThatThrownBy(() -> client.sendBotRequest("GET", "/test", null, "token"))
                .isInstanceOf(ConnectorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCORD_API_ERROR")
                .hasMessageContaining("Connection refused");
    }

    // ---- Error handling: bad JSON response ----

    @Test
    void shouldHandleNonJsonErrorBody() throws Exception {
        mockResponse(400, "Bad Request");

        assertThatThrownBy(() -> client.sendBotRequest("GET", "/test", null, "token"))
                .isInstanceOf(ConnectorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCORD_API_ERROR")
                .hasMessageContaining("Bad Request");
    }

    // ---- HTTP methods ----

    @Test
    void shouldSupportDeleteMethod() throws Exception {
        mockResponse(204, "");

        JsonNode result = client.sendBotRequest("DELETE", "/guilds/1/members/2/roles/3", null, "token");

        assertThat(result).isNotNull();

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertThat(captor.getValue().method()).isEqualTo("DELETE");
    }

    @Test
    void shouldSupportPutMethod() throws Exception {
        mockResponse(204, "");

        JsonNode result = client.sendBotRequest("PUT", "/guilds/1/members/2/roles/3", null, "token");

        assertThat(result).isNotNull();

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertThat(captor.getValue().method()).isEqualTo("PUT");
    }

    // ---- Helpers ----

    @SuppressWarnings("unchecked")
    private void mockResponse(int statusCode, String body) throws Exception {
        when(httpResponse.statusCode()).thenReturn(statusCode);
        when(httpResponse.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
    }
}
