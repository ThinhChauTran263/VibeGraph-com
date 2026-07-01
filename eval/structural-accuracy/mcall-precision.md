# Method-call (CALLS) precision - project 8dab6581

Generated: 2026-07-01 23:13  |  Seed: 42

| Metric | Value |
|---|---:|
| Total CALLS edges | 64 |
| Sampled | 30 |
| Verifiable (caller file found) | 30 |
| Unknown/missing caller file | 0 |
| Verified correct | 30 |
| **Precision** | **100%** |
| Wilson 95% CI | [88.6%, 100%] |

Method: for each sampled CALLS edge, the callee method name is searched (as a call
`name(`) in the caller's source file. Automated proxy - a same-named method in the
same file is a possible false-accept; report with N and CI. Reproducible (fixed seed).

