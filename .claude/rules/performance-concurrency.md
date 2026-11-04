---
paths:
  - "**/src/main/java/**/*.java"
  - "**/src/main/resources/application*.yml"
  - "**/build.gradle.kts"
---
# Performance and concurrency review rules

Use `review-policy.md` for evidence/severity. Query pagination, N+1 and indexes remain in `persistence.md`;
transaction boundaries remain in `application-layer.md`. Apply these rules only to relevant changed paths.

- **Advisory:** optimize against a demonstrated workload, query count, allocation profile or complexity bound.
  Repeated linear membership checks may merit a Set/Map; small bounded lists may be clearer as written. State data
  size and memory tradeoffs. Do not demand caches, parallelism, indexes or benchmarks for every simple method.
- **Mandatory:** when processing externally sized data, bound memory/work through an approved limit, pagination,
  chunking or streaming, including intermediate buffers and decompressed results. That includes reads that grow with
  user activity, such as a lead's History or a queue of open leads (SV-055, SV-086). A fixed executor size does not
  bound an unbounded queue. Preserve feature-specific limits; new business limits need the proper decision.
- **Mandatory:** for mutable singleton/static state accessed by concurrent requests/jobs, identify ownership and a
  coherent lock/atomic/immutable-publication protocol. `volatile` visibility does not make `count++` atomic;
  ConcurrentHashMap does not make `containsKey` then `put` atomic or make its mutable values thread-safe. Use an
  appropriate atomic operation or common lock for the actual invariant; keep compute callbacks short.
- **Mandatory:** when concurrent commands can violate a database invariant across rows/processes/instances, do not
  rely only on `synchronized`, a local map or an AtomicInteger. Follow the approved constraint/guarded-update/lock/version/lease protocol
  (ORM checks in `persistence.md`). Keep existing media leases/fencing under ADR-011; do not substitute a local lock.
- **Mandatory:** a create-only claim needs one atomic primitive: a unique constraint, `Files.createFile` or
  CREATE_NEW, a hard link, a conditional put. An exists check followed by `Files.move` lets several writers all win
  on Linux (SV-068).
- **Mandatory:** newly introduced asynchronous work must have bounded admission/concurrency, a deliberate executor,
  overload/rejection behavior, failure observation and lifecycle ownership. Shut down owned executors at the right
  lifecycle boundary; never close a shared Spring-managed executor per request. A virtual-thread executor still
  needs admission limits for scarce downstream resources such as JDBC connections and remote calls: use a semaphore,
  not a pool of virtual threads. On Java 21 a virtual thread blocked inside `synchronized` pins its carrier.
- **Mandatory:** a @Scheduled job runs on every instance, and Boot's default scheduler gives all such jobs one thread,
  so a slow job delays the others. This repo has no ShedLock: make the job idempotent or claim its work in the
  database (`FOR UPDATE SKIP LOCKED`, a lease, an advisory lock) and bound each run. Long work gets its own scheduler
  or executor owned by a holder class, as in MediaScheduling: a TaskScheduler or ScheduledExecutorService bean would
  switch off Boot's default scheduler.
- **Mandatory:** for blocking I/O, waits and retry loops, check applicable timeouts/deadlines, cancellation/cleanup
  and bounded retry/backoff. Cancellation of a Future is not proof that I/O stopped. Retries of writes need an
  idempotency/duplicate-effect strategy; do not retry an entire side-effecting operation blindly.
- **Mandatory:** when catching InterruptedException, propagate it or restore interrupt status and exit/cancel as
  the API permits; do not swallow it and keep looping. Close owned streams/resources on all exit paths, including
  `Files.lines/list/walk`. Clean up owned task/request ThreadLocal or MDC state in finally; do not assume context is
  inherited by pooled workers.
- **Mandatory:** when using @Async, CompletableFuture, parallel streams or scheduled work, check which thread and
  transaction actually execute it. Caller transactions do not propagate to new threads; required security/MDC
  context needs explicit supported propagation. Do not share an actual EntityManager/Session or its mutable
  managed state across workers. Pass immutable values/IDs and reload in the worker's intended unit of work.
- **Advisory:** keep streams simple and non-interfering; do not mutate the source or rely on side effects in map/peek
  that may be elided. Choose parallelStream/common-pool work only with a workload and resource/context rationale;
  blocking I/O and shared mutation usually need an explicit design rather than automatic parallelization.
- **Advisory:** when adding a cache, state key scope, capacity/expiry, invalidation and acceptable staleness. Retain
  required freshness/authorization on reads; do not introduce a cache merely to hide an unmeasured slow query.

References: Oracle Java 21 [ConcurrentHashMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html),
[ExecutorService](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html),
[Thread interruption](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#interrupt()),
[streams](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html),
[Files](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html);
[JEP 444](https://openjdk.org/jeps/444) (pinning, limiting concurrency; [JEP 491](https://openjdk.org/jeps/491) lifts
`synchronized` pinning only from JDK 24);
Spring [transaction thread scope](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html);
Spring Boot [task execution and scheduling](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html).
