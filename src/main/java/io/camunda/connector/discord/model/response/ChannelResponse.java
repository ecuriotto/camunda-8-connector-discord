package io.camunda.connector.discord.model.response;

/**
 * Response model for the {@code createChannel} operation.
 *
 * @param channelId   the ID of the newly created channel
 * @param channelName the name of the created channel
 * @param channelType the numeric type of the created channel
 */
public record ChannelResponse(String channelId, String channelName, int channelType) {
}
