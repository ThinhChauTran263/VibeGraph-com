# Structural accuracy - spring-petclinic-rest (main)

Generated: 2026-07-01 18:19  |  Project id: `d1b4041a`  |  Source files (main): 87

Ground truth is counted directly from .java source with regex (independent of the
JavaParser engine under test). Recall = min(tool, gt) / gt. Approximate oracle - see caveats.

## Nodes

| Element | Tool | Ground truth (source) | Recall % |
|---|---:|---:|---:|
| Types (class+iface+enum+record) | 84 | 85 | 98.8 |
| - Class (incl. DBModel) | 57 | 57 | 100 |
| - Interface | 27 | 28 | 96.4 |
| - Enum | 0 | 0 | n/a |
| Entities (Entity to DBModel) | 8 | 8 | 100 |
| REST/MVC endpoints | 1 | 0 | n/a |
| Methods | 364 | (not counted in P1) | n/a |

Context: Controller annotations in source = 11; RequestMapping occurrences = 11.

## Edges (tool distribution)

| Edge type | Count |
|---|---:|
| CALLS (method-call resolution) | 165 |
| HAS_METHOD | 387 |
| HANDLES_ROUTE | 1 |
| INJECTS | 3 |

## Caveats (threats to validity)
- Ground truth is regex-derived on comment/string-stripped source; the NAME-level diff
  below (0 missing / 0 spurious = exact) is the authoritative signal, not raw counts.
- Method-call PRECISION requires stratified manual sampling (Phase 2), not counted here.
- Endpoint count uses the 5 verb-mapping annotations; class-level RequestMapping excluded.
- INJECTS reflects only captured dependency edges; constructor injection may be under-counted.

## Type-name diff (diagnosis)

In source but NOT in tool graph (1): PetAgeValidation

In tool graph but NOT matched in source-regex (0): 
