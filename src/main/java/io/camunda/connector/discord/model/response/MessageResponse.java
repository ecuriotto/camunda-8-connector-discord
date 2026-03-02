package io.camunda.connector.discord.model.response;

/**
 * Response model for the {@code sendMessage} operation.
 *
 * @param messageId the ID of the created Discord message
 * @param channelId the ID of the channel the message was sent to
 * @param timestamp the ISO-8601 timestamp of the created message
 */
public record MessageResponse(String messageId, String channelId, String timestamp) {
}
