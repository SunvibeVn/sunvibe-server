## What and why
Feature / fix:
Brief / spec / plan: sunvibe-hub/docs/features/<module>/<feature>/ (brief/, impl/specs/, impl/plans/)
Related PR (other repo):

## Checklist
- [ ] ./gradlew build passes locally (Docker running for the PostgreSQL tests)
- [ ] API changed: api/openapi.json regenerated and committed; a second exportOpenApi leaves it unchanged
- [ ] Flyway: only new migrations, read line by line by the owner; check-migration-history passes
- [ ] Independent review (hub docs/setup/checklists/be-review.md) without open Blocker/Major; security-reviewer /
      migration-specialist by the triggers in hub docs/setup/AI_FLOW.md section 8
- [ ] /api-handoff written when the FE is affected (BE commit and contract sha256 pinned)
- [ ] /update-docs run and hub docs PR opened
