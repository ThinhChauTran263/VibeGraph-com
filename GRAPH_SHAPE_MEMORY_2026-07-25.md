# Graph Shape Memory

Saved before rolling back to `6bb6cc2` so the current visual layout can be restored later.

## What makes the current graph shape

### Layout engine
- ForceAtlas2 worker in `useSigma.ts`
- `FA2_LINLOG_MODE=false`
- `FA2_OUTBOUND_ATTRACTION=true`
- `FA2_ADJUST_SIZES=false`
- `FA2_STRONG_GRAVITY_MODE=false`
- `FA2_BARNES_HUT_MIN_NODES=500`
- `FA2_GRAVITY=0.001`
- `FA2_SCALING_RATIO=1500`
- `FA2_ITERATIONS=700`
- Large graph profile:
  - `FA2_GRAVITY_LARGE=0.001`
  - `FA2_SCALING_RATIO_LARGE=2000`
  - `FA2_ITERATIONS_LARGE=1000`

### Space shaping
- `LAYOUT_NORMALIZE_SPAN=9000`
- `NOVERLAP_ENABLED=true`
- `NOVERLAP_MARGIN=42`
- `NOVERLAP_RATIO=1.65`
- `NOVERLAP_AUTO_STOP_MS=9000`
- `FA2_OUTLIER_CLAMP_PERCENTILE=0.9`

### Sigma render contract
- `itemSizesReference: 'positions'`
- `zoomToSizeRatioFunction: Math.sqrt`
- `SIGMA_EDGE_SIZE=0.25`
- `SIGMA_MIN_EDGE_THICKNESS=2.8`
- `SIGMA_LABEL_RENDERED_SIZE_THRESHOLD=8`
- `SIGMA_BASE_NODE_LABEL_SIZE=7`
- `SIGMA_BASE_EDGE_LABEL_SIZE=8`

### Node sizing
- `NODE_SIZE_DEFAULT=9`
- `NODE_SIZE_MIN=6`
- `NODE_SIZE_PROJECT=18`
- `NODE_SIZE_PACKAGE=14`
- `NODE_SIZE_FILE=11`
- `NODE_SIZE_TYPE=10`
- `NODE_SIZE_MEMBER=8`
- `NODE_SIZE_ENDPOINT=8`

## Why it looks like the screenshot

- FA2 creates the core branching structure.
- Noverlap pushes overlaps apart.
- The very large normalize span makes the whole graph occupy a wide coordinate field, which makes nodes and edges feel visually smaller and more separated.
- The result is a clustered center with scattered satellites around it.

## Restore recipe

If the exact current shape is needed again after rollback, reapply the values above in:
- `.env`
- `vibegraph-web/src/lib/runtimeConfig.ts`
- `vibegraph-web/src/lib/constants.ts`

