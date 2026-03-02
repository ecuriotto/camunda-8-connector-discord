package io.camunda.connector.discord.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiscordAuthenticationTest {

  @Test
  void shouldRedactBotTokenInToString() {
    var auth = new DiscordAuthentication("super-secret-token", null);
    String result = auth.toString();

    assertThat(result).contains("[REDACTED]");
    assertThat(result).doesNotContain("super-secret-token");
  }

  @Test
  void shouldRedactWebhookUrlInToString() {
    var auth = new DiscordAuthentication(null, "https://discord.com/api/webhooks/123/secret-token");
    String result = auth.toString();

    assertThat(result).contains("[REDACTED]");
    assertThat(result).doesNotContain("secret-token");
  }

  @Test
  void shouldShowNullWhenFieldsAreNull() {
    var auth = new DiscordAuthentication(null, null);
    String result = auth.toString();

    assertThat(result).contains("botToken=null");
    assertThat(result).contains("webhookUrl=null");
  }

  @Test
  void shouldRedactBothWhenBothPresent() {
    var auth = new DiscordAuthentication("token", "https://webhook");
    String result = auth.toString();

    assertThat(result).doesNotContain("token");
    assertThat(result).doesNotContain("https://webhook");
    // Both should show [REDACTED]
    assertThat(result).isEqualTo("DiscordAuthentication[botToken=[REDACTED], webhookUrl=[REDACTED]]");
  }
}
