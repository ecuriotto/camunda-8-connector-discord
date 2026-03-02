# Copilot Instructions — Camunda 8 Discord Connector

## Project Overview

This project is a **Camunda 8 Outbound Connector** that integrates Discord into BPMN processes. It was scaffolded from the official [connector-template-outbound](https://github.com/camunda/connector-template-outbound) and uses the **Camunda Connector SDK** (annotations-based approach with `OutboundConnectorProvider`).

The connector allows process designers to send messages, embeds, and manage channels in Discord directly from their BPMN diagrams.

---

## Technology Stack

- **Java 21** (runtime and connectors)
- **Camunda Connector SDK** (`io.camunda.connector:connector-core`)
- **Maven** for build and dependency management
- **Jackson** for JSON serialization/deserialization
- **SLF4J + Logback** for logging
- **JUnit 5** for testing
- **Discord REST API v10** (`https://discord.com/api/v10`)

---

## Architecture & Patterns

### Connector Class Structure

This connector follows the **annotations-based approach** using `OutboundConnectorProvider`. The main connector class:

- Is annotated with `@OutboundConnector(name = "Discord Connector", type = "io.camunda:discord:1")`
- Is annotated with `@ElementTemplate(...)` for automatic element template generation
- Implements `OutboundConnectorProvider`
- Defines multiple operations using `@Operation` annotations
- Each operation method accepts input parameters annotated with `@Variable` and returns a response object

Example pattern from the template:

```java
@OutboundConnector(name = "Discord Connector", type = "io.camunda:discord:1")
@ElementTemplate(
    id = "io.camunda.connector.discord.v1",
    name = "Discord Connector",
    version = 1,
    description = "Send messages and manage channels in Discord",
    icon = "icon.svg",
    documentationRef = "https://github.com/YOUR_USERNAME/camunda-8-connector-discord"
)
public class DiscordConnector implements OutboundConnectorProvider {

    @Operation(id = "sendMessage", name = "Send message")
    public Object sendMessage(@Variable SendMessageRequest request) {
        // implementation
    }
}
```

### Package Structure

```
src/main/java/io/camunda/connector/discord/
├── DiscordConnector.java              # Main connector class with @Operation methods
├── model/
│   ├── request/                       # Input record classes per operation
│   │   ├── SendMessageRequest.java
│   │   ├── SendWebhookMessageRequest.java
│   │   ├── CreateChannelRequest.java
│   │   └── ManageRolesRequest.java
│   └── response/                      # Output record classes
│       ├── MessageResponse.java
│       ├── ChannelResponse.java
│       └── RoleResponse.java
├── auth/
│   └── DiscordAuthentication.java     # Authentication model (bot token or webhook URL)
└── client/
    └── DiscordApiClient.java          # HTTP client wrapper for Discord REST API
```

### SPI Registration

The file `src/main/resources/META-INF/services/io.camunda.connector.api.outbound.OutboundConnectorProvider` must contain:

```
io.camunda.connector.discord.DiscordConnector
```

---

## Discord API Reference

Base URL: `https://discord.com/api/v10`

### Authentication Methods

The connector should support two authentication modes:

1. **Bot Token** — For full API access (send messages, manage channels, manage roles)
   - Header: `Authorization: Bot <token>`
   - Requires a Discord Application with a Bot user

2. **Webhook URL** — For simple message sending (no bot required)
   - Format: `https://discord.com/api/webhooks/{webhook.id}/{webhook.token}`
   - No auth header needed; the token is embedded in the URL

### Key Endpoints

| Operation               | Method | Endpoint                                               | Auth                |
| ----------------------- | ------ | ------------------------------------------------------ | ------------------- |
| Send Message (Bot)      | POST   | `/channels/{channel.id}/messages`                      | Bot Token           |
| Send Webhook Message    | POST   | `/webhooks/{webhook.id}/{webhook.token}`               | None (token in URL) |
| Create Channel          | POST   | `/guilds/{guild.id}/channels`                          | Bot Token           |
| Add Role to Member      | PUT    | `/guilds/{guild.id}/members/{user.id}/roles/{role.id}` | Bot Token           |
| Remove Role from Member | DELETE | `/guilds/{guild.id}/members/{user.id}/roles/{role.id}` | Bot Token           |

### Message Payload (Bot & Webhook)

```json
{
  "content": "Hello from Camunda!",
  "embeds": [
    {
      "title": "Process Update",
      "description": "Order #1234 has been approved.",
      "color": 3066993,
      "fields": [
        { "name": "Status", "value": "Approved", "inline": true },
        { "name": "Assignee", "value": "John Doe", "inline": true }
      ]
    }
  ]
}
```

### Create Channel Payload

```json
{
  "name": "new-channel",
  "type": 0,
  "topic": "Created by Camunda process"
}
```

Channel types: `0` = Text, `2` = Voice, `5` = Announcement, `13` = Stage, `15` = Forum.

---

## Operations to Implement

### 1. `sendMessage` — Send a message via Bot Token

- **Input**: `channelId` (String, required), `content` (String, optional), `embeds` (JSON/FEEL expression, optional), `botToken` (String, secret)
- **Output**: `messageId`, `channelId`, `timestamp`
- **Error codes**: `DISCORD_API_ERROR`, `AUTHENTICATION_FAILED`, `CHANNEL_NOT_FOUND`

### 2. `sendWebhookMessage` — Send a message via Webhook URL

- **Input**: `webhookUrl` (String, required, secret), `content` (String, optional), `username` (String, optional), `avatarUrl` (String, optional), `embeds` (JSON/FEEL expression, optional)
- **Output**: `success` (boolean)
- **Error codes**: `DISCORD_API_ERROR`, `INVALID_WEBHOOK_URL`

### 3. `createChannel` — Create a channel in a guild

- **Input**: `guildId` (String, required), `channelName` (String, required), `channelType` (Dropdown: Text/Voice/Announcement, default Text), `topic` (String, optional), `botToken` (String, secret)
- **Output**: `channelId`, `channelName`, `channelType`
- **Error codes**: `DISCORD_API_ERROR`, `AUTHENTICATION_FAILED`, `GUILD_NOT_FOUND`, `MISSING_PERMISSIONS`

### 4. `manageRoles` — Add or remove a role from a guild member

- **Input**: `guildId` (String, required), `userId` (String, required), `roleId` (String, required), `action` (Dropdown: Add/Remove), `botToken` (String, secret)
- **Output**: `success` (boolean), `action`, `userId`, `roleId`
- **Error codes**: `DISCORD_API_ERROR`, `AUTHENTICATION_FAILED`, `MEMBER_NOT_FOUND`, `ROLE_NOT_FOUND`

---

## Input Validation

Use **Jakarta Bean Validation** annotations on request record fields:

```java
public record SendMessageRequest(
    @NotEmpty String channelId,
    String content,
    Object embeds,
    @NotEmpty String botToken
) {}
```

At least one of `content` or `embeds` must be provided for message operations. Implement a custom validation or check in the operation method.

---

## Secrets Handling

Sensitive fields (`botToken`, `webhookUrl`) should be referenced as Camunda secrets in the element template. Users will configure them as `{{secrets.DISCORD_BOT_TOKEN}}` or `{{secrets.DISCORD_WEBHOOK_URL}}`.

The Connector SDK automatically replaces `secrets.*` references at runtime.

---

## Error Handling

- Throw `ConnectorException(errorCode, message)` for non-retryable errors (e.g., invalid input, 4xx responses)
- Throw `ConnectorRetryExceptionBuilder` for retryable errors (e.g., rate limiting — Discord returns HTTP 429)
- When Discord returns 429 (rate limited), extract `retry_after` from the response body and use it as `backoffDuration`

---

## HTTP Client Guidelines

- Use `java.net.http.HttpClient` (built-in, no extra dependencies) or Apache HttpClient 5 (already available in the connector runtime)
- Set `User-Agent: DiscordBot (https://github.com/YOUR_USERNAME/camunda-8-connector-discord, 1.0)`
- Set `Content-Type: application/json`
- Parse responses with Jackson `ObjectMapper`

---

## Testing Guidelines

- Write **unit tests** for each operation using mocked HTTP responses
- Use the Connector SDK test utilities: `OutboundConnectorContextBuilder` to build test contexts
- Test validation: ensure missing required fields throw appropriate errors
- Test error handling: simulate 4xx, 5xx, and 429 responses
- Place tests in `src/test/java/io/camunda/connector/discord/`

Example test pattern:

```java
@Test
void shouldSendMessage() {
    // Given
    var input = new SendMessageRequest("123456", "Hello", null, "bot-token");
    // When
    var result = connector.sendMessage(input);
    // Then
    assertNotNull(result);
}
```

---

## Element Template

The element template JSON is **auto-generated** during `mvn clean package` by the Element Template Generator based on the `@ElementTemplate`, `@Operation`, and `@Variable` annotations. The generated file is placed in `element-templates/`.

Do NOT manually create or edit the element template JSON unless absolutely necessary.

---

## Build & Run

```bash
# Build and generate element template
mvn clean package

# Run tests
mvn clean verify

# Run locally against a Camunda cluster
# (configure application.properties with cluster credentials first)
mvn spring-boot:run -pl connector-runtime
```

---

## Files to Modify from Template

| Original File                            | Rename / Update To                                        |
| ---------------------------------------- | --------------------------------------------------------- |
| `io.camunda.example` package             | `io.camunda.connector.discord`                            |
| `MyConnector.java`                       | `DiscordConnector.java`                                   |
| `EchoRequest.java` / `EchoResponse.java` | Replace with Discord-specific request/response records    |
| `pom.xml` `<artifactId>`                 | `connector-discord`                                       |
| `pom.xml` `<name>`                       | `Discord Connector`                                       |
| `pom.xml` `<description>`                | `Camunda 8 Connector for Discord`                         |
| SPI file                                 | Update to `io.camunda.connector.discord.DiscordConnector` |
| `icon.svg`                               | Replace with Discord logo (or a custom icon)              |

---

## Similarity with Slack Connector

The official Camunda Slack outbound connector is the closest architectural reference for this Discord connector. The Slack connector uses the older pattern (OutboundConnectorFunction + @JsonSubTypes polymorphism), while our project uses the newer annotations-based pattern (OutboundConnectorProvider + @Operation). Adapt the patterns accordingly.

Key Slack Connector Files (GitHub)
All files are in the camunda/connectors monorepo under connectors/slack/:

File URL What to learn from it
SlackFunction.java https://github.com/camunda/connectors/blob/main/connectors/slack/src/main/java/io/camunda/connector/slack/outbound/SlackFunction.java Main connector class: @OutboundConnector + @ElementTemplate annotations, property groups, how execute() delegates to the request object
SlackRequest.java https://github.com/camunda/connectors/blob/main/connectors/slack/src/main/java/io/camunda/connector/slack/outbound/SlackRequest.java Request record with @JsonSubTypes for polymorphic operation dispatch, token field with @TemplateProperty, @NestedProperties, and the invoke() pattern
SlackRequestData.java https://github.com/camunda/connectors/blob/main/connectors/slack/src/main/java/io/camunda/connector/slack/outbound/model/SlackRequestData.java Sealed interface with @TemplateDiscriminatorProperty for method selection dropdown, @TemplateSubType
ChatPostMessageData.java https://github.com/camunda/connectors/blob/main/connectors/slack/src/main/java/io/camunda/connector/slack/outbound/model/ChatPostMessageData.java Operation implementation: record with @TemplateSubType, @TemplateProperty annotations for each field, invoke() method calling the API
ConversationsCreateData.java https://github.com/camunda/connectors/blob/main/connectors/slack/src/main/java/io/camunda/connector/slack/outbound/model/ConversationsCreateData.java Channel creation with Dropdown for visibility (Public/Private), validation patterns, @PropertyConstraints
ReactionsAddData.java https://github.com/camunda/connectors/blob/main/connectors/slack/src/main/java/io/camunda/connector/slack/outbound/model/ReactionsAddData.java Simple operation: channel + emoji + timestamp fields, clean invoke() with error handling
ReactionsAddDataTest.java (in src/test/) Test pattern: @Mock MethodsClient, ArgumentCaptor, asserting correct values passed to API client

Architectural Mapping: Slack → Discord
Since our project uses the newer OutboundConnectorProvider + @Operation pattern (from the template), here's how the Slack patterns map:

Slack Pattern (older) Discord Pattern (newer, our project)
SlackFunction implements OutboundConnectorFunction - DiscordConnector implements OutboundConnectorProvider
SlackRequestData sealed interface + @TemplateDiscriminatorProperty - @Operation methods directly on DiscordConnector
ChatPostMessageData record with @TemplateSubType + invoke() - @Operation(id="sendMessage") sendMessage(@Variable SendMessageRequest req)
SlackRequest<T> with @JsonSubTypes polymorphism - Not needed: each @Operation method has its own typed input
context.bindVariables(SlackRequest.class) in execute() - SDK handles binding automatically per @Operation
SlackResponse marker interface - Return plain records or objects from each operation method

Key Patterns to Replicate from Slack
Token/secret as a top-level field — Slack puts token at the request level with @TemplateProperty(group = "authentication", feel = FeelMode.optional). Do the same for botToken / webhookUrl.

Property groups — Slack defines groups like authentication, method, message, channel. Define similar groups in @ElementTemplate(propertyGroups = {...}).

Dropdown for operation type — Slack uses @TemplateDiscriminatorProperty for method selection. With @Operation, the SDK generates this automatically.

Validation with @NotBlank + @PropertyConstraints — Slack uses both Jakarta validation and template-level constraints. Follow the same approach.

Error handling pattern — Slack checks response.isOk() and throws RuntimeException with the error message. For Discord, throw ConnectorException with specific error codes instead.

Response records — Slack uses simple records like ReactionsAddSlackResponse(). Create similar lightweight response records.

toString() redacting secrets — Slack's SlackRequest.toString() redacts the token: "token=[REDACTED]". Do the same for botToken and webhookUrl.

What NOT to Copy from Slack
Slack SDK dependency (com.slack.api) — Discord has no equivalent Java SDK. Use java.net.http.HttpClient directly against the Discord REST API.
OutboundConnectorFunction pattern — We use the newer OutboundConnectorProvider pattern with @Operation annotations.
@JsonSubTypes polymorphism — Not needed with @Operation-based approach.

## Coding Conventions

- Use **Java records** for request/response models (immutable, concise)
- Use **SLF4J** for logging (`LoggerFactory.getLogger(...)`)
- Follow **Google Java Style** formatting
- Keep methods focused and small; extract HTTP logic into `DiscordApiClient`
- Document all public methods with Javadoc
- Use `@NotEmpty`, `@NotNull` from Jakarta validation on required fields
- Use descriptive error codes in `ConnectorException` (e.g., `DISCORD_API_ERROR`, not generic codes)
