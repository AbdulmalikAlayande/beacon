# 0016. Beacon's own simple template engine, rendering at the worker

- Status: Accepted
- Date: 2026-11-02
- Register entries: D-080, D-081
- Document status: Approved, last checked against the code on 2026-11-02

## Context

Notifications are short texts with a few values filled in: a name, an amount, an OTP. A missing optional value must never stop an OTP from being sent. A library also should not bring a heavy template engine into every host. And rendered bodies, especially HTML email, multiplied across a large batch would bloat the queue.

## Decision

- Beacon has its own simple engine, `DefaultTemplateEngine`: a single regex pass over `${key}` placeholders using `appendReplacement` and `appendTail`.
  - Keys are flat strings. Dots and dashes are part of the key; there is no property navigation.
  - Keys are trimmed, so `${ name }` resolves `name`.
  - A missing key and a null value are treated the same: the placeholder is left as written and a warning is logged. Rendering never throws for this.
  - Values are escaped with `quoteReplacement`, so `$` and `\` in values are literal, and values are never expanded again.
  - Subject (email), title (push) and body are rendered separately.
  - The destination is chosen by channel: the phone number for SMS, the email address for EMAIL, and the push token for PUSH.
- Rendering happens at the worker, just before sending, not at enqueue. Only the encrypted context is queued.
- `AdvancedTemplateEngine` is deleted.

## Consequences

- A missing optional field cannot break an OTP.
- No template library is added to the host.
- The queue stays small, because it holds only the encrypted context (ADR 0014).
- There are no loops or conditions in templates.
- `TemplateResolver` is the planned way for a host to supply its own templates (Planned); nothing uses it yet.
- Still open: a null channel throws a raw `NullPointerException` (changing it to `NotificationException` was considered, not decided).

## Alternatives rejected

- **Thymeleaf.** Rejected as too heavy for a library.
- **A lexer, parser and syntax-tree engine with loops and conditions.** Rejected as more than notifications need.
- **Rendering before queuing.** Rejected because rendered bodies would bloat the queue (ADR 0027).

## In the code

- `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/template/DefaultTemplateEngine.java`
- `beacon-api/src/main/java/io/github/abdulmalikalayande/beacon/api/port/TemplateEngine.java`
- Tests: `DefaultTemplateEngineTest` (25 tests).
- Not yet true: `beacon-core/src/main/java/io/github/abdulmalikalayande/beacon/core/template/AdvancedTemplateEngine.java` still exists as an empty stub (register G-13).
- Not built yet: the worker that calls the engine.
