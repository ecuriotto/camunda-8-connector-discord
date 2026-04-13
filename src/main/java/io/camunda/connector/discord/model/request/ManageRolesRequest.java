package io.camunda.connector.discord.model.request;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.constraints.NotEmpty;

/**
 * Input model for the {@code manageRoles} operation.
 *
 * <p>
 * Adds or removes a role from a guild member in Discord.
 *
 * @param guildId  the guild (server) ID
 * @param userId   the target user ID
 * @param roleId   the role ID to add or remove
 * @param action   the action to perform (Add or Remove)
 * @param botToken the Discord bot token for authentication
 */
public record ManageRolesRequest(
                @NotEmpty @TemplateProperty(group = "role", label = "Guild ID", description = "The ID of the Discord guild (server)") String guildId,

                @NotEmpty @TemplateProperty(group = "role", label = "User ID", description = "The ID of the guild member") String userId,

                @NotEmpty @TemplateProperty(group = "role", label = "Role ID", description = "The ID of the role to add or remove") String roleId,

                @TemplateProperty(group = "role", label = "Action", description = "Whether to add or remove the role", type = PropertyType.Dropdown, defaultValue = "Add", choices = {
                                @DropdownPropertyChoice(value = "Add", label = "Add"),
                                @DropdownPropertyChoice(value = "Remove", label = "Remove")
                }) String action,

                @NotEmpty @TemplateProperty(group = "authentication", label = "Bot token", description = "Discord bot token for API authentication. Use {{secrets.DISCORD_BOT_TOKEN}}.") String botToken) {
}
