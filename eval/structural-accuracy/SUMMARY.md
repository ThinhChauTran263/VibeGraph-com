# Structural accuracy - aggregate summary (Phase 1 + 2a)

Independent evaluation of VibeGraph's extracted graph against ground truth counted
directly from source (regex on comment/string-stripped `.java`, NOT JavaParser).
Scope: production code (`src/main/java`) only. Name-level diff is the authoritative signal.

## Verdict: NOT 100% overall, but stronger than the first pass suggested

Type and entity extraction are effectively exact. The initially-reported "endpoint gap" on
petclinic-rest was a **measurement error** (see F1) - on the analyzed source, endpoint
detection was actually correct. The real remaining limitations are (a) endpoints that only
exist in build-generated / interface-inherited code, and (b) constructor-injection edges.
Numbers below are real (unedited).

## Per-repo results

| Dimension | spring-petclinic (30 files) | spring-petclinic-rest (87 files) |
|---|---:|---:|
| Types (class/iface/enum) recall | 25/25 = **100%** | 84/85 = **98.8%** (1 = `@interface`, see note) |
| Class (incl. DBModel) | 22/22 = 100% | 57/57 = 100% |
| Interface | 3/3 = 100% | 27/28 = 96.4% |
| Entities (@Entity to DBModel) | 6/6 = **100%** | 8/8 = **100%** |
| REST/MVC endpoints | 16/17 = **94.1%** | 1/1 detectable = **100%** (see F1) |
| Type-name diff (missing / spurious) | 0 / 0 | 1 / 0 |

Note: the 1 "missing" type in petclinic-rest is `PetAgeValidation`, an `@interface`
(annotation). The tool classifies it as an `Annotation` node, not `Interface` - a
categorization difference, not a real miss. So real type recall is ~100% on both repos.

## Two concrete, reproducible findings (for the Evaluation chapter)

### F1 - Endpoint detection: original diagnosis was WRONG; corrected below (LOW/MEDIUM)
- FIRST (incorrect) reading: "petclinic-rest has 11 endpoints but the tool found 1 -> ~9%".
- CORRECTION after inspecting the source: the 11 `@RequestMapping` in petclinic-rest's
  `src/main/java` are all **class-level path prefixes** (`@RequestMapping("api")`), NOT
  endpoints. There are **0** method-level verb-mappings in source. The actual REST
  endpoints are declared on **OpenAPI-generated interfaces** emitted at build time into
  `target/generated-sources/openapi/...`, which neither the tool nor the oracle analyzed
  (source-only scope). The single method-level mapping in source
  (`RootRestControllerV1.@RequestMapping(value="/")`) was **correctly** detected.
  => On the analyzed source, endpoint detection was correct, not defective.
- Lesson (worth stating in the thesis): tool and oracle must share the exact same file
  scope; comparing method endpoints against class-level prefixes produced a false gap.
- GENUINE improvement still shipped: `@RequestMapping(method = RequestMethod.X)` (and the
  multi-verb `{...}` form) now resolves to the concrete verb(s) instead of a generic
  `"REQUEST"` label. This helps codebases written in the older `@RequestMapping(method=)`
  style on concrete controllers. It does **not** change the petclinic numbers (neither repo
  uses that style in source), so no accuracy lift is claimed here - only correctness.
- OPEN item (not fixed): endpoints defined on build-generated / interface-inherited
  mappings are invisible to source-only analysis. Capturing them would require analyzing
  generated sources or resolving interface inheritance - a larger, separate task.

### F2 - Constructor injection now captured (FIXED, measurable lift)
- Before: petclinic used constructor injection, so `INJECTS = 0` (field `@Autowired` only).
- After: the visitor now emits INJECTS for the constructor parameters of Spring-managed
  beans (single constructor auto-wired; with several, only the `@Autowired` one). Primitive
  and common value types are skipped; plain non-bean POJOs are ignored.
- Measured E2E on spring-petclinic (main): `INJECTS` **0 -> 6** after the fix.
- Unit-tested (constructor params injected, plain POJO ignored, multi-constructor prefers
  `@Autowired`, value-type params skipped).

## What is already strong (defensible claims)

- **Type extraction: ~100%** across both repos (0 spurious types in either).
- **Entity detection: 100%** across both repos.
- **Endpoint detection on classic verb-mapping controllers: ~94%**.
- **Method-call (CALLS) precision: 100%** on a 30-edge sample of spring-petclinic
  (Wilson 95% CI [88.6%, 100%]); no fabricated calls found. See `mcall-precision.md`.

## Next steps (prioritized by the numbers)

1. DONE (this change): `@RequestMapping(method=)` + multi-verb `{...}` now resolve to the
   concrete verb(s) instead of "REQUEST". Unit-tested; no petclinic accuracy delta (neither
   uses that style in source).
2. DONE (F2): constructor-parameter injection now captured as INJECTS. Measured lift on
   spring-petclinic: 0 -> 6.
3. OPEN F1 remainder: endpoints on build-generated / interface-inherited mappings need
   generated-source analysis or interface-inheritance resolution - larger separate task.
4. DONE (Phase 2b): method-call CALLS precision measured by seeded random sampling +
   source verification, with a Wilson 95% CI. See `mcall-precision.ps1` / `.md`.
5. Add a 3rd/4th repo (a `@PreAuthorize` CRUD, a service-only lib) for breadth.

## Reproduce

See `README.md`. Per-repo raw reports: `pc/report.md`, `pcrest/report.md` (+ `.csv`).
