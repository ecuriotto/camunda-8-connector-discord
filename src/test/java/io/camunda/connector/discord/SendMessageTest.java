package io.camunda.connector.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.api.error.ConnectorRetryExceptionBuilder;
import io.camunda.connector.discord.client.DiscordApiClient;
import io.camunda.connector.discord.model.request.SendMessageRequest;
import io.camunda.connector.discord.model.response.MessageResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SendMessageTest {

  private static final String CHANNEL_ID = "123456789";
  private static final String BOT_TOKEN = "test-bot-token";
  private static final String MESSAGE_ID = "987654321";
  private static final String TIMESTAMP = "2026-03-02T12:00:00.000000+00:00";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock private DiscordApiClient apiClient;

  private DiscordConnector connector;

  @BeforeEach
  void setUp() {
    connector = new DiscordConnector(apiClient);
  }

  private void stubObjectMapper() {
    when(apiClient.getObjectMapper()).thenReturn(MAPPER);
  }

  private ObjectNode successResponse() {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("id", MESSAGE_ID);
    node.put("channel_id", CHANNEL_ID);
    node.put("timestamp", TIMESTAMP);
    return node;
  }

  // ---- Success scenarios ----

  @Nested
  @DisplayName("Successful message sending")
  class SuccessTests {

    @Test
    @DisplayName("Should send message with content only")
    void shouldSendMessageWithContentOnly() {
      stubObjectMapper();
      var request = new SendMessageRequest(CHANNEL_ID, "Hello Discord!", null, BOT_TOKEN);
      when(apiClient.sendBotRequest(eq("POST"), eq("/channels/" + CHANNEL_ID + "/messages"), any(), eq(BOT_TOKEN)))
          .thenReturn(successResponse());

      MessageResponse result = connector.sendMessage(request);

      assertThat(result.messageId()).isEqualTo(MESSAGE_ID);
      assertThat(result.channelId()).isEqualTo(CHANNEL_ID);
      assertThat(result.timestamp()).isEqualTo(TIMESTAMP);
    }

    @Test
    @DisplayName("Should send message with embeds only")
    void shouldSendMessageWithEmbedsOnly() {
      stubObjectMapper();
      var embeds = List.of(Map.of("title", "Test Embed", "description", "A test embed"));
      var request = new SendMessageRequest(CHANNEL_ID, null, embeds, BOT_TOKEN);
      when(apiClient.sendBotRequest(eq("POST"), eq("/channels/" + CHANNEL_ID + "/messages"), any(), eq(BOT_TOKEN)))
          .thenReturn(successResponse());

      MessageResponse result = connector.sendMessage(request);

      assertThat(result.messageId()).isEqualTo(MESSAGE_ID);
      assertThat(result.channelId()).isEqualTo(CHANNEL_ID);
    }

    @Test
    @DisplayName("Should send message with both content and embeds")
    void shouldSendMessageWithContentAndEmbeds() {
      stubObjectMapper();
      var embeds = List.of(Map.of("title", "Embed"));
      var request = new SendMessageRequest(CHANNEL_ID, "Hello!", embeds, BOT_TOKEN);
      when(apiClient.sendBotRequest(eq("POST"), eq("/channels/" + CHANNEL_ID + "/messages"), any(), eq(BOT_TOKEN)))
          .thenReturn(successResponse());

      MessageResponse result = connector.sendMessage(request);

      assertThat(result.messageId()).isEqualTo(MESSAGE_ID);
    }
  }

  // ---- Validation scenarios ----

  @Nested
  @DisplayName("Input validation")
  class ValidationTests {

    @Test
    @DisplayName("Should reject request when both content and embeds are null")
    void shouldRejectWhenBothContentAndEmbedsNull() {
      var request = new SendMessageRequest(CHANNEL_ID, null, null, BOT_TOKEN);

      assertThatThrownBy(() -> connector.sendMessage(request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("content")
          .hasMessageContaining("embeds");
    }

    @Test
    @DisplayName("Should reject request when content is blank and embeds is null")
    void shouldRejectWhenContentBlankAndEmbedsNull() {
      var request = new SendMessageRequest(CHANNEL_ID, "   ", null, BOT_TOKEN);

      assertThatThrownBy(() -> connector.sendMessage(request))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  // ---- Error scenarios ----

  @Nested
  @DisplayName("Error handling")
  class ErrorTests {

    @Test
    @DisplayName("Should map NOT_FOUND to CHANNEL_NOT_FOUND")
    void shouldMapNotFoundToChannelNotFound() {
      stubObjectMapper();
      var request = new SendMessageRequest(CHANNEL_ID, "Hello", null, BOT_TOKEN);
      when(apiClient.sendBotRequest(any(), any(), any(), any()))
          .thenThrow(new ConnectorException("NOT_FOUND", "Discord resource not found"));

      assertThatThrownBy(() -> connector.sendMessage(request))
          .isInstanceOf(ConnectorException.class)
          .satisfies(ex -> {
            ConnectorException ce = (ConnectorException) ex;
            assertThat(ce.getErrorCode()).isEqualTo("CHANNEL_NOT_FOUND");
            assertThat(ce.getMessage()).contains(CHANNEL_ID);
          });
    }

    @Test
    @DisplayName("Should propagate AUTHENTICATION_FAILED error")
    void shouldPropagateAuthenticationFailed() {
      stubObjectMapper();
      var request = new SendMessageRequest(CHANNEL_ID, "Hello", null, BOT_TOKEN);
      when(apiClient.sendBotRequest(any(), any(), any(), any()))
          .thenThrow(new ConnectorException("AUTHENTICATION_FAILED", "Invalid token"));

      assertThatThrownBy(() -> connector.sendMessage(request))
          .isInstanceOf(ConnectorException.class)
          .satisfies(ex -> {
            ConnectorException ce = (ConnectorException) ex;
            assertThat(ce.getErrorCode()).isEqualTo("AUTHENTICATION_FAILED");
          });
    }

    @Test
    @DisplayName("Should propagate DISCORD_API_ERROR")
    void shouldPropagateDiscordApiError() {
      stubObjectMapper();
      var request = new SendMessageRequest(CHANNEL_ID, "Hello", null, BOT_TOKEN);
      when(apiClient.sendBotRequest(any(), any(), any(), any()))
          .thenThrow(new ConnectorException("DISCORD_API_ERROR", "Internal server error"));

      assertThatThrownBy(() -> connector.sendMessage(request))
          .isInstanceOf(ConnectorException.class)
          .satisfies(ex -> {
            ConnectorException ce = (ConnectorException) ex;
            assertThat(ce.getErrorCode()).isEqualTo("DISCORD_API_ERROR");
          });
    }

    @Test
    @DisplayName("Should propagate rate limit (429) as retryable exception")
    void shouldPropagateRateLimitAsRetryable() {
      stubObjectMapper();
      var request = new SendMessageRequest(CHANNEL_ID, "Hello", null, BOT_TOKEN);
      when(apiClient.sendBotRequest(any(), any(), any(), any()))
          .thenThrow(new ConnectorRetryExceptionBuilder()
              .errorCode("RATE_LIMITED")
              .message("Rate limited")
              .build());

      assertThatThrownBy(() -> connector.sendMessage(request))
          .isInstanceOf(ConnectorRetryException.class);
    }
  }

  // ---- Payload building ----

  @Nested
  @DisplayName("Payload construction")
  class PayloadTests {

    @Test
    @DisplayName("Should build payload with content only")
    void shouldBuildPayloadContentOnly() throws Exception {
      stubObjectMapper();
      String payload = connector.buildMessagePayload("Hello", null);
      JsonNode parsed = MAPPER.readTree(payload);

      assertThat(parsed.has("content")).isTrue();
      assertThat(parsed.get("content").asText()).isEqualTo("Hello");
      assertThat(parsed.has("embeds")).isFalse();
    }

    @Test
    @DisplayName("Should build payload with embeds only")
    void shouldBuildPayloadEmbedsOnly() throws Exception {
      stubObjectMapper();
      var embeds = List.of(Map.of("title", "Test"));
      String payload = connector.buildMessagePayload(null, embeds);
      JsonNode parsed = MAPPER.readTree(payload);

      assertThat(parsed.has("content")).isFalse();
      assertThat(parsed.has("embeds")).isTrue();
      assertThat(parsed.get("embeds").isArray()).isTrue();
    }

    @Test
    @DisplayName("Should build payload with both content and embeds")
    void shouldBuildPayloadBoth() throws Exception {
      stubObjectMapper();
      var embeds = List.of(Map.of("title", "Embed"));
      String payload = connector.buildMessagePayload("Hello", embeds);
      JsonNode parsed = MAPPER.readTree(payload);

      assertThat(parsed.has("content")).isTrue();
      assertThat(parsed.has("embeds")).isTrue();
    }

    @Test
    @DisplayName("Should exclude blank content from payload")
    void shouldExcludeBlankContent() throws Exception {
      stubObjectMapper();
      var embeds = List.of(Map.of("title", "Test"));
      String payload = connector.buildMessagePayload("   ", embeds);
      JsonNode parsed = MAPPER.readTree(payload);

      assertThat(parsed.has("content")).isFalse();
      assertThat(parsed.has("embeds")).isTrue();
    }
  }
}
