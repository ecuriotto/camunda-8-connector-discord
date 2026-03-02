package io.camunda.connector.discord.model.response;

/**
 * Response model for the {@code sendWebhookMessage} operation.
 *
 * @param success whether the webhook message was sent successfully
 */
public record WebhookResponse(boolean success) {}
