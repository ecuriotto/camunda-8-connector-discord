package io.camunda.connector.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.discord.client.DiscordApiClient;
import io.camunda.connector.discord.model.request.ManageRolesRequest;
import io.camunda.connector.discord.model.response.RoleResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManageRolesTest {

  private static final String GUILD_ID = "111222333";
  private static final String USER_ID = "444555666";
  private static final String ROLE_ID = "777888999";
  private static final String BOT_TOKEN = "test-bot-token";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock private DiscordApiClient apiClient;

  private DiscordConnector connector;

  @BeforeEach
  void setUp() {
    connector = new DiscordConnector(apiClient);
  }

  private ObjectNode emptyResponse() {
    return MAPPER.createObjectNode();
  }

  // ---- Success scenarios ----

  @Nested
  @DisplayName("Successful role management")
  class SuccessTests {

    @Test
    @DisplayName("Should add a role using PUT")
    void shouldAddRoleUsingPut() {
      var request = new ManageRolesRequest(GUILD_ID, USER_ID, ROLE_ID, "Add", BOT_TOKEN);
      String expectedEndpoint = "/guilds/" + GUILD_ID + "/members/" + USER_ID + "/roles/" + ROLE_ID;
      when(apiClient.sendBotRequest(eq("PUT"), eq(expectedEndpoint), isNull(), eq(BOT_TOKEN)))
          .thenReturn(emptyResponse());

      RoleResponse result = connector.manageRoles(request);

      assertThat(result.success()).isTrue();
      assertThat(result.action()).isEqualTo("Add");
      assertThat(result.userId()).isEqualTo(USER_ID);
      assertThat(result.roleId()).isEqualTo(ROLE_ID);
      verify(apiClient).sendBotRequest(eq("PUT"), eq(expectedEndpoint), isNull(), eq(BOT_TOKEN));
    }

    @Test
    @DisplayName("Should remove a role using DELETE")
    void shouldRemoveRoleUsingDelete() {
      var request = new ManageRolesRequest(GUILD_ID, USER_ID, ROLE_ID, "Remove", BOT_TOKEN);
      String expectedEndpoint = "/guilds/" + GUILD_ID + "/members/" + USER_ID + "/roles/" + ROLE_ID;
      when(apiClient.sendBotRequest(eq("DELETE"), eq(expectedEndpoint), isNull(), eq(BOT_TOKEN)))
          .thenReturn(emptyResponse());

      RoleResponse result = connector.manageRoles(request);

      assertThat(result.success()).isTrue();
      assertThat(result.action()).isEqualTo("Remove");
      assertThat(result.userId()).isEqualTo(USER_ID);
      assertThat(result.roleId()).isEqualTo(ROLE_ID);
      verify(apiClient).sendBotRequest(eq("DELETE"), eq(expectedEndpoint), isNull(), eq(BOT_TOKEN));
    }

    @Test
    @DisplayName("Should default to Add when action is null")
    void shouldDefaultToAddWhenActionNull() {
      var request = new ManageRolesRequest(GUILD_ID, USER_ID, ROLE_ID, null, BOT_TOKEN);
      String expectedEndpoint = "/guilds/" + GUILD_ID + "/members/" + USER_ID + "/roles/" + ROLE_ID;
      when(apiClient.sendBotRequest(eq("PUT"), eq(expectedEndpoint), isNull(), eq(BOT_TOKEN)))
          .thenReturn(emptyResponse());

      RoleResponse result = connector.manageRoles(request);

      assertThat(result.success()).isTrue();
      assertThat(result.action()).isEqualTo("Add");
    }

    @Test
    @DisplayName("Should handle case-insensitive action")
    void shouldHandleCaseInsensitiveAction() {
      var request = new ManageRolesRequest(GUILD_ID, USER_ID, ROLE_ID, "remove", BOT_TOKEN);
      String expectedEndpoint = "/guilds/" + GUILD_ID + "/members/" + USER_ID + "/roles/" + ROLE_ID;
      when(apiClient.sendBotRequest(eq("DELETE"), eq(expectedEndpoint), isNull(), eq(BOT_TOKEN)))
          .thenReturn(emptyResponse());

      RoleResponse result = connector.manageRoles(request);

      assertThat(result.success()).isTrue();
      assertThat(result.action()).isEqualTo("remove");
    }

    @Test
    @DisplayName("Should send null body for both PUT and DELETE")
    void shouldSendNullBody() {
      var request = new ManageRolesRequest(GUILD_ID, USER_ID, ROLE_ID, "Add", BOT_TOKEN);
      when(apiClient.sendBotRequest(any(), any(), any(), any()))
          .thenReturn(emptyResponse());

      connector.manageRoles(request);

      verify(apiClient).sendBotRequest(any(), any(), isNull(), any());
    }
  }

  // ---- Error scenarios ----

  @Nested
  @DisplayName("Error handling")
  class ErrorTests {

    @Test
    @DisplayName("Should map NOT_FOUND to MEMBER_OR_ROLE_NOT_FOUND")
    void shouldMapNotFoundToMemberOrRoleNotFound() {
      var request = new ManageRolesRequest(GUILD_ID, USER_ID, ROLE_ID, "Add", BOT_TOKEN);
      when(apiClient.sendBotRequest(any(), any(), any(), any()))
          .thenThrow(new ConnectorException("NOT_FOUND", "Resource not found"));

      assertThatThrownBy(() -> connector.manageRoles(request))
          .isInstanceOf(ConnectorException.class)
          .satisfies(ex -> {
            ConnectorException ce = (ConnectorException) ex;
            assertThat(ce.getErrorCode()).isEqualTo("MEMBER_OR_ROLE_NOT_FOUND");
            assertThat(ce.getMessage()).contains(GUILD_ID);
          });
    }

    @Test
    @DisplayName("Should map 403 AUTHENTICATION_FAILED to MISSING_PERMISSIONS")
    void shouldMap403ToMissingPermissions() {
      var request = new ManageRolesRequest(GUILD_ID, USER_ID, ROLE_ID, "Add", BOT_TOKEN);
      when(apiClient.sendBotRequest(any(), any(), any(), any()))
          .thenThrow(new ConnectorException("AUTHENTICATION_FAILED",
              "Discord authentication failed (HTTP 403): Missing Permissions"));

      assertThatThrownBy(() -> connector.manageRoles(request))
          .isInstanceOf(ConnectorException.class)
          .satisfies(ex -> {
            ConnectorException ce = (ConnectorException) ex;
            assertThat(ce.getErrorCode()).isEqualTo("MISSING_PERMISSIONS");
            assertThat(ce.getMessage()).contains(GUILD_ID);
          });
    }

    @Test
    @DisplayName("Should propagate 401 AUTHENTICATION_FAILED as-is")
    void shouldPropagate401AuthenticationFailed() {
      var request = new ManageRolesRequest(GUILD_ID, USER_ID, ROLE_ID, "Remove", BOT_TOKEN);
      when(apiClient.sendBotRequest(any(), any(), any(), any()))
          .thenThrow(new ConnectorException("AUTHENTICATION_FAILED",
              "Discord authentication failed (HTTP 401): Invalid token"));

      assertThatThrownBy(() -> connector.manageRoles(request))
          .isInstanceOf(ConnectorException.class)
          .satisfies(ex -> {
            ConnectorException ce = (ConnectorException) ex;
            assertThat(ce.getErrorCode()).isEqualTo("AUTHENTICATION_FAILED");
          });
    }

    @Test
    @DisplayName("Should propagate DISCORD_API_ERROR")
    void shouldPropagateDiscordApiError() {
      var request = new ManageRolesRequest(GUILD_ID, USER_ID, ROLE_ID, "Add", BOT_TOKEN);
      when(apiClient.sendBotRequest(any(), any(), any(), any()))
          .thenThrow(new ConnectorException("DISCORD_API_ERROR", "Internal server error"));

      assertThatThrownBy(() -> connector.manageRoles(request))
          .isInstanceOf(ConnectorException.class)
          .satisfies(ex -> {
            ConnectorException ce = (ConnectorException) ex;
            assertThat(ce.getErrorCode()).isEqualTo("DISCORD_API_ERROR");
          });
    }
  }

  // ---- Endpoint construction ----

  @Nested
  @DisplayName("Endpoint construction")
  class EndpointTests {

    @Test
    @DisplayName("Should construct correct endpoint path")
    void shouldConstructCorrectEndpoint() {
      var request = new ManageRolesRequest("g1", "u2", "r3", "Add", BOT_TOKEN);
      when(apiClient.sendBotRequest(any(), any(), any(), any()))
          .thenReturn(emptyResponse());

      connector.manageRoles(request);

      verify(apiClient).sendBotRequest(
          eq("PUT"), eq("/guilds/g1/members/u2/roles/r3"), isNull(), eq(BOT_TOKEN));
    }
  }
}
