I couldn't open your current `DeliveryTask.java` or `NotificationQueue.java`; they aren't in the files available to me here. What I can see is the original Phase 1 design of both, plus your current `DeliveryTaskEntity`, listener and migration. So the comparison below is against the design. Paste the two current files and I'll confirm each point against what's actually there.

## What each one is

**`DeliveryTask`** lives in `beacon-api/dto`. It's a plain Java record with no JPA and no Spring, describing one job: "send this notification to this user on this channel." It's the object the `NotificationQueue` port speaks in, so it has to make sense to every queue: the database one today, and RabbitMQ or Kafka later. A Kafka message has no table row, so the shared language can't be a JPA entity.

**`DeliveryTaskEntity`** lives in `beacon-core/entity`. It's one row of the `notification_queue` table, and only the database queue uses it. It carries things that only make sense for a table, such as `status` (QUEUED or PROCESSING, which the poller needs) and `updated_at` (which stuck-row recovery needs).

So the intended relationship is: the rest of the library speaks `DeliveryTask`; `DatabaseNotificationQueue` converts it into a `DeliveryTaskEntity` on the way in, and back into a `DeliveryTask` on the way out. That conversion is the whole reason two classes exist. Your boss named the entity after the DTO precisely so that one maps to the other.

## Where it has gone wrong

**1. The DTO is skipped entirely, and this is the main mistake.** Your listener builds `DeliveryTaskEntity` objects directly through `createQueued(event, ...)` and saves them with the repository. `DeliveryTask` never appears anywhere in the write path. That's why I said earlier the listener should save through the queue: the real fix is that the listener builds `DeliveryTask` objects and calls `queue.enqueue(...)`, and only `DatabaseNotificationQueue` ever touches the entity. Right now `DeliveryTask` exists on paper and nowhere in practice.

**2. The time field means different things.** The original design gave `DeliveryTask` a `scheduledAt` (when the host wants it sent). The table has `available_at` (when it may be picked up, which also covers quiet hours and retry backoff). Those aren't the same: a retried message has no new schedule, but it does have a new available time. The queue only cares about "when may this be picked up," so the DTO should carry `availableAt`. If it carries `scheduledAt`, the conversion has to guess, and quiet-hours and retry delays get lost when the task travels through a broker.

**3. The ID types don't match.** The original DTO had `taskId` and `notificationId` as `String`; the entity and the rest of the code use `UUID`. Every conversion then has to parse and format, and a malformed string fails at runtime instead of compile time. Pick one and use it on both sides. `UUID` is plain Java, so it's fine in `beacon-api`.

**4. Who creates the row ID is unclear.** The entity now uses `@GeneratedValue`, so the database layer invents the ID on save. But a task going to Kafka also needs an ID, and no database is involved there. The cleaner rule is that the ID is created when the `DeliveryTask` is created (`UUID.randomUUID()`), and the entity simply stores it with a plain `@Id` and no `@GeneratedValue`. Setting an ID by hand on a `@GeneratedValue` entity also makes Hibernate treat it as an existing row, which triggers an extra select before every insert.

**5. The port has no way to take tasks out, and it shouldn't get one.** In the original design, `NotificationQueue` only has `enqueue`, `enqueueDelayed` and `enqueueDead`. That's actually correct, because "claim a batch" is a database idea: Kafka pushes messages, it doesn't let you claim them. So the claim method belongs on `DatabaseNotificationQueue` itself, not on the port. What every queue needs in common on the way out is something to hand tasks to, a small handler interface that the worker (item 8) implements. The database queue's poller calls that handler; a future Kafka listener would call the same handler. This keeps the test list I gave you valid, with one clarification: `claim` and `recover` are methods of the database implementation, not of the port.

**6. `enqueueDelayed` becomes redundant once the DTO carries `availableAt`.** If the task already says when it's available, `enqueue(task)` is enough, and a second method doing the same job invites the two to drift apart. I'd drop it, but that one is a judgment call.

## What is right and should stay

`status` and `updated_at` are not on the DTO in the design, and that's correct; they're queue bookkeeping, and a broker wouldn't have them. Carrying `encryptedContext` on the DTO is also correct, because it means the context is never plain text in any queue, database or broker. And the entity's `equals` and `hashCode` on `id` only are right.

## Order to fix it in

Before writing `DatabaseNotificationQueue`, settle the DTO: `UUID` IDs, `availableAt` instead of `scheduledAt`, and the task ID created by whoever builds the task. Then remove `@GeneratedValue` from the entity. Then `DatabaseNotificationQueue` converts between the two, and finally the listener builds `DeliveryTask` objects and calls the queue. Your existing ingestion tests will tell you if that last switch broke anything.

Send me the current `DeliveryTask.java` and `NotificationQueue.java` and I'll tell you exactly which of these points still apply.