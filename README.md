# User Management with Email Notifications

A Java 21 / Spring Boot 4.0.2 REST API backed by MySQL. It includes Spring Security authentication, BCrypt passwords, USER/ADMIN authorization, validated DTOs, pagination, soft deactivation, and a durable asynchronous email outbox.

## Implemented behavior

- Registration always assigns USER. Profile requests cannot set roles, account status, IDs, timestamps, or password hashes.
- Login returns a random 256-bit opaque bearer token. Only its SHA-256 hash is stored; tokens expire after 60 minutes by default.
- Users can read and update their own profile. Administrators can create, list, read, update, and deactivate users.
- Passwords require at least 12 Unicode characters and at most 72 UTF-8 bytes (BCrypt's limit). Passwords and hashes are never included in API responses, notifications, or request DTO string representations.
- Password changes require the current password and revoke all tokens. Email changes also revoke all tokens. Deactivation preserves the record, revokes tokens, and blocks login.
- Email addresses are normalized to lowercase; application checks and a database unique constraint reject duplicates. Deactivated accounts retain their email reservation.
- Registration, profile updates, and deactivation notify the user and optional HR address in separate messages. Password changes notify only the user.
- Errors use a consistent JSON structure with field errors for invalid request bodies.

## Run locally

Requirements: Java 21 and MySQL. Use the included Maven wrapper or an installed Maven.

Create a fresh development database:

```sql
CREATE DATABASE user_management;
```

In PowerShell:

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/user_management'
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = 'replace-with-your-local-password'
$env:MAIL_HOST = 'localhost'
$env:MAIL_PORT = '1025'
$env:MAIL_FROM = 'no-reply@example.test'
$env:HR_EMAIL = 'hr@example.test'
.\mvnw.cmd spring-boot:run
```

The default email configuration targets a local SMTP capture server on port 1025. Without one, API operations still succeed, while delivery attempts eventually become FAILED. For a real SMTP provider, set MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM, MAIL_SMTP_AUTH=true and MAIL_STARTTLS=true as appropriate. Use an authorized sender address.

See [.env.example](.env.example) for configuration names. It is a reference file: Spring Boot does not automatically load it. Set environment variables in your shell or IntelliJ's run configuration. Use HTTPS when deploying the API.

### Provision the first administrator

Before starting the application, set:

```powershell
$env:BOOTSTRAP_ADMIN_ENABLED = 'true'
$env:BOOTSTRAP_ADMIN_EMAIL = 'admin@example.test'
$env:BOOTSTRAP_ADMIN_PASSWORD = 'replace-with-a-strong-unique-password'
```

Startup creates the administrator only if the email is unused. It never promotes an existing USER or reactivates an inactive account. Existing active administrators are left unchanged; bootstrap is not a password-reset mechanism.

After provisioning, remove the bootstrap password and disable BOOTSTRAP_ADMIN_ENABLED in the run configuration. Log in through the normal login endpoint to obtain an admin token. There is no public role-change endpoint.

### Existing database upgrade

Use a fresh development database for the quickest demo. To retain an old database, back it up and follow [the legacy upgrade guide](docs/legacy-upgrade.md) before the first upgraded startup. Old role strings were client controlled, so they must not be trusted as permissions. Legacy users without password hashes cannot log in.

The development default is DDL_AUTO=update. Review schema changes and use managed migrations with DDL_AUTO=validate before a production deployment.

## API

There are **11 application operations**. The previous /user/* routes have been replaced with /api/* routes.

| Method | Path | Access / result |
| --- | --- | --- |
| POST | /api/auth/register | Public; creates USER, returns 201 |
| POST | /api/auth/login | Public; returns token and profile |
| POST | /api/auth/logout | Authenticated; revokes current token, returns 204 |
| POST | /api/users | ADMIN; creates USER, returns 201 |
| GET | /api/users | ADMIN; paginated list including inactive users |
| GET | /api/users/{id} | Owner or ADMIN |
| PUT | /api/users/{id} | Owner or ADMIN; updates an active profile |
| DELETE | /api/users/{id} | ADMIN; deactivation, returns 204; repeated calls do not resend email |
| GET | /api/users/me | Authenticated profile |
| PUT | /api/users/me | Update authenticated profile |
| PUT | /api/users/me/password | Change password; revoke tokens, return 204 |

List parameters: page=0, size=20 (1-100), sort=id, direction=asc. Allowed sort fields: id, firstName, lastName, email, createdAt. Direction is asc or desc.

Import [the Postman collection](docs/UserManagement.postman_collection.json), or import [the OpenAPI specification](src/main/resources/static/openapi.json) into your API client or Swagger Editor. The running application also serves the specification at /openapi.json. Set collection variables locally; do not export populated credentials or tokens.

Registration body:

```json
{
  "firstName": "Vicky",
  "lastName": "Kumar",
  "email": "vicky@example.test",
  "password": "Replace with a strong password",
  "phoneNumber": "+91 9876543210",
  "department": "Engineering"
}
```

Login with email and password, then send Authorization: Bearer <accessToken>. No browser session, HTTP Basic authentication, or authentication cookies are used. CSRF protection is disabled for this header-only authentication model. Logout revokes the current token; log in again after changing your password or email.

Profile update bodies contain firstName, lastName, email, phoneNumber, and department, with no password. Password changes contain currentPassword and newPassword.

Example validation response:

```json
{
  "timestamp": "2026-10-27T12:00:00Z",
  "status": 400,
  "message": "Validation failed",
  "path": "/api/auth/register",
  "fieldErrors": { "email": "must be a well-formed email address" }
}
```

Responses distinguish 400 (invalid input), 401 (authentication), 403 (permissions), 404 (missing user), and 409 (duplicate email or data conflict). Malformed JSON and unknown request fields are rejected.

## Email delivery and failure behavior

1. A user operation and its email_notifications rows are committed in the same transaction. If either fails, both roll back.
2. Every five seconds a scheduled job calls a separate @Async worker. It processes up to 20 committed, due messages. SMTP runs outside the HTTP request.
3. Each recipient has a separate row. Delivery records status, attempt count, next attempt time, last error category, and sent time.
4. Transient mail failures are retried after 30 and 60 seconds, up to three total attempts. Authentication and message-format failures stop immediately. Connection, read, and write timeouts are each five seconds.
5. Exhausted messages remain FAILED for inspection. After fixing the cause, an operator can explicitly requeue selected failed rows; automatic polling does not retry them indefinitely.

SMTP failure cannot turn an already-saved user operation into an HTTP error. A database failure while writing the outbox does fail and roll back the user operation. Pending messages survive application restarts. Sent status means the SMTP server accepted the message, not that the recipient read or received it in their inbox.

Delivery uses a database row lock during the bounded SMTP attempt to coordinate workers. This small-project design occupies a database connection during SMTP. The executor has one worker and bounded capacity. Delivery is **at least once**: a process crash after SMTP acceptance but before recording SENT can result in a duplicate after restart. This is not an exactly-once delivery guarantee.

Inspect failures without selecting message contents:

```sql
SELECT id, status, attempts, next_attempt_at, last_error
FROM email_notifications
WHERE status = 'FAILED';
```

After investigation, an operator can requeue a specific failed ID:

```sql
UPDATE email_notifications
SET status = 'PENDING', attempts = 0, next_attempt_at = CURRENT_TIMESTAMP, last_error = NULL
WHERE id = 123 AND status = 'FAILED';
```

This is an explicit operational action; it is not automatically performed by the application.

## Tests and build

```powershell
.\mvnw.cmd verify
```

Tests use an isolated H2 database in MySQL compatibility mode, disable scheduled jobs/admin bootstrap, and mock JavaMailSender. They require no MySQL server, SMTP account, or production secrets and send no real email.

The integration suite covers registration, BCrypt storage, validation, duplicate emails, ownership and roles, token hashing/expiry/logout, password changes, deactivation, pagination, admin bootstrap, asynchronous delivery, retry exhaustion/recovery, transaction rollback, commit visibility, and concurrent delivery.

H2 tests do not prove MySQL-specific schema or locking behavior. Verify those against a disposable MySQL environment before deployment. This repository does not claim load-test results, coverage percentages, delivery guarantees beyond those above, email verification, password reset, or brute-force protection.

## Architecture

Controller -> validated request DTO -> transactional service -> JPA repository.
Security filter -> hashed-token lookup -> current account status and role.
Transactional service -> durable outbox -> scheduled async dispatcher -> SMTP.

Configuration contains environment-variable references rather than embedded passwords. Rotate any credentials previously published in Git history; replacing the current file does not remove old commits.

## Resume wording

After running and understanding the project, you can describe it as:

- Built a Spring Boot user-management API with BCrypt password hashing, expiring bearer-token authentication, role and ownership checks, validated DTOs, and consistent error responses.
- Implemented durable asynchronous SMTP notifications with bounded retries, transactional persistence, and integration tests for authorization and delivery failures.

Use measured results for any performance or coverage claims.