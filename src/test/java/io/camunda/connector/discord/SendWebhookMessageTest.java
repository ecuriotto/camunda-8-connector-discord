package io.camunda.connector.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.discord.client.DiscordApiClient;
import io.camunda.connector.discord.model.request.SendWebhookMessageRequest;
import io.camunda.connector.discord.model.response.WebhookResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SendWebhookMessageTest {

    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/123/abc-token";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private DiscordApiClient apiClient;

    private DiscordConnector connector;

    @BeforeEach
    void setUp() {
        connector = new DiscordConnector(apiClient);
    }

    private void stubObjectMapper() {
        when(apiClient.getObjectMapper()).thenReturn(MAPPER);
    }

    private ObjectNode emptyResponse() {
        return MAPPER.createObjectNode();
    }

    // ---- Success scenarios ----

    @Nested
    @DisplayName("Successful webhook message sending")
    class SuccessTests {

        @Test
        @DisplayName("Should send webhook message with content only")
        void shouldSendWithContentOnly() {
            stubObjectMapper();
            var request = new SendWebhookMessageRequest(WEBHOOK_URL, "Hello!", null, null, null);
            when(apiClient.sendWebhookRequest(eq(WEBHOOK_URL), any())).thenReturn(emptyResponse());

            WebhookResponse result = connector.sendWebhookMessage(request);

            assertThat(result.success()).isTrue();
            verify(apiClient).sendWebhookRequest(eq(WEBHOOK_URL), any());
        }

        @Test
        @DisplayName("Should send webhook message with embeds only")
        void shouldSendWithEmbedsOnly() {
            stubObjectMapper();
            var embeds = java.util.List.of(java.util.Map.of("title", "Test"));
            var request = new SendWebhookMessageRequest(WEBHOOK_URL, null, null, null, embeds);
            when(apiClient.sendWebhookRequest(eq(WEBHOOK_URL), any())).thenReturn(emptyResponse());

            WebhookResponse result = connector.sendWebhookMessage(request);

            assertThat(result.success()).isTrue();
        }

        @Test
        @DisplayName("Should send webhook message with all optional fields")
        void shouldSendWithAllOptionalFields() throws Exception {
            stubObjectMapper();
            var embeds = java.util.List.of(java.util.Map.of("title", "Embed"));
            var request = new SendWebhookMessageRequest(
                    WEBHOOK_URL, "Hello!", "CustomBot", "https://example.com/avatar.png", embeds);
            when(apiClient.sendWebhookRequest(eq(WEBHOOK_URL), any())).thenReturn(emptyResponse());

            WebhookResponse result = connector.sendWebhookMessage(request);

            assertThat(result.success()).isTrue();

            // Verify payload includes username and avatar_url
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(apiClient).sendWebhookRequest(eq(WEBHOOK_URL), payloadCaptor.capture());
            JsonNode parsed = MAPPER.readTree(payloadCaptor.getValue());
            assertThat(parsed.get("content").asText()).isEqualTo("Hello!");
            assertThat(parsed.get("username").asText()).isEqualTo("CustomBot");
            assertThat(parsed.get("avatar_url").asText()).isEqualTo("https://example.com/avatar.png");
            assertThat(parsed.has("embeds")).isTrue();
        }

        @Test
        @DisplayName("Should exclude blank optional fields from payload")
        void shouldExcludeBlankOptionalFields() throws Exception {
            stubObjectMapper();
            var request = new SendWebhookMessageRequest(WEBHOOK_URL, "Hello!", "  ", "", null);
            when(apiClient.sendWebhookRequest(eq(WEBHOOK_URL), any())).thenReturn(emptyResponse());

            connector.sendWebhookMessage(request);

            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(apiClient).sendWebhookRequest(eq(WEBHOOK_URL), payloadCaptor.capture());
            JsonNode parsed = MAPPER.readTree(payloadCaptor.getValue());
            assertThat(parsed.has("content")).isTrue();
            assertThat(parsed.has("username")).isFalse();
            assertThat(parsed.has("avatar_url")).isFalse();
        }
    }

    // ---- Validation scenarios ----

    @Nested
    @DisplayName("Input validation")
    class ValidationTests {

        @Test
        @DisplayName("Should reject when both content and embeds are null")
        void shouldRejectWhenBothNull() {
            var request = new SendWebhookMessageRequest(WEBHOOK_URL, null, null, null, null);

            assertThatThrownBy(() -> connector.sendWebhookMessage(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("content")
                    .hasMessageContaining("embeds");
        }

        @Test
        @DisplayName("Should reject when content is blank and embeds is null")
        void shouldRejectWhenContentBlank() {
            var request = new SendWebhookMessageRequest(WEBHOOK_URL, "  ", null, null, null);

            assertThatThrownBy(() -> connector.sendWebhookMessage(request))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ---- Error scenarios ----

    @Nested
    @DisplayName("Error handling")
    class ErrorTests {

        @Test
        @DisplayName("Should map NOT_FOUND to INVALID_WEBHOOK_URL")
        void shouldMapNotFoundToInvalidWebhookUrl() {
            stubObjectMapper();
            var request = new SendWebhookMessageRequest(WEBHOOK_URL, "Hello", null, null, null);
            when(apiClient.sendWebhookRequest(any(), any()))
                    .thenThrow(new ConnectorException("NOT_FOUND", "Webhook not found"));

            assertThatThrownBy(() -> connector.sendWebhookMessage(request))
                    .isInstanceOf(ConnectorException.class)
                    .satisfies(ex -> {
                        ConnectorException ce = (ConnectorException) ex;
                        assertThat(ce.getErrorCode()).isEqualTo("INVALID_WEBHOOK_URL");
                    });
        }

        @Test
        @DisplayName("Should propagate DISCORD_API_ERROR")
        void shouldPropagateDiscordApiError() {
            stubObjectMapper();
            var request = new SendWebhookMessageRequest(WEBHOOK_URL, "Hello", null, null, null);
            when(apiClient.sendWebhookRequest(any(), any()))
                    .thenThrow(new ConnectorException("DISCORD_API_ERROR", "Server error"));

            assertThatThrownBy(() -> connector.sendWebhookMessage(request))
                    .isInstanceOf(ConnectorException.class)
                    .satisfies(ex -> {
                        ConnectorException ce = (ConnectorException) ex;
                        assertThat(ce.getErrorCode()).isEqualTo("DISCORD_API_ERROR");
                    });
        }
    }
}
