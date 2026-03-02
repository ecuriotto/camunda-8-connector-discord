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
import io.camunda.connector.discord.model.request.CreateChannelRequest;
import io.camunda.connector.discord.model.response.ChannelResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateChannelTest {

    private static final String GUILD_ID = "111222333";
    private static final String BOT_TOKEN = "test-bot-token";
    private static final String CHANNEL_ID = "444555666";
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

    private ObjectNode channelResponse(String name, int type) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", CHANNEL_ID);
        node.put("name", name);
        node.put("type", type);
        return node;
    }

    // ---- Success scenarios ----

    @Nested
    @DisplayName("Successful channel creation")
    class SuccessTests {

        @Test
        @DisplayName("Should create a text channel")
        void shouldCreateTextChannel() {
            stubObjectMapper();
            var request = new CreateChannelRequest(GUILD_ID, "general", "0", null, BOT_TOKEN);
            when(apiClient.sendBotRequest(eq("POST"), eq("/guilds/" + GUILD_ID + "/channels"), any(), eq(BOT_TOKEN)))
                    .thenReturn(channelResponse("general", 0));

            ChannelResponse result = connector.createChannel(request);

            assertThat(result.channelId()).isEqualTo(CHANNEL_ID);
            assertThat(result.channelName()).isEqualTo("general");
            assertThat(result.channelType()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should create a voice channel")
        void shouldCreateVoiceChannel() {
            stubObjectMapper();
            var request = new CreateChannelRequest(GUILD_ID, "voice-chat", "2", null, BOT_TOKEN);
            when(apiClient.sendBotRequest(eq("POST"), eq("/guilds/" + GUILD_ID + "/channels"), any(), eq(BOT_TOKEN)))
                    .thenReturn(channelResponse("voice-chat", 2));

            ChannelResponse result = connector.createChannel(request);

            assertThat(result.channelName()).isEqualTo("voice-chat");
            assertThat(result.channelType()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should create an announcement channel")
        void shouldCreateAnnouncementChannel() {
            stubObjectMapper();
            var request = new CreateChannelRequest(GUILD_ID, "news", "5", "Important updates", BOT_TOKEN);
            when(apiClient.sendBotRequest(eq("POST"), eq("/guilds/" + GUILD_ID + "/channels"), any(), eq(BOT_TOKEN)))
                    .thenReturn(channelResponse("news", 5));

            ChannelResponse result = connector.createChannel(request);

            assertThat(result.channelName()).isEqualTo("news");
            assertThat(result.channelType()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should default to text channel when type is null")
        void shouldDefaultToTextWhenTypeNull() throws Exception {
            stubObjectMapper();
            var request = new CreateChannelRequest(GUILD_ID, "default-channel", null, null, BOT_TOKEN);
            when(apiClient.sendBotRequest(any(), any(), any(), any()))
                    .thenReturn(channelResponse("default-channel", 0));

            connector.createChannel(request);

            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(apiClient).sendBotRequest(any(), any(), payloadCaptor.capture(), any());
            JsonNode parsed = MAPPER.readTree(payloadCaptor.getValue());
            assertThat(parsed.get("type").asInt()).isEqualTo(0);
        }
    }

    // ---- Payload construction ----

    @Nested
    @DisplayName("Payload construction")
    class PayloadTests {

        @Test
        @DisplayName("Should include topic when provided")
        void shouldIncludeTopicWhenProvided() throws Exception {
            stubObjectMapper();
            var request = new CreateChannelRequest(GUILD_ID, "test-channel", "0", "A test topic", BOT_TOKEN);
            when(apiClient.sendBotRequest(any(), any(), any(), any()))
                    .thenReturn(channelResponse("test-channel", 0));

            connector.createChannel(request);

            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(apiClient).sendBotRequest(any(), any(), payloadCaptor.capture(), any());
            JsonNode parsed = MAPPER.readTree(payloadCaptor.getValue());
            assertThat(parsed.get("name").asText()).isEqualTo("test-channel");
            assertThat(parsed.get("type").asInt()).isEqualTo(0);
            assertThat(parsed.get("topic").asText()).isEqualTo("A test topic");
        }

        @Test
        @DisplayName("Should exclude topic when null")
        void shouldExcludeTopicWhenNull() throws Exception {
            stubObjectMapper();
            var request = new CreateChannelRequest(GUILD_ID, "no-topic", "2", null, BOT_TOKEN);
            when(apiClient.sendBotRequest(any(), any(), any(), any()))
                    .thenReturn(channelResponse("no-topic", 2));

            connector.createChannel(request);

            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(apiClient).sendBotRequest(any(), any(), payloadCaptor.capture(), any());
            JsonNode parsed = MAPPER.readTree(payloadCaptor.getValue());
            assertThat(parsed.has("topic")).isFalse();
        }
    }

    // ---- Error scenarios ----

    @Nested
    @DisplayName("Error handling")
    class ErrorTests {

        @Test
        @DisplayName("Should map NOT_FOUND to GUILD_NOT_FOUND")
        void shouldMapNotFoundToGuildNotFound() {
            stubObjectMapper();
            var request = new CreateChannelRequest(GUILD_ID, "test", "0", null, BOT_TOKEN);
            when(apiClient.sendBotRequest(any(), any(), any(), any()))
                    .thenThrow(new ConnectorException("NOT_FOUND", "Resource not found"));

            assertThatThrownBy(() -> connector.createChannel(request))
                    .isInstanceOf(ConnectorException.class)
                    .satisfies(ex -> {
                        ConnectorException ce = (ConnectorException) ex;
                        assertThat(ce.getErrorCode()).isEqualTo("GUILD_NOT_FOUND");
                        assertThat(ce.getMessage()).contains(GUILD_ID);
                    });
        }

        @Test
        @DisplayName("Should map 403 AUTHENTICATION_FAILED to MISSING_PERMISSIONS")
        void shouldMap403ToMissingPermissions() {
            stubObjectMapper();
            var request = new CreateChannelRequest(GUILD_ID, "test", "0", null, BOT_TOKEN);
            when(apiClient.sendBotRequest(any(), any(), any(), any()))
                    .thenThrow(new ConnectorException("AUTHENTICATION_FAILED",
                            "Discord authentication failed (HTTP 403): Missing Permissions"));

            assertThatThrownBy(() -> connector.createChannel(request))
                    .isInstanceOf(ConnectorException.class)
                    .satisfies(ex -> {
                        ConnectorException ce = (ConnectorException) ex;
                        assertThat(ce.getErrorCode()).isEqualTo("MISSING_PERMISSIONS");
                    });
        }

        @Test
        @DisplayName("Should propagate 401 AUTHENTICATION_FAILED as-is")
        void shouldPropagate401AuthenticationFailed() {
            stubObjectMapper();
            var request = new CreateChannelRequest(GUILD_ID, "test", "0", null, BOT_TOKEN);
            when(apiClient.sendBotRequest(any(), any(), any(), any()))
                    .thenThrow(new ConnectorException("AUTHENTICATION_FAILED",
                            "Discord authentication failed (HTTP 401): Invalid token"));

            assertThatThrownBy(() -> connector.createChannel(request))
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
            var request = new CreateChannelRequest(GUILD_ID, "test", "0", null, BOT_TOKEN);
            when(apiClient.sendBotRequest(any(), any(), any(), any()))
                    .thenThrow(new ConnectorException("DISCORD_API_ERROR", "Internal server error"));

            assertThatThrownBy(() -> connector.createChannel(request))
                    .isInstanceOf(ConnectorException.class)
                    .satisfies(ex -> {
                        ConnectorException ce = (ConnectorException) ex;
                        assertThat(ce.getErrorCode()).isEqualTo("DISCORD_API_ERROR");
                    });
        }
    }
}
