---
paths:
  - "modules/*/src/main/java/**/core/application/**"
  - "app/src/main/java/**/integration/**"
---
# Application layer rules (details: docs/architecture/layer-guide.md#application-layer-application)
- Commands (application/command): Java records, no business logic; shape checks the request DTO does not already
  guarantee go in the compact constructor.
- Queries (application/query) for read models; keep reads and writes in separate use cases.
- Use cases (application/usecase): new modules group operations per aggregate with named methods, as acquisition-service
  and survey-service do (OwnerLeadCommandUseCase, SurveyAppointmentCommandUseCase). One interface per operation with a
  single execute(...) method stays valid where it exists (PropertyCreateUseCase); property-service's area catalog groups
  named reads (AdministrativeAreaQueryUseCase).
- Implementations: @Service + @Transactional, implement the use case, orchestrate domain objects
  through domain repository interfaces. They live in application/service/ in every module (`naming-layout.md`).
- External systems (email, SMS, audit, token) are ports (interfaces) in application/shared/external/<system>/; adapters
  live in infrastructure. A call to another module is a port in application/shared/external/<topic>/ with its adapter
  in app (ADR-009).
- Never import infrastructure.* or presentation.*.

## Transaction review additions

Apply `review-policy.md` for evidence/severity; these checks refine the transaction-placement rule above.

- **Mandatory:** for a transactional use case, trace the actual boundary and transaction manager. In proxy mode,
  self-invocation, manually constructed services, private methods and @PostConstruct code do not create the requested
  intercepted boundary; an already active outer transaction may still apply. A self-call cannot activate REQUIRES_NEW
  or a new rollback/read-only setting. Use a suitable bean boundary or TransactionTemplate when required, not
  self-injection as a mechanical fix. Check the configured proxy type before assumptions about final/non-public methods.
- **Mandatory:** verify success AND failure effects. By default RuntimeException/Error roll back and checked
  exceptions do not; inspect local rollback rules and any global override. Catching an exception or returning a
  failure result can commit prior writes; catching an inner failure may still leave REQUIRED rollback-only and
  cause UnexpectedRollbackException. Use the intended semantics, not a blanket `rollbackFor = Exception.class`.
- **Mandatory:** keep intentional noRollbackFor narrow and justified (e.g. auth failed-attempt counters/revocation
  must survive the rejected request); verify the allowed committed changes. Do not remove it just to make every
  domain exception roll back. The rule must hold at every transactional boundary the exception crosses: an inner
  REQUIRED method without it marks the whole transaction rollback-only. A successful repository save or flush is not
  a committed transaction.
- **Mandatory:** ADR-009 synchronous cross-module ports join the caller's transaction. Do not introduce REQUIRES_NEW,
  async or a different manager that breaks the approved atomic operation. For an intentionally independent unit,
  explain failure/consistency semantics and connection-pool/lock implications. REQUIRES_NEW needs a second connection
  while the outer one stays bound; Spring warns of pool exhaustion when threads can outnumber connections, as here
  (Hikari 20, Tomcat 200), so state why the pool cannot starve. An inner transaction must not wait on locks held by
  its suspended caller.
- **Mandatory:** a readOnly transaction is neither a safe place for writes nor a security control. Spring calls the
  flag a hint, but on this stack Hibernate does not flush, so entity changes are not written, and the connection
  begins READ ONLY, so PostgreSQL refuses direct SQL writes. A REQUIRED inner method joins the outer transaction and
  silently ignores its own readOnly, isolation and timeout. Check writes, lazy reads and DTO mapping within the
  intended scope.
- **Mandatory:** for effects required only after commit, follow the approved events/outbox design (ADR-003/009).
  Ordinary event publication is not automatically after commit; an AFTER_COMMIT listener is not durable delivery
  across a crash, and it is skipped when no transaction is active unless `fallbackExecution` is set. If a required
  effect must survive restart, atomically persist the planned durable job/outbox intent with the state change and use
  idempotent handling. Any DB write from an after-commit callback needs a real new write transaction; verify the call
  boundary. On an AFTER_* @TransactionalEventListener, Spring accepts @Transactional only with REQUIRES_NEW or
  NOT_SUPPORTED. Preserve refusal/audit events intentionally surviving a rollback.
- **Mandatory:** do not hold a DB transaction/connection while media upload/download bytes stream (ADR-011). Keep
  scanner/processor and storage I/O outside DB scopes as the media design requires; keep short TransactionTemplate
  scopes and revalidation at the authorized completion boundary. This is an approved exception to blanket
  service-level @Transactional; do not wrap an entire media service mechanically.
- **Advisory:** for other slow external I/O, minimize lock/connection hold time when the consistency contract allows;
  splitting a transaction needs explicit failure/retry/compensation semantics. Do not ban every external call in a
  transaction or promise that a database rollback can undo a sent email/remote write.

References: Spring [proxy boundaries](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html),
[rollback](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html),
[propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html),
[rollback-only participation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/AbstractPlatformTransactionManager.html#setGlobalRollbackOnParticipationFailure(boolean)),
[transactional events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html),
[listener javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/event/TransactionalEventListener.html);
pgjdbc [readOnlyMode](https://jdbc.postgresql.org/documentation/use/).
