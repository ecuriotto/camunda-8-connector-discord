# Camunda 8 Discord Connector

A [Camunda 8 Outbound Connector](https://docs.camunda.io/docs/components/connectors/custom-built-connectors/connector-sdk/) that integrates Discord into BPMN processes. Send messages, manage channels, and control roles directly from your process diagrams.

## Supported Operations

### 1. Send Message (Bot)

Send a message to a Discord channel using a bot token.

| Input      | Type   | Required | Description                                           |
|------------|--------|----------|-------------------------------------------------------|
| `channelId`| String | Yes      | The Discord channel ID                                |
| `content`  | String | No*      | Plain text message content                            |
| `embeds`   | JSON   | No*      | Array of embed objects (FEEL expression)              |
| `botToken` | String | Yes      | Bot token — use `{{secrets.DISCORD_BOT_TOKEN}}`       |

*At least one of `content` or `embeds` must be provided.

**Output:** `messageId`, `channelId`, `timestamp`

### 2. Send Webhook Message

Send a message via a Discord webhook URL (no bot required).

| Input       | Type   | Required | Description                                            |
|-------------|--------|----------|--------------------------------------------------------|
| `webhookUrl`| String | Yes      | Full webhook URL — use `{{secrets.DISCORD_WEBHOOK_URL}}`|
| `content`   | String | No*      | Plain text message content                             |
| `username`  | String | No       | Override the webhook's default username                 |
| `avatarUrl` | String | No       | Override the webhook's default avatar                   |
| `embeds`    | JSON   | No*      | Array of embed objects (FEEL expression)               |

*At least one of `content` or `embeds` must be provided.

**Output:** `success` (boolean)

### 3. Create Channel

Create a new channel in a Discord guild (server).

| Input         | Type     | Required | Description                                       |
|---------------|----------|----------|---------------------------------------------------|
| `guildId`     | String   | Yes      | The guild (server) ID                             |
| `channelName` | String   | Yes      | Name for the new channel                          |
| `channelType` | Dropdown | No       | Text (default), Voice, or Announcement            |
| `topic`       | String   | No       | Channel topic/description                         |
| `botToken`    | String   | Yes      | Bot token — use `{{secrets.DISCORD_BOT_TOKEN}}`   |

**Output:** `channelId`, `channelName`, `channelType`

### 4. Manage Roles

Add or remove a role from a guild member.

| Input      | Type     | Required | Description                                       |
|------------|----------|----------|---------------------------------------------------|
| `guildId`  | String   | Yes      | The guild (server) ID                             |
| `userId`   | String   | Yes      | The target user ID                                |
| `roleId`   | String   | Yes      | The role ID to add or remove                      |
| `action`   | Dropdown | No       | Add (default) or Remove                           |
| `botToken` | String   | Yes      | Bot token — use `{{secrets.DISCORD_BOT_TOKEN}}`   |

**Output:** `success`, `action`, `userId`, `roleId`

## Error Codes

| Code                      | Description                                  |
|---------------------------|----------------------------------------------|
| `CHANNEL_NOT_FOUND`       | The specified channel does not exist          |
| `GUILD_NOT_FOUND`         | The specified guild does not exist            |
| `MEMBER_OR_ROLE_NOT_FOUND`| The member or role was not found in the guild |
| `MISSING_PERMISSIONS`     | Bot lacks required permissions                |
| `INVALID_WEBHOOK_URL`     | Webhook URL is invalid or expired             |
| `AUTHENTICATION_FAILED`   | Bot token is invalid or missing               |
| `RATE_LIMITED`             | Discord rate limit hit (retryable)            |
| `DISCORD_API_ERROR`       | General Discord API error                     |

## Configuration

### Discord Bot Setup

1. Go to the [Discord Developer Portal](https://discord.com/developers/applications)
2. Create a new Application and add a Bot user
3. Copy the bot token
4. Invite the bot to your server with appropriate permissions (Send Messages, Manage Channels, Manage Roles)

### Camunda Secrets

Store sensitive values as [Camunda secrets](https://docs.camunda.io/docs/components/console/manage-clusters/manage-secrets/):

- `DISCORD_BOT_TOKEN` — Your Discord bot token
- `DISCORD_WEBHOOK_URL` — Your Discord webhook URL (for webhook operations)

Reference them in the connector as `{{secrets.DISCORD_BOT_TOKEN}}` and `{{secrets.DISCORD_WEBHOOK_URL}}`.

## Build

```bash
# Build and generate element template
mvn clean package

# Run tests only
mvn clean verify
```

The generated element template is located at [`element-templates/discord-connector.json`](./element-templates/discord-connector.json).

## Deployment

### Self-Managed

1. Build the connector JAR: `mvn clean package`
2. Deploy the fat JAR (with dependencies) to your Connector Runtime
3. Import the element template into Camunda Modeler

### SaaS

1. Build the connector JAR: `mvn clean package`
2. Upload the element template to Web Modeler and publish it
3. Deploy the connector to your SaaS environment

### Local Development

1. Start a local Camunda environment (e.g., via [Docker Compose](https://github.com/camunda/camunda-distributions))
2. Run `io.camunda.connector.discord.LocalConnectorRuntime` from the test sources
3. Add the element template to your Modeler configuration

## Technology Stack

- Java 21
- Camunda Connector SDK 8.8
- Discord REST API v10
- `java.net.http.HttpClient` (no third-party HTTP libraries)
- Jackson for JSON processing
- JUnit 5 + Mockito + AssertJ for testing

## License

[Apache License 2.0](./LICENSE)

