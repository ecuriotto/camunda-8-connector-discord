package io.camunda.connector.discord.model.response;

/**
 * Response model for the {@code manageRoles} operation.
 *
 * @param success whether the role operation succeeded
 * @param action  the action that was performed (Add or Remove)
 * @param userId  the target user ID
 * @param roleId  the role ID that was added or removed
 */
public record RoleResponse(boolean success, String action, String userId, String roleId) {}
