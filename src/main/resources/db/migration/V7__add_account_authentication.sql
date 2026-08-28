ALTER TABLE accounts
ADD COLUMN password_hash VARCHAR(255);

UPDATE accounts
SET password_hash = '$2a$10$gqpDd1Tx7ysUAEjMkeCBwOIJ2WvJZKrJwsb4sUDtpun/iVOALN1ti'
WHERE password_hash IS NULL;

ALTER TABLE accounts
ALTER COLUMN password_hash SET NOT NULL;
