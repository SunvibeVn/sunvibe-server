---
paths:
  - "modules/*/src/main/java/**"
  - "app/src/test/java/**/architecture/**"
---
# Names and packages: one standard for every module

Source: sections 4 and 5 of `../docs/rebuild/implementation/2026-11-06-cleanup-spec.md` (owner, gate 1, 2026-11-06; SV-162),
and its amendment of 2026-11-07 ([DEL-5], Claude; SV-265).
`app/src/test/java/vn/sunvibe/architecture/NamingConventionArchTest.java` fails the build on what it can check.

- A plan that breaks this file is a conflict, whether or not behaviour changes: stop the dependent work and report it
  (spec P-1). A word in a spec becomes a class name in the plan, so both use only what is written here.
- A new kind of package or a new word needs a row here first.
- What stays on purpose (DTO names, `mapper/{auto,manual}/`, auth's `v1`, ...) is listed in spec 3.1.

## Packages, under `vn.sunvibe.<module>.core`

`<area>` is a sub-package used when a module has more than one area (auth: `auth`, `role`, `staff`, `user`, `v1`; survey:
`appointment`, `checklist`, `media`, `common`, where a class serving more than one area goes to `common`). It is
optional: a small folder stays flat. `<concept>` is a domain concept such as `lead` or `unit`. Packages this table does
not list stay as they are: `infrastructure/{config,security,email,sms,audit,media,json}`, `persistence/migration`,
`presentation/config`, `<module>.core.shared`.

| Package | Holds | Class names |
|---|---|---|
| `domain/model/<concept>/` | aggregate, entities, value objects, enums; a large concept may add `vo/` and `enums/` inside it | no suffix rule |
| `domain/policy/<concept>/` | pure rules | `*Policy`, `*Rules` |
| `domain/repository/[<area>/]` | repository interfaces and their small records | `*DomainRepository`, some `*Repository` |
| `domain/exception/[<area>/]`, `application/exception/[<area>/]` | exceptions | `*Exception` |
| `application/command/[<area>/]` | command records | `*Command`, parts `*Input` |
| `application/query/[<area>/]` | read models | `*View`, `*Result` |
| `application/usecase/[<area>/]` | use case interfaces and the records and exceptions they publish; no implementation | `*UseCase`, their records and exceptions |
| `application/service/[<area>/]` | use case implementations and their collaborators | `*Service` for the implementation |
| `application/shared/[<area>/]` | helpers shared inside the application layer | |
| `application/shared/external/<system>/` | ports to other modules and systems | `*Port` and its records and exceptions |
| `infrastructure/persistence/{entity,repository,adapter}/[<area>/]` | JPA | `*Entity` (keys `*Id`, `*Key`), `*JpaRepository`, `*Adapter` (package-private helpers may stay beside their adapter) |
| `infrastructure/persistence/mapper/{auto,manual}/` | domain and entity mapping | `*Mapper`, manual ones `*EntityMapper` |
| `infrastructure/persistence/converter/` | only classes that implement JPA `AttributeConverter` | `*AttributeConverter` |
| `infrastructure/persistence/json/` | stored document (de)serialisation | `*Codec` |
| `infrastructure/persistence/support/` | small persistence helpers | |
| `infrastructure/scheduling/` | scheduled triggers | `*Job` |
| `presentation/controller/[<area>/]` | REST controllers | `*Controller` |
| `presentation/converter/[<area>/]` | DTO to command, result to DTO | `*Converter` |
| `presentation/resolver/` | the signed-in user as a viewer or actor | `*Resolver` |
| `presentation/dto/{request,response}/[<area>/]` | DTOs | see the words below |
| `presentation/exception/`, `filter/`, `validation/`, `openapi/` | as named | `*ExceptionHandler` and `*Exception` (in `exception/`), `*Filter` |

An exception that crosses a module boundary sits with its contract, in `application/usecase/` or
`application/shared/external/<system>/`, not in `application/exception/`: `ModuleBoundaryArchTest` lets an integration
adapter read only those two packages (ADR-009). No class sits directly in `domain/policy/`; survey-service uses its
areas as the concepts there (`domain/policy/{appointment,checklist,media,common}/`).

`presentation/scheduler/` and `application/usecase/external/` are not homes for new code. survey-service keeps its media
job classes in `application/service/media/` and its programmatic registration in `infrastructure/media/`, a documented
design (`performance-concurrency.md`). Its `presentation/converter/` stays flat: its classes call each other
package-private across areas.

Package names that code compares do not move (spec R-5): the `basePackages` of the exception handlers, the `*JpaConfig`
scans, and `dto/request/checklist` and `dto/request/media` of survey-service, which `StrictChecklistJsonConfig` compares
exactly, so they keep their records and gain no sub-package.

## What presentation may use

Presentation may use domain enums, value records and exceptions, wherever they sit, `domain.policy` and
`domain.repository` included (`PageResult`, for example). It never uses repository interfaces, classes that hold rules,
or aggregates, except a named allowlist in the test. The list only shrinks, except that splitting an allowlisted class
replaces its entry with the new classes. The list, with a reason per class, and the check are spec N-14 in
`NamingConventionArchTest` (`presentation_usesNoRepositoryRulesOrAggregate`).

## What each word means

| Word | Means | New code |
|---|---|---|
| Converter | presentation: DTO to command or result to DTO; a pure function of its arguments | no security context, no use case call |
| Mapper | persistence: domain object to JPA entity and back | |
| AttributeConverter | implements `jakarta.persistence.AttributeConverter` | only here |
| Resolver | reads the signed-in principal and returns a viewer or actor | |
| Assembler | application: composes a read model from several sources | |
| Codec | object to stored text and back | |
| Policy | a pure decision: may, must, which | |
| Rules | pure checks of values that return violations | |
| Normalizer | a pure function that reduces a text to the form it is compared in | |
| Result | what a command returns | existing query results keep their name |
| View | what a query returns | |
| Facts | read snapshot passed across a port | |
| Type | classification that is stored or exposed | |
| Kind | a discriminator used only inside code | |
| Input | nested part of a request DTO | top-level stays `*Request` |
| Job | a scheduled trigger that calls one use case or port | |

A nested response DTO has a feature-prefixed noun and never the name of a domain class.
