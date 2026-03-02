# Discord Connector — Implementation Plan

> Step-by-step plan to transform the scaffolded connector template into a fully functional Camunda 8 Discord Connector.
>
> **Convention:** Every sprint ends with a **Verification Checklist** containing automated commands (marked with `[AUTO]`) and manual inspections (marked with `[MANUAL]`). A sprint is considered complete only when every item passes.

---

## Sprint 1 — Project Scaffolding & Renaming

Refactor the template project into the Discord connector structure. No business logic yet — only skeleton classes, correct packages, and a green build.

### 1.1 — Update Maven Coordinates

- Change `pom.xml` `<artifactId>` → `connector-discord`
- Change `<name>` → `Discord Connector`
- Change `<description>` → `Camunda 8 Connector for Discord`
- Verify the project still compiles after changes (`mvn compile`)

### 1.2 — Rename Package & Main Connector Class

- Move source files from `io.camunda.example` → `io.camunda.connector.discord`
- Rename `MyConnector.java` → `DiscordConnector.java`
- Update `@OutboundConnector` annotation:
  - `name = "Discord Connector"`
  - `type = "io.camunda:discord:1"`
- Update `@ElementTemplate` annotation with Discord-specific values:
  - `id = "io.camunda.connector.discord.v1"`
  - `name = "Discord Connector"`
  - `description = "Send messages and manage channels in Discord"`
- Remove the template `echo` and `addTwoNumbers` operation methods (leave the class empty for now)
- Update element-template-generator plugin config in `pom.xml` to point to the new class and template ID

### 1.3 — Update SPI Registration

- Update `META-INF/services/io.camunda.connector.api.outbound.OutboundConnectorProvider` to contain:
  `io.camunda.connector.discord.DiscordConnector`

### 1.4 — Create Package Structure (Empty Skeleton)

Create the target package layout with placeholder files:

```
io.camunda.connector.discord/
├── DiscordConnector.java
├── model/
│   ├── request/    (empty for now)
│   └── response/   (empty for now)
├── auth/           (empty for now)
└── client/         (empty for now)
```

### 1.5 — Delete Template Files & Clean Up

- Delete `EchoRequest.java`, `EchoResponse.java`, `Authentication.java` from old package
- Delete old `io.camunda.example` package directory
- Remove or update existing tests that reference the old classes (`MyConnectorIntegrationTest`, `LocalConnectorRuntime`)
- Clean up the `element-templates/my-connector.json` (will be regenerated)
- Run `mvn clean compile` — must pass with zero errors

### 1.6 — Verification Checklist

| #  | Type     | Check | How to verify |
|----|----------|-------|---------------|
| 1  | `[AUTO]`   | Project compiles | `mvn clean compile` exits with `BUILD SUCCESS` |
| 2  | `[AUTO]`   | No references to old package | `grep -r "io.camunda.example" src/` returns no results |
| 3  | `[AUTO]`   | No old template files remain | `find src/ -name "MyConnector.java" -o -name "EchoRequest.java" -o -name "EchoResponse.java"` returns nothing |
| 4  | `[AUTO]`   | SPI file points to new class | `cat src/main/resources/META-INF/services/io.camunda.connector.api.outbound.OutboundConnectorProvider` outputs `io.camunda.connector.discord.DiscordConnector` |
| 5  | `[MANUAL]` | `DiscordConnector.java` exists at `src/main/java/io/camunda/connector/discord/DiscordConnector.java` | Open file, verify `@OutboundConnector(name = "Discord Connector", type = "io.camunda:discord:1")` |
| 6  | `[MANUAL]` | Package directories created | Confirm `model/request/`, `model/response/`, `auth/`, `client/` directories exist under `src/main/java/io/camunda/connector/discord/` |
| 7  | `[MANUAL]` | `pom.xml` updated | Confirm `<artifactId>connector-discord</artifactId>`, `<name>Discord Connector</name>`, `<description>Camunda 8 Connector for Discord</description>` |
| 8  | `[MANUAL]` | Element template plugin config updated | Confirm `pom.xml` plugin references `io.camunda.connector.discord.DiscordConnector` and template ID `io.camunda.connector.discord.v1` |

### 1.7 — Commit & Push

```bash
git add -A
git commit -m "sprint-1: scaffold Discord connector — rename packages, update Maven coords, create skeleton structure"
git push
```

---

## Sprint 2 — Discord API Client & Authentication

Build the HTTP transport layer that all operations will share.

### 2.1 — Create `DiscordAuthentication` Model

- Create `io.camunda.connector.discord.auth.DiscordAuthentication` record
- Two fields: `botToken` (String, secret) and `webhookUrl` (String, secret)
- Add `@TemplateProperty` annotations with `group = "authentication"` and FEEL support
- Add Jakarta validation (`@NotEmpty` where appropriate)
- Override `toString()` to redact sensitive values

### 2.2 — Create `DiscordApiClient` (Core HTTP Logic)

- Create `io.camunda.connector.discord.client.DiscordApiClient`
- Use `java.net.http.HttpClient` for HTTP calls
- Set required headers:
  - `User-Agent: DiscordBot (https://github.com/camunda/camunda-8-connector-discord, 1.0)`
  - `Content-Type: application/json`
- Implement generic methods:
  - `sendBotRequest(String method, String endpoint, String body, String botToken)` → `HttpResponse<String>`
  - `sendWebhookRequest(String webhookUrl, String body)` → `HttpResponse<String>`
- Use Jackson `ObjectMapper` for JSON serialization/deserialization

### 2.3 — Implement Error Handling Utilities

- Create a helper method `handleResponse(HttpResponse<String> response)` that:
  - On `2xx` → return parsed JSON body
  - On `401/403` → throw `ConnectorException("AUTHENTICATION_FAILED", ...)`
  - On `404` → throw `ConnectorException("NOT_FOUND", ...)` (caller maps to specific code)
  - On `429` → throw retryable exception via `ConnectorRetryExceptionBuilder`, extracting `retry_after` from Discord response body
  - On other `4xx/5xx` → throw `ConnectorException("DISCORD_API_ERROR", ...)`
- Extract Discord error message from JSON response body for better error messages

### 2.4 — Unit Tests for `DiscordApiClient`

- Mock `HttpClient` responses
- Test successful request handling
- Test each error branch (401, 403, 404, 429, 500)
- Test that 429 responses correctly populate retry duration
- Test that User-Agent and Content-Type headers are set correctly

### 2.5 — Verification Checklist

| #  | Type     | Check | How to verify |
|----|----------|-------|---------------|
| 1  | `[AUTO]`   | Project compiles with new classes | `mvn clean compile` exits with `BUILD SUCCESS` |
| 2  | `[AUTO]`   | All unit tests pass | `mvn clean test` exits with `BUILD SUCCESS` |
| 3  | `[AUTO]`   | Client test class exists | `find src/test/ -name "DiscordApiClientTest.java"` returns a result |
| 4  | `[MANUAL]` | `DiscordApiClient.java` exists at expected path | Open `src/main/java/io/camunda/connector/discord/client/DiscordApiClient.java` |
| 5  | `[MANUAL]` | Client uses `java.net.http.HttpClient` | Verify import in `DiscordApiClient.java` — no third-party HTTP lib |
| 6  | `[MANUAL]` | `User-Agent` header is set correctly | Check that requests include `DiscordBot (...)` user agent string |
| 7  | `[MANUAL]` | Error handling covers 401, 403, 404, 429, 5xx | Review `handleResponse` method branches |
| 8  | `[MANUAL]` | 429 handling extracts `retry_after` and uses `ConnectorRetryExceptionBuilder` | Review the 429 branch and corresponding test |
| 9  | `[MANUAL]` | `DiscordAuthentication.toString()` redacts secrets | Verify the output doesn't print token/URL values |

### 2.6 — Commit & Push

```bash
git add -A
git commit -m "sprint-2: add DiscordApiClient, authentication model, and error handling with tests"
git push
```

---

## Sprint 3 — Operation: Send Message (Bot Token)

Implement the first and most important operation end-to-end.

### 3.1 — Create `SendMessageRequest` Record

- Fields: `channelId` (@NotEmpty), `content` (optional), `embeds` (Object, optional), `botToken` (@NotEmpty, secret)
- Add `@TemplateProperty` annotations with appropriate groups, labels, descriptions
- Add custom validation: at least one of `content` or `embeds` must be provided

### 3.2 — Create `MessageResponse` Record

- Fields: `messageId` (String), `channelId` (String), `timestamp` (String)

### 3.3 — Implement `sendMessage` Operation

- Add `@Operation(id = "sendMessage", name = "Send message")` method to `DiscordConnector`
- Build JSON payload with `content` and/or `embeds`
- Call `DiscordApiClient.sendBotRequest("POST", "/channels/{channelId}/messages", ...)`
- Parse response into `MessageResponse`
- Map 404 → `CHANNEL_NOT_FOUND` error code

### 3.4 — Unit Tests for `sendMessage`

- Test successful message send (content only, embeds only, both)
- Test validation: missing channelId, missing botToken, missing both content and embeds
- Test error scenarios: CHANNEL_NOT_FOUND, AUTHENTICATION_FAILED, DISCORD_API_ERROR
- Test rate limiting (429) produces retryable exception

### 3.5 — Verification Checklist

| #  | Type     | Check | How to verify |
|----|----------|-------|---------------|
| 1  | `[AUTO]`   | All tests pass | `mvn clean test` exits with `BUILD SUCCESS` |
| 2  | `[AUTO]`   | sendMessage test class exists | `find src/test/ -path "*discord*" -name "*SendMessage*Test.java"` returns a result |
| 3  | `[AUTO]`   | DiscordConnector has sendMessage operation | `grep -n "sendMessage" src/main/java/io/camunda/connector/discord/DiscordConnector.java` shows `@Operation` annotation |
| 4  | `[MANUAL]` | `SendMessageRequest` has `@NotEmpty` on `channelId` and `botToken` | Open record and verify annotations |
| 5  | `[MANUAL]` | Custom validation rejects requests missing both `content` and `embeds` | Review the validation logic or the test that covers this case |
| 6  | `[MANUAL]` | `MessageResponse` has fields: `messageId`, `channelId`, `timestamp` | Open record and verify |
| 7  | `[MANUAL]` | Operation maps Discord 404 → `CHANNEL_NOT_FOUND` error code | Review error mapping in the operation method |
| 8  | `[MANUAL]` | Tests cover: success, validation failure, 404, 401, 429 | Review test class for these scenarios |

### 3.6 — Commit & Push

```bash
git add -A
git commit -m "sprint-3: implement sendMessage operation (bot token) with request/response models and tests"
git push
```

---

## Sprint 4 — Operation: Send Webhook Message

### 4.1 — Create `SendWebhookMessageRequest` Record

- Fields: `webhookUrl` (@NotEmpty, secret), `content` (optional), `username` (optional), `avatarUrl` (optional), `embeds` (Object, optional)
- Add `@TemplateProperty` annotations
- Validate: at least one of `content` or `embeds`

### 4.2 — Implement `sendWebhookMessage` Operation

- Add `@Operation(id = "sendWebhookMessage", name = "Send webhook message")` method
- Build JSON payload including optional `username` and `avatar_url` fields
- Call `DiscordApiClient.sendWebhookRequest(webhookUrl, body)`
- Return a simple response record with `success = true`
- Map errors: `INVALID_WEBHOOK_URL` for 404, `DISCORD_API_ERROR` for others

### 4.3 — Unit Tests for `sendWebhookMessage`

- Test successful webhook message (with/without optional fields)
- Test validation: missing webhookUrl, missing content and embeds
- Test error scenarios: INVALID_WEBHOOK_URL, DISCORD_API_ERROR

### 4.4 — Verification Checklist

| #  | Type     | Check | How to verify |
|----|----------|-------|---------------|
| 1  | `[AUTO]`   | All tests pass | `mvn clean test` exits with `BUILD SUCCESS` |
| 2  | `[AUTO]`   | Webhook test class exists | `find src/test/ -path "*discord*" -name "*Webhook*Test.java"` returns a result |
| 3  | `[AUTO]`   | DiscordConnector has sendWebhookMessage operation | `grep -n "sendWebhookMessage" src/main/java/io/camunda/connector/discord/DiscordConnector.java` shows `@Operation` annotation |
| 4  | `[AUTO]`   | Two operations exist so far | `grep -c "@Operation" src/main/java/io/camunda/connector/discord/DiscordConnector.java` outputs `2` |
| 5  | `[MANUAL]` | `SendWebhookMessageRequest` has `@NotEmpty` on `webhookUrl` | Open record and verify |
| 6  | `[MANUAL]` | Payload includes optional `username` and `avatar_url` when provided | Review JSON-building logic |
| 7  | `[MANUAL]` | Operation maps 404 → `INVALID_WEBHOOK_URL` | Review error mapping |
| 8  | `[MANUAL]` | Webhook requests do NOT send an `Authorization` header | Review `sendWebhookRequest` in `DiscordApiClient` |

### 4.5 — Commit & Push

```bash
git add -A
git commit -m "sprint-4: implement sendWebhookMessage operation with tests"
git push
```

---

## Sprint 5 — Operation: Create Channel

### 5.1 — Create `CreateChannelRequest` Record

- Fields: `guildId` (@NotEmpty), `channelName` (@NotEmpty), `channelType` (Dropdown: Text=0, Voice=2, Announcement=5, default Text), `topic` (optional), `botToken` (@NotEmpty, secret)
- Add `@TemplateProperty` annotations with dropdown configuration for channel type

### 5.2 — Create `ChannelResponse` Record

- Fields: `channelId` (String), `channelName` (String), `channelType` (int)

### 5.3 — Implement `createChannel` Operation

- Add `@Operation(id = "createChannel", name = "Create channel")` method
- Build JSON payload: `name`, `type`, `topic`
- Call `DiscordApiClient.sendBotRequest("POST", "/guilds/{guildId}/channels", ...)`
- Parse response into `ChannelResponse`
- Map errors: `GUILD_NOT_FOUND` for 404, `MISSING_PERMISSIONS` for 403

### 5.4 — Unit Tests for `createChannel`

- Test successful channel creation for each type (Text, Voice, Announcement)
- Test validation: missing guildId, missing channelName, missing botToken
- Test error scenarios: GUILD_NOT_FOUND, MISSING_PERMISSIONS, AUTHENTICATION_FAILED

### 5.5 — Verification Checklist

| #  | Type     | Check | How to verify |
|----|----------|-------|---------------|
| 1  | `[AUTO]`   | All tests pass | `mvn clean test` exits with `BUILD SUCCESS` |
| 2  | `[AUTO]`   | Channel test class exists | `find src/test/ -path "*discord*" -name "*CreateChannel*Test.java" -o -name "*Channel*Test.java"` returns a result |
| 3  | `[AUTO]`   | Three operations exist | `grep -c "@Operation" src/main/java/io/camunda/connector/discord/DiscordConnector.java` outputs `3` |
| 4  | `[MANUAL]` | `CreateChannelRequest` has a dropdown field for `channelType` | Open record and verify `@TemplateProperty` with dropdown choices (Text, Voice, Announcement) |
| 5  | `[MANUAL]` | `ChannelResponse` has fields: `channelId`, `channelName`, `channelType` | Open record and verify |
| 6  | `[MANUAL]` | Operation maps 404 → `GUILD_NOT_FOUND`, 403 → `MISSING_PERMISSIONS` | Review error mapping in the operation method |
| 7  | `[MANUAL]` | JSON payload uses Discord field names (`name`, `type`, `topic`) | Review payload-building logic |

### 5.6 — Commit & Push

```bash
git add -A
git commit -m "sprint-5: implement createChannel operation with dropdown types and tests"
git push
```

---

## Sprint 6 — Operation: Manage Roles

### 6.1 — Create `ManageRolesRequest` Record

- Fields: `guildId` (@NotEmpty), `userId` (@NotEmpty), `roleId` (@NotEmpty), `action` (Dropdown: Add/Remove), `botToken` (@NotEmpty, secret)
- Add `@TemplateProperty` annotations with dropdown configuration for action

### 6.2 — Create `RoleResponse` Record

- Fields: `success` (boolean), `action` (String), `userId` (String), `roleId` (String)

### 6.3 — Implement `manageRoles` Operation

- Add `@Operation(id = "manageRoles", name = "Manage roles")` method
- Determine HTTP method from action: Add → `PUT`, Remove → `DELETE`
- Call `DiscordApiClient.sendBotRequest(method, "/guilds/{guildId}/members/{userId}/roles/{roleId}", ...)`
- Return `RoleResponse(true, action, userId, roleId)`
- Map errors: `MEMBER_NOT_FOUND` / `ROLE_NOT_FOUND` for 404, `MISSING_PERMISSIONS` for 403

### 6.4 — Unit Tests for `manageRoles`

- Test successful role add and remove
- Test validation: missing guildId, userId, roleId, botToken
- Test error scenarios: MEMBER_NOT_FOUND, ROLE_NOT_FOUND, AUTHENTICATION_FAILED

### 6.5 — Verification Checklist

| #  | Type     | Check | How to verify |
|----|----------|-------|---------------|
| 1  | `[AUTO]`   | All tests pass | `mvn clean test` exits with `BUILD SUCCESS` |
| 2  | `[AUTO]`   | Roles test class exists | `find src/test/ -path "*discord*" -name "*ManageRoles*Test.java" -o -name "*Role*Test.java"` returns a result |
| 3  | `[AUTO]`   | Four operations exist | `grep -c "@Operation" src/main/java/io/camunda/connector/discord/DiscordConnector.java` outputs `4` |
| 4  | `[AUTO]`   | All four request records exist | `find src/main/ -path "*discord*" -name "*Request.java" \| wc -l` outputs `4` |
| 5  | `[MANUAL]` | `ManageRolesRequest` has a dropdown for `action` (Add/Remove) | Open record and verify `@TemplateProperty` with dropdown choices |
| 6  | `[MANUAL]` | Add action uses `PUT`, Remove action uses `DELETE` | Review HTTP method selection logic |
| 7  | `[MANUAL]` | `RoleResponse` has fields: `success`, `action`, `userId`, `roleId` | Open record and verify |
| 8  | `[MANUAL]` | Endpoint is correctly built: `/guilds/{guildId}/members/{userId}/roles/{roleId}` | Review URL construction |

### 6.6 — Commit & Push

```bash
git add -A
git commit -m "sprint-6: implement manageRoles operation (add/remove) with tests"
git push
```

---

## Sprint 7 — Integration, Polish & Documentation

### 7.1 — Build & Element Template Generation

- Run `mvn clean package` — must pass all tests
- Verify the auto-generated element template JSON in `element-templates/`
- Inspect the template: check that all operations, property groups, dropdowns, and secrets are correctly generated

### 7.2 — Add Discord Icon

- Replace `icon.svg` with a Discord-themed connector icon
- Ensure `@ElementTemplate(icon = "icon.svg")` references it correctly

### 7.3 — Update README.md

- Rewrite README with:
  - Project description
  - Supported operations and their inputs/outputs
  - Configuration instructions (bot token, webhook URL)
  - Build instructions (`mvn clean package`)
  - How to deploy the connector to Camunda 8 (Self-Managed & SaaS)
  - Link to auto-generated element template

### 7.4 — Final Review & Cleanup

- Review all code for consistency (naming, Javadoc, logging)
- Ensure all secrets are redacted in `toString()` implementations
- Verify SLF4J logging at appropriate levels (INFO for operations, DEBUG for HTTP details)
- Run full build one last time: `mvn clean verify`
- Verify no compiler warnings or deprecation notices

### 7.5 — Verification Checklist

| #  | Type     | Check | How to verify |
|----|----------|-------|---------------|
| 1  | `[AUTO]`   | Full build passes | `mvn clean verify` exits with `BUILD SUCCESS` |
| 2  | `[AUTO]`   | Element template generated | `find element-templates/ -name "*.json" \| head -1` returns a file |
| 3  | `[AUTO]`   | No old package references remain | `grep -r "io.camunda.example" src/ pom.xml` returns no results |
| 4  | `[AUTO]`   | No compiler warnings | `mvn clean compile 2>&1 \| grep -i "warning"` returns no results (or only safe warnings) |
| 5  | `[AUTO]`   | All expected source files present | `find src/main/java -name "*.java" \| wc -l` outputs expected count (≥ 9: connector + 4 requests + 3 responses + client) |
| 6  | `[MANUAL]` | Element template JSON lists all 4 operations | Open generated JSON, verify `sendMessage`, `sendWebhookMessage`, `createChannel`, `manageRoles` |
| 7  | `[MANUAL]` | Element template has correct dropdown options | Verify channel type and role action dropdowns in the JSON |
| 8  | `[MANUAL]` | `icon.svg` is a Discord-themed icon | Open the file visually |
| 9  | `[MANUAL]` | README documents all 4 operations with inputs/outputs | Read through the README |
| 10 | `[MANUAL]` | Secrets (`botToken`, `webhookUrl`) are never logged in plain text | Search code for `LOG` calls — none should print secrets |
| 11 | `[MANUAL]` | All public methods have Javadoc | Spot-check `DiscordConnector`, `DiscordApiClient` |

### 7.6 — Commit & Push

```bash
git add -A
git commit -m "sprint-7: final polish — element template, icon, README, cleanup"
git push
```

---

## Summary

| Sprint | Focus                        | Key Deliverable                                  | Verification | Commit message |
| ------ | ---------------------------- | ------------------------------------------------ | ------------ | -------------- |
| 1      | Scaffolding & Renaming       | Clean project structure, green build             | 4 auto / 4 manual checks | `sprint-1: scaffold Discord connector...` |
| 2      | API Client & Auth            | Reusable HTTP client with error handling          | 3 auto / 6 manual checks | `sprint-2: add DiscordApiClient...` |
| 3      | Send Message (Bot)           | First operation end-to-end with tests             | 3 auto / 5 manual checks | `sprint-3: implement sendMessage...` |
| 4      | Send Webhook Message         | Webhook-based messaging with tests                | 4 auto / 4 manual checks | `sprint-4: implement sendWebhookMessage...` |
| 5      | Create Channel               | Channel creation with dropdown types & tests      | 3 auto / 4 manual checks | `sprint-5: implement createChannel...` |
| 6      | Manage Roles                 | Role add/remove with tests                        | 4 auto / 4 manual checks | `sprint-6: implement manageRoles...` |
| 7      | Integration, Polish & Docs   | Element template, icon, README, final QA          | 5 auto / 6 manual checks | `sprint-7: final polish...` |
