# 0014. The template context is encrypted while queued

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-064
- Document status: Approved, last checked against the code on 2026-11-02

## Context

The context map a host passes to `send()` fills the template placeholders, and it often carries OTPs and personal data. Templates are rendered only at delivery time (ADR 0016), so the context sits in Beacon's tables, or in a broker, until then. It must never sit there in plain text, and Beacon should notice if someone tampers with it.

## Decision

- A `ContextEncryptor` interface with an `AesGcmContextEncryptor` implementation:
  - AES-256-GCM, with a fresh random 12-byte IV for every call;
  - the context is serialised as JSON before encryption;
  - the output is `Base64(IV + ciphertext)`;
  - a null context encrypts to null.
- The context is encrypted once per notification and decrypted by the worker just before rendering. Under ADR 0010 it is encrypted in `send()` and stored encrypted in the request row; the expand step copies the ciphertext into each channel task.

## Consequences

- OTPs and personal data are never stored in plain text in the queue.
- Tampering is detected, because GCM authenticates the ciphertext.
- How the host supplies a key, and how keys are rotated, is open (O-06). The recorded proposal is one required property holding a Base64-encoded 32-byte key, with startup failing when it is missing. Under the current O-06 proposal, every host must supply a key.

## Alternatives rejected

The register records no rejected alternatives.

## In the code

- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/crypto/ContextEncryptor.java`
- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/crypto/AesGcmContextEncryptor.java`
- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/listener/NotificationRequestedEventListener.java` (encrypts once per event today)
- Tests: `AesGcmContextEncryptorTest` (8 tests); `NotificationIngestionTransactionalTest.savedContext_isEncrypted_andDecryptsBack`.
- Not yet true: Jackson exceptions are not converted to `NotificationException`, and no additional authenticated data ties a ciphertext to its row (register G-32).
- Not built yet: the key property (O-06), decryption in the worker, and encryption in `send()` (ADR 0010).
