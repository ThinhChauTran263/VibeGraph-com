# Grapuco Runtime Verification — 2026-08-18

**Người thực hiện:** Qoder (chrome-devtools MCP, phiên thật của user)
**Tài khoản:** thinhtran09177@gmail.com — **139 credits** (đã nạp; phần Claude bị chặn ở -5 credits giờ thông suốt)
**Repo:** `fatc-Grocery-Store` → id `9adbeb67-ce1b-49c7-beb2-70f5b607d2fa` (upload mới; khác id `eae6552e-…` của phiên Claude)
**Mục đích:** xác minh những phần Claude đánh dấu ⚠️ UNVERIFIED trong `SessionChatClaude.md` / `07-OPEN-QUESTIONS.md`.

---

## 1. Endpoint graph/schema — KHÔNG còn treo

`GET https://api.grapuco.com/graph/schema/9adbeb67-…` → **200** ngay lập tức (853 bytes).
Cái "treo vô hạn" của Claude là **credit gating** (-5 credits), không phải lỗi kỹ thuật. Nạp credit hết treo.

Response = metadata thuần (đếm label/edge), **không có toạ độ**:

```json
{"nodeLabels":[{"label":"CodeElement","count":1565},{"label":"Method","count":933},…],
 "edgeTypes":[{"type":"CALLS","count":1487},…],
 "totalNodes":3575,"totalEdges":5402}
```

## 2. Toạ độ KHÔNG precompute ở backend ← chốt hạ ẩn số lớn nhất

`GET /graph/architecture/{id}?group=Method&skip=0&limit=500` → 200, `{nodes, edges, pagination}`.
Đo trong page: **1266 nodes, 0 node có x/y** (`withXY: 0`).

Node keys chỉ là ngữ nghĩa:
`id, label, endLine, repoId, entryPointReason, parameterCount, startLine, filePath, description, language, astFrameworkReason, isExported, tenantId, name, entryPointScore, astFrameworkMultiplier`

⇒ **Layout chạy 100% client-side** (d3-force trong browser). Giả thuyết "toạ độ tính sẵn ở backend" của Claude (suy từ `cooldownTicks:0/warmupTicks:0`) là **SAI cho graph chính** — cặp props đó thuộc một instance embedded/mini (backgroundColor trong suốt, `enablePanInteraction:false`); graph chính dùng default của lib (`cooldownTicks: Infinity`, `cooldownTime: 15000`) → simulation chạy live ≤ 15 s, render dần nên không có pha "Finalizing" chặn UI.

## 3. Tham số d3-force THẬT (trích nguyên văn từ bundle `/_next/static/chunks/0qy4-68qybnav.js`)

```js
.force("charge",     forceManyBody().strength(-3e3).distanceMax(5e3))          // -3000, cắt ở 5000
.force("collision",  forceCollide(e => Math.max(3*(e.val||8), 10) + 100))      // bán kính vẽ + PAD 100
.force("link",       forceLink(edges).id(e => e.id).distance(150))             // link 150
```

Vẽ node (graph-space, p=1):

```js
// nodeCanvasObject
let s = Math.max(3*(e.val||8), 10);           // bán kính ĐƠN VỊ GRAPH
a.arc(e.x, e.y, s, 0, 2*Math.PI)              // màn hình = s·k  ⇒ tỉ lệ tuyến tính với zoom
// nodePointerAreaPaint — hit-test dùng CHÍNH bán kính đó ⇒ picking khớp hình vẽ
```

**Bí mật của khe hở:** pad collide = **100 graph units**, trong khi bán kính vẽ chỉ 24–50 units. Hai node bất kỳ bắt buộc cách tâm ≥ (rA+100)+(rB+100) ≈ 250+ units — link distance 150 còn NHỎ hơn collide, nên kể cả node nối nhau cũng không chạm. Pad cùng không gian graph với node ⇒ bất biến theo zoom (p=1) ⇒ giải một lần, đúng mãi mãi.

Engine defaults (chunk lib `0yqh6xsxkbt5u.js`): `minZoom 0.01`, `maxZoom 1000`, `cooldownTime 15000`, `d3AlphaDecay 0.0228`, `d3VelocityDecay 0.4`, `nodeRelSize 4`.

## 4. Đo trực tiếp trên canvas thật (pixel analysis + d3 `__zoom`)

| Đại lượng | Đo được | Suy ra |
|---|---|---|
| k tại fit | 0.01674 | — |
| k sau 10 tick in | 0.08833 | ~1.18×/tick |
| Đường kính Class (vàng) tại k=0.0883 | median **11 px** (92 clusters, p90 11.5) | phân phối sạch |
| k sau +8 tick | 0.33427 | ×3.785 |
| Đường kính Class tại k=0.3343 | median **41 px** | ×3.727 |
| **Số mũ p** | ln(3.727)/ln(3.785) = **0.99** | **p = 1.0 — graph-space sizing, xác nhận bằng số** |
| Zoom-out 60 tick | k dừng ở **0.01** | = `minZoom` default → clamp cứng, khớp Claude |
| Ảnh tại floor k=0.01 | node = chấm 1–2 px **rời nhau**, không bết khối | khớp mô tả của Claude |
| Ảnh tại k=0.64 (~38× fit) | bóng 60–100 px, khe đen rõ, label đọc được, edge hairline | khớp Claude |

Mật độ tại fit (tính từ tham số): khe tối thiểu ≈ 250 units × k(0.0167) ≈ **4.2 px**, bán kính vẽ ≈ 0.8–1.7 px ⇒ khe luôn lớn hơn node ⇒ **không thể chồng ở bất kỳ zoom nào**.

## 5. Đối chiếu với VibeGraph (trạng thái hiện tại)

| Thành phần | Grapuco (đo thật) | VibeGraph hiện nay | Kết luận |
|---|---|---|---|
| Node circle | graph-space, p=1 (đo 0.99) | p=1 (`ZOOM_SIZE_POWER=1.0`) ✓ | ĐÃ ĐÚNG |
| Clamp zoom-out | minZoom 0.01 | `maxCameraRatio: 4` ✓ | ĐÃ ĐÚNG |
| Edge line | screen-space, dày cố định | `SIGMA_MIN_EDGE_THICKNESS=2.8` ✓ | ĐÃ ĐÚNG |
| De-overlap | **ràng buộc TRONG simulation**, pad = +100 units (≈ 2–4× bán kính vẽ) | post-pass `settleScreenOverlaps` sau FA2 | KHÁC TRỤC — nhưng ổn nếu mật độ khả thi |
| Node size tại fit | chấm 1–2 px (bán kính graph 24–50 units × k nhỏ) | 6–18 px cố định theo `.env` | **THỪA 3–8× ngân sách diện tích** → blob (xem phân tích mật độ phiên trước) |

⇒ Chẩn đoán "blob tròn" của VibeGraph giữ nguyên: với 1.512 node × bán kính 6–18 px, viewport ~600k px² thiếu ~2× diện tích; settle đốt 160 iteration → đóng gói tròn. Grapuco tránh được vì **fit-view node của họ là chấm 1–2 px** (phình tuyến tính khi zoom nhờ p=1) — đúng thứ giải pháp *density-adaptive fit size* sẽ đem lại cho VibeGraph (coverage ≈ 0.3 ⇒ scale ≈ 0.35–0.5 cho 1.5k node, tức bán kính fit ~3–6 px — cùng trật tự với Grapuco).

## 6. Kết luận cho các ⚠️ UNVERIFIED của Claude

1. ✅ Tham số layout thật: charge −3000/dMax 5000, collide = draw+100, link 150 (§3).
2. ✅ Toạ độ KHÔNG precompute backend (§2) — đính chính suy đoán của Claude.
3. ✅ p = 1 đo bằng số (§4), không chỉ bằng mắt.
4. ✅ minZoom = 0.01 hard clamp (§4).
5. ✅ Hit-test dùng bán kính đã scale (cùng `s` trong `nodePointerAreaPaint`) — picking khớp hình vẽ ở mọi zoom.
6. ✅ Schema "treo" = credit gating; hết credit là 200 ngay.

## 7. VibeGraph sau khi áp dụng 3-layer hybrid (BLOB-1..4, đo trên host 8080/5173)

Đo trên project `2c67c31c-b65d-42ba-b128-43cf88501339` (1.512 node hiển thị,
viewport 984×612) qua DEV-only QA hook `window.__vibegraph_qa`:

| Đại lượng | Trước (blob tròn) | Sau BLOB-1..4 |
|---|---|---|
| Fit view | đĩa tròn đặc, node 6–18 px chồng nhau | speckle hữu cơ + nhánh/rìa, dots ~2 px |
| Touching pairs (fit) | hàng nghìn (đa số node) | **240 / 1.14M pairs (0.02%)** |
| Node size fit | 6–18 px cố định | median 2 px, max ~3.4 px (density-adaptive) |
| Zoom 6.7× | vẫn chồng | bóng tách rời, label đọc được, khe đen rõ |
| Settle | 21–32 s | ~9 s (8 s worker budget + pass ms) |
| Engine | FA2 + post-pass giằng co | ngraph worker (Layer 1) + d3-forceCollide (Layer 2) + Sigma (Layer 3) |

Env tuning chốt (local `.env`, gitignored): `VITE_LAYOUT_FIT_SIZE_COVERAGE=0.25`,
`VITE_LAYOUT_SCREEN_OVERLAP_GAP_PX=6`, `VITE_COLLIDE_ITERATIONS=60`,
`VITE_NGRAPH_GRAVITY=-2.5`.

Fix kèm phát hiện khi verify: `runPostLayoutPass` phải gọi `refresh()` FULL
(không `skipIndexation`) để Sigma re-index bounds — nếu không camera giữ framing
của vị trí cũ và zoomToFit không khung đúng layout cuối.

### 7.1 Bản chỉnh sửa sau feedback người dùng (core vẫn bết tấm xanh)

Đo lại qua chrome-devtools sau mỗi vòng sửa (project 2c67c31c, fit view):

| Vòng | Thay đổi | Touching pairs | nn P10 (px) |
|---|---|---|---|
| 0 | BLOB-1..4 chốt vội | 844 | 2.58 |
| 1 | revert env tuning (gravity -2.5 nén core sau normalize) | 844→280* | 3.15 |
| 2 | **collide VÀO trong worker** (post-hoc không tạo được diện tích) + fix d3 **radius cache** (phải gọi lại `force.radius()` sau khi đổi r) | 587 | 4.07 |
| 3 | strength 1.0, 2 collide ticks/loop vs 2 ngraph steps (springs từng thắng collide), post-pass strength 1 | **53** | **6.76** |

*Vòng 1 đo sau khi worker collide chạy nhưng radii chưa áp dụng (bug cache).

Kết quả cuối: fit = speckle rời rạc có khe đen (không còn tấm xanh đặc);
6.7× = bóng tách rời + label đọc được. Suite 596/596, vue-tsc clean.

Bài học: (1) collide phải là ràng buộc TRONG simulation như grapuco — post-pass
chỉ là trang điểm; (2) d3-forceCollide cache radii lúc initialize — mọi lần đổi
bán kính phải gọi lại `.radius(accessor)`; (3) đừng "chốt" khi chưa soi lại
bằng devtools sau mỗi vòng sửa.
