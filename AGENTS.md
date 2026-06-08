# Beacon — Development Notes

## Item 5: First Sitting — Review & Action Items

### What's Missing in `DefaultDeduplicationStore`

#### 1. `isDuplicate()` is not yet implemented

```java
public boolean isDuplicate(String deduplicationKey) {
    return false;  // <-- always says "no, not a duplicate" — even if it IS
}
```

This should check the map, like `isSeen` does. Right now if anyone calls `isDuplicate()`, they get a lie. In a library, that's dangerous — a host developer might call this method trusting it, and it will silently tell them there are no duplicates. They'll never know their notifications are being sent twice.

#### 2. No input validation

What happens if someone passes `null` to `markSeen()`?

```java
defaultDedupStore.putIfAbsent(null, null);  // <-- ConcurrentHashMap throws NullPointerException
```

The caller gets a raw `NullPointerException` with no explanation. In a library, that's hostile — the host developer sees a stack trace from deep inside your code and has no idea what they did wrong. You should catch this early and throw something clear, like `IllegalArgumentException("idempotencyKey must not be null or blank")`.

Same for blank/empty strings — should `""` be a valid key? Probably not.

#### 3. No memory management

The `ConcurrentHashMap` grows forever. Every key that's ever seen stays in memory permanently. In a real application running for days or weeks, this map fills up and the JVM runs out of memory. A library should handle this — either by setting a max size, or by evicting entries older than some window. This is what the plan calls the **"retention policy."**

#### 4. `acquireDeliveryLock` / `releaseDeliveryLock` are stubs

They return `false` always. That's fine for the first sitting (you don't need delivery locks yet), but these are part of the contract. When a worker later calls `acquireDeliveryLock`, it will always be told "someone else has it" and will never process anything. This is a future sitting concern, but worth knowing.

#### 5. `isDuplicate` vs `isSeen` — two methods that answer the same question

`isDuplicate` and `isSeen` both answer the question "has this key been seen?" You added `isSeen` to the interface. That's fine for now, but notice that `isDuplicate` was the original method in the contract. They should agree. Right now one returns `false` always and the other actually checks the map.

---

### Additional Test Cases (Edge Cases for Production)

These are the edge cases that could bite in production and hurt users of host applications.

#### Validation Suite — Additions

- **Null idempotency key (not blank, `null`).** Your `@NotBlank` annotation also rejects `null` — but you should prove it with a test. Blank and `null` are different inputs that hit different code paths inside the validator.

- **Whitespace-only idempotency key.** A key that's `"   "` — all spaces. `@NotBlank` should reject this too, but if it doesn't, it becomes a valid key that's invisible. A host developer would be confused.

- **Multiple violations at once.** Send a request with blank key AND blank userId AND null type — all three broken. Your service should still throw `ConstraintViolationException`, and the exception should contain all three violations, not just the first one it found.

- **Validation runs before dedup.** If you send a bad request, `markSeen` should never be called. If it was, the bad request would "burn" the idempotency key — the host would fix their request and retry, but now the key is used up and they'd get `DuplicateNotificationException`. Prove that validation stops everything before dedup runs.

#### Dedup Suite — Additions

- **Two different keys both succeed independently.** Send with key A, then send with key B. Both should succeed. Both should publish events. This proves keys don't interfere with each other.

- **Null idempotency key reaches `markSeen`.** What if somehow a null key gets past validation? (Maybe in a future version someone removes the `@NotBlank` annotation.) Does your dedup store blow up with a raw `NullPointerException`? Or does it give a clear error? This is defensive depth — test what the store does with bad input even if the service layer should prevent it.

- **Event publisher throws after `markSeen` succeeds.** This is the subtle one. Imagine `markSeen` succeeds (key is now "used"), but then `publishEvent` throws an exception (Spring misconfiguration, out of memory, whatever). The key is burned — it's marked as seen — but no event was published. The host retries with the same key and gets `DuplicateNotificationException`. The notification is lost forever. This is a real production edge case. You should at minimum prove what happens — even if you don't fix it now, the test documents the behavior.

- **`markSeen` is not called when validation fails.** This is the flip side of the validation test above, but verified from the dedup store's perspective. After a failed validation, `isSeen` should return `false` for that key.

#### Acceptance Response Suite — Currently Empty

This needs several tests:

- **Response has a non-null notification ID.** The host needs this ID to track their notification later.

- **Response status is `CREATED`.** Not `null`, not `QUEUED`, not `DELIVERED` — specifically `CREATED`. That's the early-lifecycle status the plan calls for.

- **Response echoes back the idempotency key.** The host can correlate the response to their request.

- **`acceptedAt` matches the clock, not wall time.** Since you use a fixed clock in tests, `acceptedAt` should be exactly `2026-07-25T15:00:00Z`. This proves you used the injected clock, not `Instant.now()`.

- **Same request produces the same notification ID.** Because your `uuidFrom` method is deterministic (same key + same instant = same UUID), sending the same request should always produce the same ID. This matters when hosts retry and want to match responses.

- **Different requests produce different notification IDs.** Two different idempotency keys should produce two different notification IDs. If they didn't, you'd have two different notifications with the same ID, and status lookups would return the wrong data.

#### Separate Test Class: `DefaultDeduplicationStoreTest`

The dedup store is a building block that the service depends on. It deserves its own tests, separate from the service tests. The service tests prove the service uses the store correctly. The store tests prove the store itself works correctly. Two different things.

- First `markSeen` -> no exception.
- Second `markSeen` with same key -> `DuplicateNotificationException`.
- `isSeen` before `markSeen` -> `false`.
- `isSeen` after `markSeen` -> `true`.
- `isDuplicate` before `markSeen` -> `false`. _(Will fail right now because `isDuplicate` is not yet implemented.)_
- `isDuplicate` after `markSeen` -> `true`. _(Same — will fail.)_
- Null key -> clear error, not raw `NullPointerException`.
- Blank key -> clear error.
- Concurrent `markSeen` with same key -> exactly one wins. Same `CountDownLatch` pattern, but testing the store directly, not through the service. This proves the store is safe independent of whatever wraps it.
- Different keys don't interfere. Mark key A, check key B — B is not seen.

---

### Summary of What To Do

#### Fix `DefaultDeduplicationStore` first:

1. Implement `isDuplicate()` — make it actually check the map
2. Add null/blank validation to `markSeen()`, `isDuplicate()`, and `isSeen()`
3. (Later) add memory management / size bounds

#### Then write the tests:

1. Fix the `verify(never())` bug in dedup test 2 — should be `verify(eventPublisher, times(1))`
2. Fix the `DuplicateNotificationException` constructor call — pass just the raw key, not a formatted message
3. Add the new validation edge case tests
4. Add the new dedup edge case tests
5. Fill in the acceptance response tests
6. Create a separate `DefaultDeduplicationStoreTest` class
                                                                   



---



## What is a retention policy?

Your `DefaultDeduplicationStore` remembers every idempotency key that was ever sent. It puts them in a ConcurrentHashMap and never removes them.

Think of it like a guest book at a hotel front desk. Every guest who checks in signs their name. The book gets thicker and thicker. After a year,
you have thousands of pages. After five years, you need a new desk to hold all the books. But you only actually care about recent guests — you're
checking the book to see if someone already checked in today, not five years ago.

That's the problem. The map grows forever, and old entries serve no purpose.

A retention policy is your rule for when to throw away old entries. It's the answer to the question: "How long do we need to remember a key before
it's safe to forget it?"
                                                                                                                                                    
---                                                    
### The decisions we need to make

**Decision 1 — How long should we remember a key?**

This depends on how the host application works. Ask yourself: how long could a duplicate trigger realistically arrive after the original?

- If a payment service retries a failed webhook, it usually retries within minutes to hours.
- If a batch job runs daily and accidentally re-processes old records, duplicates could arrive days later.
- If a system replays events from a message queue after a crash, it might replay things from weeks ago.

For most applications, 24 hours to 7 days is enough. After that, a duplicate is so unlikely that it's not worth the memory cost to guard against  
it.

But here's the thing — this is an in-memory store. It lives inside the JVM. If the application restarts, the whole map is gone anyway. So the     
retention window really only matters for how long the app runs without restarting. For the JDBC store (database-backed), retention matters more
because the data survives restarts.

For the in-memory DefaultDeduplicationStore, the practical concern is: don't let the map eat all the memory while the app is running.

**Decision 2 — How do we enforce the limit?**

Two common approaches:

**Option A** — Time-based expiry. Each entry gets a timestamp. A background task periodically scans the map and removes entries older than the        
retention window (e.g., older than 1 hour). This is like the hotel tearing out guest book pages older than a week.

**Option B** — Size-based cap. The map has a maximum number of entries (e.g., 10,000). When it's full and a new key arrives, the oldest entry gets    
evicted. This is like the hotel saying "this book only holds 500 names — when it's full, we start a new one and throw the old one away."

**Option C** — Both. Cap the size AND expire by time. Belt and suspenders.

For an in-memory store, Option A (time-based) is the most natural. The host application should be able to configure the window — maybe they want 1
hour, maybe they want 24 hours. We pick a sensible default and let them change it.

**Decision 3 — Who triggers the cleanup?**

Three ways to clean up expired entries:

Lazy cleanup. Every time markSeen or isSeen is called, check if that specific entry is expired. Only clean the one you're looking at. Simple, but
old entries that nobody asks about stay forever.

Scheduled cleanup. A background thread runs every N minutes and sweeps the whole map, removing everything past the retention window. Thorough, but
you need a thread.

On-write cleanup. Every time a new key is added, also scan for and remove a few expired entries. A middle ground — no background thread, but old  
entries get cleaned gradually.

For a library, scheduled cleanup is the most reliable. You know the map stays clean. But it adds complexity — now your store manages a thread, and
the host has to worry about shutting it down properly.

Lazy cleanup is the simplest and often good enough for an in-memory store. The old entries just sit there harmlessly until someone happens to     
check them, at which point you say "this is expired, treat it as unseen."

Decision 4 — Should the retention window be configurable?

Yes. Different host applications have different needs. A banking app might want 7 days. A marketing notification system might be fine with 1 hour.
Make the window a constructor parameter with a sensible default.
                                                                                                                                                    
---                                                    
What I'd recommend for DefaultDeduplicationStore

Since this is the in-memory implementation (not the database one):

1. Store a timestamp with each key — instead of ConcurrentHashMap<String, String>, use ConcurrentHashMap<String, Instant> where the value is when
   the key was marked seen.
2. Accept a retention duration in the constructor — something like Duration ttl, with a default of 1 hour.
3. Use lazy cleanup — when checking isSeen or isDuplicate, also check if the entry is older than the TTL. If it is, treat it as not seen (and     
   remove it). When markSeen is called, do a quick sweep of a few old entries to keep the map from growing without bound.
4. Accept a max size as a safety net — even with lazy cleanup, if someone sends thousands of unique keys per second, the map could grow fast. A   
   hard cap (say 100,000 entries) prevents disaster.

  ---                                                                                                                                               
What this means for the JDBC store later

The database-backed JdbcDeduplicationStore has the same question but a different answer. There, the retention policy is:

- A created_at column (you already have this in your V2 migration).
- A scheduled job (cron or Spring @Scheduled) that runs DELETE FROM deduplication_store WHERE created_at < now() - interval '30 days'.
- The database handles the storage — no memory concern — so the window can be much longer.

But that's a future sitting concern. For now, we're talking about the in-memory store.
                                                                                                                                                    
---                                                                                                                                               
What do you think?

The key choices are:
- TTL duration: how long to remember? (I'd default to 1 hour for in-memory)
- Cleanup strategy: lazy, scheduled, or on-write? (I'd lean lazy for simplicity)
- Max size cap: safety net number? (Something like 100,000)
- Configurable via constructor: yes or no? (I'd say yes)

What feels right to you?