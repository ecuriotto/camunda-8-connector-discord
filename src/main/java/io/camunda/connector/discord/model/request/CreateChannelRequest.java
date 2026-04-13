package io.camunda.connector.discord.model.request;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.constraints.NotEmpty;

/**
 * Input model for the {@code createChannel} operation.
 *
 * <p>
 * Creates a new channel in a Discord guild (server).
 *
 * @param guildId     the target guild (server) ID
 * @param channelName the name for the new channel
 * @param channelType the channel type (Text=0, Voice=2, Announcement=5)
 * @param topic       optional channel topic/description
 * @param botToken    the Discord bot token for authentication
 */
public record CreateChannelRequest(
        @NotEmpty @TemplateProperty(group = "channel", label = "Guild ID", description = "The ID of the Discord guild (server) to create the channel in") String guildId,

        @NotEmpty @TemplateProperty(group = "channel", label = "Channel name", description = "Name for the new channel") String channelName,

        @TemplateProperty(group = "channel", label = "Channel type", description = "The type of channel to create", type = PropertyType.Dropdown, defaultValue = "0", choices = {
                @DropdownPropertyChoice(value = "0", label = "Text"),
                @DropdownPropertyChoice(value = "2", label = "Voice"),
                @DropdownPropertyChoice(value = "5", label = "Announcement")
        }) String channelType,

        @TemplateProperty(group = "channel", label = "Topic", description = "Channel topic or description (optional)", optional = true) String topic,

        @NotEmpty @TemplateProperty(group = "authentication", label = "Bot token", description = "Discord bot token for API authentication. Use {{secrets.DISCORD_BOT_TOKEN}}.") String botToken) {

    /**
     * Returns the numeric channel type, defaulting to 0 (Text) if not set or
     * invalid.
     */
    public int channelTypeAsInt() {
        if (channelType == null || channelType.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(channelType);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
