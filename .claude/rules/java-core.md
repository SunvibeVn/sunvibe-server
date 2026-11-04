---
paths:
  - "**/*.java"
---
# Java core review rules

Java 21 baseline. Apply the evidence and severity policy in `review-policy.md`; Mandatory applies only under the
stated condition, Advisory is a suggestion. Preserve module-local types and approved feature exceptions.

## Collections and value semantics

- **Mandatory:** when choosing/changing a collection, preserve required ordering, duplicates, null handling and
  mutability. A Set silently discards duplicates; HashMap/HashSet do not promise encounter order. Handle duplicate
  keys deliberately in `Collectors.toMap` (reject, merge or group according to the contract), not by guessing; a null
  value fails there too. Replacing `collect(Collectors.toList())` with `Stream.toList()` makes the result unmodifiable.
- **Advisory:** use ArrayList for ordinary indexed/append-and-iterate lists; HashSet/HashMap for uniqueness/key lookup
  without order; LinkedHashSet/LinkedHashMap for encounter order; TreeSet/TreeMap for sorted/range operations with a
  suitable comparator; EnumSet/EnumMap for enum membership/keys; ArrayDeque for a local queue/stack without nulls.
  LinkedList is not automatically faster for insertion: locating an indexed position is linear. Choose by operations.
- **Mandatory:** use generics and avoid raw types. Keep unavoidable unchecked casts narrowly scoped and document
  the invariant that makes them type-safe. When an aggregate, VO or record promises immutability, defensively copy
  mutable inputs/outputs as needed. Records are shallow; `Collections.unmodifiableList(source)` still observes source
  mutations. `List.copyOf` rejects nulls and does not copy mutable elements. `List.of`, `Set.of` and `Map.of` results
  throw on a null lookup such as `contains(null)`. Preserve established null contracts instead of blindly replacing
  all collections.
- **Mandatory:** custom equality must be symmetric/transitive and give equal objects equal hashes. Keep equality
  fields stable while used as hash keys/set elements. Follow the module's ValueObject `equalsCore/hashCodeCore`
  hooks where present (see `domain-layer.md`); use matching attributes and any normalization in both operations.
  Compare strings/boxed values by value, not `==` unless identity is intended; enums may use `==`.
- **Mandatory:** if comparator equality differs from equals, check TreeSet/TreeMap duplicate/key semantics against
  the contract. For JPA entities, review identity/proxy/lifecycle behavior in `persistence.md`; do not generate
  equality/hash/toString over mutable or lazy associations with Lombok `@Data` without checking consequences.

## Strings and absence

- **Advisory:** use a method-local StringBuilder for repeated variable-size string accumulation (e.g. a loop);
  short `+` expressions are fine. Do not require a builder for every concatenation or pre-size from unbounded input.
- **Mandatory:** a StringBuilder shared across threads needs an explicit synchronization protocol. StringBuffer
  synchronizes individual operations; it does not make a multi-call check-then-act sequence atomic. Do not recommend
  StringBuffer merely because the server is concurrent; a request-local builder is not shared state.
- **Mandatory:** case folding for a key or comparison uses `Locale.ROOT`: the default locale changes the result
  (Turkish `i`). `String.length()` and Bean Validation `@Size` count UTF-16 units, so an emoji counts 2; a limit shared
  with the frontend uses that same unit.
- **Advisory:** use Optional for meaningful possibly absent return values; prefer an empty collection for no results
  when that is the contract. Avoid introducing Optional fields/parameters/JPA or JSON properties without checking
  serialization/mapping and existing API conventions; do not force a redesign of an established contract.
- **Mandatory:** never return null from an Optional-returning method. Use `ofNullable` for nullable input; unwrap
  only when presence is established or with a deliberate exception/default. `orElse(expensiveOrSideEffect())`
  evaluates its argument even when present; use `orElseGet` when the fallback must be lazy.

## Exceptions

- **Mandatory:** do not swallow an exception, or log it and continue, unless the contract says that failure is
  tolerable. Catch the narrowest useful type and keep the cause when wrapping. `catch (Exception)` also catches
  programming errors, so it belongs only at a real boundary such as a job loop or a third-party adapter. In a
  transactional path a catch changes what commits (`application-layer.md`); InterruptedException has its own rule
  (`performance-concurrency.md`).

## Money, numbers and time

- **Mandatory:** monetary calculations must preserve exact VND values; do not introduce float/double intermediates.
  Construct decimals from exact strings/integral values; `BigDecimal.valueOf(double)` cannot undo precision already
  lost upstream. If division/rounding is needed, use the approved scale, rounding point and RoundingMode, or reject
  inexact input when required; do not invent a rounding policy. Check narrowing conversions/overflow at boundaries.
- **Mandatory:** distinguish `BigDecimal.compareTo` numeric equality from scale-sensitive `equals/hashCode`:
  `1.0` and `1.00` compare as zero but are not equals. If a VO normalizes scale, keep its hash consistent; do not
  globally replace equals with compareTo or silently change collection keys.
- **Mandatory:** follow `../docs/decisions/ADR-006-id-and-money.md`: new monetary columns use NUMERIC(15,0) VND and
  the approved amount/currency/unit contract. Use the owning feature's approved Money representation; property
  Money has whole-VND rounding, while survey-checklist documents a feature-local Long-record exception. Do not add
  a dependency between feature modules to reuse a VO or move it to shared-service without an approved need/decision.
- **Mandatory:** distinguish a point in time (Instant), a calendar date (LocalDate) and local wall time
  (LocalDateTime); conversions that need a zone must use the contract's explicit ZoneId/offset, not the host default.
  ADR-006 requires new timestamp columns to use Instant/TIMESTAMPTZ; legacy property LocalDateTime waits for its
  separately approved migration. Do not rewrite it as checklist cleanup.
- **Mandatory:** new main code reads the time from AppClock or a clock port such as MediaClock, not from a direct
  `*.now()`; a bare `LocalDate.now()` also uses the host zone. Direct calls already in the code, for example in
  PropertyDocument, JWTProvider and MediaSpool, are pre-existing.
- **Mandatory:** PostgreSQL stores timestamps to the microsecond: when a time is compared after a database round trip,
  truncate it first (`truncatedTo(ChronoUnit.MICROS)`, as the survey-service clocks do). Bound client-supplied dates
  before arithmetic: a value near `Instant.MAX` overflows into a 500 (SV-033).
- **Advisory:** prefer immutable `java.time` APIs and Duration/Period matching elapsed-time/calendar semantics.
  For deadline/expiry logic, use the project seam `testing.md` describes: AppClock is static wall time, not a fixed-clock
  seam, so pass `Instant now` into the rule or inject a port such as `MediaClock` where deterministic behavior needs it.

## References

- Oracle Java 21: [List](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/List.html),
  [Map](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html),
  [ArrayDeque](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayDeque.html),
  [Collectors](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collectors.html),
  [Object equality](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html#equals(java.lang.Object)).
- Oracle Java 21: [StringBuilder](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/StringBuilder.html),
  [Optional](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Optional.html),
  [BigDecimal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html),
  [java.time](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/package-summary.html).
- PostgreSQL 17: [date/time types](https://www.postgresql.org/docs/17/datatype-datetime.html).
