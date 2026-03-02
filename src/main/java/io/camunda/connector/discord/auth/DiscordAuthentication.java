package io.camunda.connector.discord.auth;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;

/**
 * Authentication model for Discord API access.
 *
 * <p>
 * Supports two modes:
 * <ul>
 * <li><b>Bot Token</b> — for full API access (send messages, manage channels,
 * roles)</li>
 * <li><b>Webhook URL</b> — for simple message sending (no bot required)</li>
 * </ul>
 *
 * <p>
 * Sensitive fields are redacted in {@link #toString()} to prevent secret
 * leakage in logs.
 */
public record DiscordAuthentication(
        @TemplateProperty(group = "authentication", label = "Bot Token", description = "Discord bot token for API authentication. Use {{secrets.DISCORD_BOT_TOKEN}}.", type = PropertyType.String, optional = true) String botToken,

        @TemplateProperty(group = "authentication", label = "Webhook URL", description = "Discord webhook URL for sending messages. Use {{secrets.DISCORD_WEBHOOK_URL}}.", type = PropertyType.String, optional = true) String webhookUrl) {

    @Override
    public String toString() {
        return "DiscordAuthentication["
                + "botToken=" + (botToken != null ? "[REDACTED]" : "null")
                + ", webhookUrl=" + (webhookUrl != null ? "[REDACTED]" : "null")
                + "]";
    }
}
