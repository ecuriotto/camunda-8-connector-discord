package io.camunda.connector.discord.model.request;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.constraints.NotEmpty;

/**
 * Input model for the {@code sendMessage} operation.
 *
 * <p>
 * Sends a message to a Discord channel using a bot token. At least one of
 * {@code content} or {@code embeds} must be provided.
 *
 * @param channelId the target Discord channel ID
 * @param content   plain-text message content (optional if embeds is provided)
 * @param embeds    an array of embed objects (optional if content is provided)
 * @param botToken  the Discord bot token for authentication
 */
public record SendMessageRequest(
        @NotEmpty @TemplateProperty(group = "message", label = "Channel ID", description = "The ID of the Discord channel to send the message to") String channelId,

        @TemplateProperty(group = "message", label = "Message content", description = "Plain text message content. At least one of content or embeds must be provided.", optional = true) String content,

        @TemplateProperty(group = "message", label = "Embeds", description = "Array of embed objects (as FEEL expression or JSON). At least one of content or embeds must be provided.", optional = true, type = PropertyType.Text, feel = FeelMode.required) Object embeds,

        @NotEmpty @TemplateProperty(group = "authentication", label = "Bot token", description = "Discord bot token for API authentication. Use {{secrets.DISCORD_BOT_TOKEN}}.") String botToken) {

    /**
     * Validates that at least one of {@code content} or {@code embeds} is provided.
     *
     * @throws IllegalArgumentException if both are null/empty
     */
    public void validate() {
        boolean hasContent = content != null && !content.isBlank();
        boolean hasEmbeds = embeds != null;
        if (!hasContent && !hasEmbeds) {
            throw new IllegalArgumentException(
                    "At least one of 'content' or 'embeds' must be provided for sendMessage");
        }
    }
}
