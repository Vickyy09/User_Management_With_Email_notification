# Upgrade an existing database

This guide applies only to the original project, before password_hash, version, auth_tokens, and email_notifications were introduced. Stop the old application and back up the database. Review these statements against your schema; the application does not execute this guide.

The old API accepted arbitrary role strings. Treat all old roles as untrusted. First inspect email normalization collisions:

```sql
SELECT LOWER(TRIM(email)) AS normalized_email, COUNT(*) AS total
FROM users
GROUP BY LOWER(TRIM(email))
HAVING COUNT(*) > 1;
```

Resolve duplicate, blank, or invalid emails and overlength profile fields before normalizing. Preserve records through a reviewed migration rather than deleting data to bypass constraints.

On the original schema, run a reviewed one-time migration:

```sql
UPDATE users SET role = 'USER';
UPDATE users SET email = LOWER(TRIM(email));
ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(100) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
```

Do not run ADD COLUMN statements if the columns already exist. If a previous startup added version with NULL values, fill those with 0 before updates. Account names are limited to 80 characters, email to 254, phone to 30, and department to 100; review longer legacy values before Hibernate changes column lengths.

Start the upgraded application with the reviewed development schema-update setting to add the auth_tokens and email_notifications tables. For production, create a versioned schema migration and use DDL_AUTO=validate.

Provision a new administrator through the documented bootstrap variables with an unused email. Existing accounts have no password hashes and cannot log in. Provision those only through an identity-verified administrative migration using the application's BCrypt encoder. Never put plaintext passwords in password_hash, assign a shared default password, or trust an old ADMIN string. A self-service password reset flow is not included.

Existing active flags and timestamps are retained. The new DELETE operation deactivates accounts; it cannot restore records that the old version already physically deleted.