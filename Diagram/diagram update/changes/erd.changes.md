# ERD changes: old artifact -> verified current artifact

## PostgreSQL mapping

| Old artifact/page | Current artifact/section | Evidence-backed change | Evidence |
| --- | --- | --- | --- |
| Old `ERD PostgreSQL` page and 10-table PlantUML subset | `plantuml_erd_component_class.md` PART 1 and the PostgreSQL page in `3.ERD Diagram` | Expands to all 21 domain tables and all 23 physical foreign keys observed in the running schema. The diagrams.net page is now a full table/FK companion instead of grouped subsystem boxes. | `src/main/resources/db/migration/V1__init_auth_schema.sql`; `src/main/resources/db/migration/V2__phase4_account_foundation.sql`; `src/main/resources/db/migration/V3__plans_and_credits.sql`; runtime `information_schema` audit at `2026-08-14T10:12:42+07:00`. |
| Old incomplete column lists | PART 1 entity definitions | Restores current timestamp/audit/display columns, including `audit_logs.ip_address`, `credit_pricing_rules.display_name`, and the `created_at`/`updated_at` fields proved by migrations/JPA/runtime. | `src/main/resources/db/migration/V7__admin_ops.sql:5-52`; `src/main/resources/db/migration/V10__phase7_support_audit_notifications.sql:11-57`; `src/main/resources/db/migration/V18__refresh_sessions.sql:1-20`; `src/main/java/com/vibegraph/auth/domain/AuditLog.java:50`; `src/main/java/com/vibegraph/auth/domain/UserAccountSettings.java:55`. |
| Old mandatory-looking one-to-one/nullable relationships | PART 1 FK arrows | Corrects parent optionality for `user_account_settings` and `project_usage` (`0..1` child per parent) and shows nullable `ON DELETE SET NULL` foreign keys as optional parent references. | Runtime `information_schema.columns`/FK audit; `src/main/resources/db/migration/V2__phase4_account_foundation.sql`; `src/main/resources/db/migration/V8__anti_abuse.sql`; `src/main/resources/db/migration/V10__phase7_support_audit_notifications.sql`. |
| Old index summary | PART 1 note | Defines the count precisely: 66 indexes on the 21 domain tables, 68 across public schema when the two Flyway metadata indexes are included. Records the important compound/functional/partial unique indexes by name. | Runtime `pg_indexes` audit at `2026-08-14T10:12:42+07:00`; SQL migrations `V1`, `V2`, `V3`, `V4`, `V10`, `V12`. |
| Old audit actor/target links | `audit_logs` logical-reference fields | Keeps actor/target UUIDs but removes physical FK arrows because V15 deliberately drops those constraints. | `src/main/resources/db/migration/V15__audit_log_transaction_hardening.sql:1-7`. |
| Old orphan `system_control_settings` claim | Omitted from current schema | Does not reconstruct missing V16 content. V20 explicitly removes the orphan table; Flyway reports 19 successful migrations. | `src/main/resources/db/migration/V20__drop_orphan_system_control_settings.sql:4-19`; runtime Flyway history audit. |

## Neo4j mapping

| Old/current stale claim | Corrected current representation | Evidence |
| --- | --- | --- |
| `IMPORTS` as `File -> File` | Primary type (`Class`/`Interface`/`Record`/`DBModel`) -> imported type, `External` or `Package` | `src/main/java/com/vibegraph/parser/visitor/ImportVisitor.java:20-39`; runtime endpoint audit. |
| `HAS_INNER` as type -> member | Outer type -> inner type | `src/main/java/com/vibegraph/parser/visitor/ClassVisitor.java:82-86`; runtime endpoint audit. |
| `OVERRIDES` as type -> type | Method -> Method | `src/main/java/com/vibegraph/parser/visitor/MethodVisitor.java:400-417`; runtime `OVERRIDES=240`. |
| `HAS_RELATION`/`INJECTS` with member endpoints | Owner type -> related/injected type or external target | `src/main/java/com/vibegraph/parser/visitor/FieldVisitor.java:64-83`; `src/main/java/com/vibegraph/parser/visitor/SpringAnnotationVisitor.java:157-166`; runtime endpoint audit. |
| Omitted current relationships | Adds `INSTANTIATES`, `CATCHES` and `STEP_IN_FLOW`; records `ANNOTATED_BY=1,712` as persisted legacy data rather than current emission | `src/main/java/com/vibegraph/parser/visitor/MethodVisitor.java:329-339`; `src/main/java/com/vibegraph/parser/visitor/MethodVisitor.java:545-563`; `src/main/java/com/vibegraph/parser/flow/FlowAnalyzer.java:15-47`; runtime audit. |
| `isStub` and Neo4j Project `status` properties | Removes both current-property claims. Project status remains PostgreSQL control-plane data. | `src/main/java/com/vibegraph/graph/repository/impl/neo4j/Neo4jGraphRepository.java:199-204`; runtime property audit. |
| Route constraint implied to cover `APIEndpoint` | Shows `Route` as a V1 constrained/indexed label with runtime count 0, and `APIEndpoint` as the current emitted label with runtime count 620 and no Route constraint coverage | `src/main/resources/db/migration/V1__init_schema.cypher:33-100`; `src/main/java/com/vibegraph/parser/visitor/SpringAnnotationVisitor.java:120-129`; runtime label/schema audit. |
| Generic V1/V2 note | Lists the exact V1 constraint/index names and V2 shared `Symbol` indexes | `src/main/resources/db/migration/V1__init_schema.cypher`; `src/main/resources/db/migration/V2__symbol_label.cypher`. |

## Runtime evidence boundary

- PostgreSQL schema facts were observed at `2026-08-14T10:12:42+07:00`: 21 domain tables,
  23 FKs, 66 domain indexes (68 public including Flyway) and 19 successful migrations. Volatile
  table row counts are intentionally omitted from the canonical ERD.
- Neo4j at the same audit window contained 56,724 nodes and 116,987 relationships. These are
  timestamped observations, not cardinality guarantees.
- Current `DEFINES` emission is File -> Class/Interface/Enum/Record/DBModel. Older persisted
  `DEFINES` endpoints remain in runtime data and are documented as drift, not promoted to current
  parser behavior.
- `OWNS`, `PUBLISHES_EVENT`, `LISTENS_EVENT`, `TRIGGERS`, `CALLS_DYNAMIC` and
  `DISPATCH_CANDIDATES` returned zero rows and are not drawn as observed relationships.

## File-level mapping

| Old file | Current file | Change record |
| --- | --- | --- |
| `Diagram/3.ERD Diagram` | `Diagram/diagram update/3.ERD Diagram` | Two valid diagrams.net pages: full PostgreSQL 21-table/23-FK ERD and current-vocabulary Neo4j schema/runtime view. |
| ERD portions of `Diagram/plantuml_erd_component_class.md` | PART 1/PART 2 under `Diagram/diagram update/` | Canonical verified ERD source. |
| ERD portions of the combined PlantUML file | Matching combined portions under `Diagram/diagram update/` | Generated mirror only. |

## Claims intentionally not proven

- Neo4j is schema-optional; a migration or allow-list entry alone does not prove current emission.
- Runtime counts can change after imports/analysis and do not represent an external production DB.
- Application identifiers without PostgreSQL FK constraints are not drawn as physical FKs unless
  explicitly labeled logical references.
