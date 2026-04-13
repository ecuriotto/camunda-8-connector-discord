package io.camunda.connector.discord.model.request;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.constraints.NotEmpty;

/**
 * Input model for the {@code sendWebhookMessage} operation.
 *
 * <p>
 * Sends a message to a Discord channel via a webhook URL. At least one of
 * {@code content} or {@code embeds} must be provided.
 *
 * @param webhookUrl the full Discord webhook URL (contains auth token)
 * @param content    plain-text message content (optional if embeds is provided)
 * @param username   override the webhook's default username (optional)
 * @param avatarUrl  override the webhook's default avatar URL (optional)
 * @param embeds     an array of embed objects (optional if content is provided)
 */
public record SendWebhookMessageRequest(
        @NotEmpty @TemplateProperty(group = "authentication", label = "Webhook URL", description = "Discord webhook URL. Use {{secrets.DISCORD_WEBHOOK_URL}}.") String webhookUrl,

        @TemplateProperty(group = "message", label = "Message content", description = "Plain text message content. At least one of content or embeds must be provided.", optional = true) String content,

        @TemplateProperty(group = "message", label = "Username", description = "Override the webhook's default username (optional).", optional = true) String username,

        @TemplateProperty(group = "message", label = "Avatar URL", description = "Override the webhook's default avatar (optional).", optional = true) String avatarUrl,

        @TemplateProperty(group = "message", label = "Embeds", description = "Array of embed objects (as FEEL expression or JSON). At least one of content or embeds must be provided.", optional = true, type = PropertyType.Text, feel = FeelMode.required) Object embeds) {

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
                    "At least one of 'content' or 'embeds' must be provided for sendWebhookMessage");
        }
    }
}
