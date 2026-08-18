# 08 — Task Assignment: Graph Upgrade

**Lead:** Qwen 3.8 Max · **Date:** 2026-08-17
**Sources:** `update/graph/01–07` (đọc toàn bộ trước khi nhận task)
**Impact check (quy tắc repo):** `gitnexus_impact({target: "useSigma", direction: "upstream"})` → **LOW risk**. Chuỗi ảnh hưởng: `useSigma` ← `GraphCanvas.vue` (d=1) ← `GraphView.vue` (d=2). Không đụng process/module khác.

---

## 0. Kỷ luật chung — BẮT BUỘC ĐỌC

### 0.1 Branch & commit
- **Hiện trạng:** HEAD đang ở `backup-full-fixed-20260728` (branch backup). **KHÔNG commit code lên branch này.**
- **Lead tạo** branch `feat/graph-zoom-invariant` từ HEAD trước khi Wave 1 chạy (task **L2**). Mọi code chỉ commit lên branch này.
- **Step 1 (T3–T7) = 1 commit duy nhất** — không tách. Tách ra sẽ khiến graph "trông hỏng" giữa chừng (`05` §Step 1).
- Trước mỗi commit: chạy `gitnexus_detect_changes()` (quy tắc repo).
- Mỗi commit message theo conventional commits (`feat:`/`fix:`/`perf:`/`refactor:`/`chore:`), tham chiếu task ID (ví dụ `feat(graph): zoom-size power 1.0 [T3-T7]`).

### 0.2 Trap list — vi phạm = vứt bỏ cả ngày làm việc (`07` Part B)
| # | KHÔNG ĐƯỢC | Vì sao |
|---|---|---|
| B1 | Đổi `itemSizesReference` → `'positions'` | Node phình ~100×, không đổi zoom scaling (`02` §2) |
| B2 | Ship `p=1.0` mà chưa hạ `SIGMA_EDGE_SIZE` + floor `{min: 0.05}` | Edge dày lên từ ~11× zoom, fix "có vẻ không chạy" |
| B3 | Tin comment trong `runtimeConfig.ts` | 3 chỗ sai đã chứng minh (`01` §4) |
| B4 | Chỉnh 4 knob chết | Không có tác dụng (`01` §5) |
| B5 | Ẩn node `Field` "cho đỡ rối" | Đã ẩn mặc định rồi |
| B6 | Cắt bớt edge types | VibeGraph render ÍT node hơn grapuco mà vẫn rối hơn → không phải vấn đề mật độ |
| B7 | Bỏ single-transaction Neo4j write | Atomicity có chủ đích (B-M11) |
| B10 | `.parallelStream()` vòng lặp parse | `JavaParser` dùng chung không thread-safe |

### 0.3 Định nghĩa "xong" chung cho mọi task code
- `gitnexus_detect_changes()` sạch phạm vi mong đợi trước commit
- Unit test mới (repo chuẩn 80%) nếu phần đó unit-testable
- QA (T14–T16) pass checklist tương ứng
- Lead review trước khi merge nội bộ

---

## 1. Bảng phân công tổng quan

| ID | Task | Owner | Escalate | Wave | Phụ thuộc | Risk |
|----|------|-------|----------|-------|-----------|------|
| **T1** | Verify hit-testing Sigma (Step 0.1) — **BLOCKING** | **DeepSeek V4 Pro** | — | **1** | — | — |
| **T2** | Baseline capture + QA harness scripts (Step 0.3) | **MiniMax M3** | — | **1** | — | — |
| **T6a** | Backend F2: bỏ double file read | **GLM-5.2** | — | **1** | — | Low |
| **T6b** | Backend F4: progress thật khi DB write | **GLM-5.2** | — | **1** | — | Low |
| **L2** | Tạo branch + commit convention | Lead | — | **1** | — | — |
| **T3** | Step 1.1: `ZOOM_SIZE_POWER` 0.75 → 1.0 | **DeepSeek V4 Pro** | Kimi K3 | **2** | T1 | Low |
| **T4** | Step 1.2: `SIGMA_EDGE_SIZE` → 0.02 + hạ floor | **Kimi K2.7 Code** | Kimi K3 | **2** | T1 | Low |
| **T5** | Step 1.3: `maxCameraRatio` clamp | **Kimi K2.7 Code** | Kimi K3 | **2** | T1 | Low |
| **T7** | Step 1.4: guard comments | **Kimi K2.7 Code** | — | **2** | T3 | Low |
| **T8** | Step 2: fix đơn vị noverlap (đo K runtime) | **DeepSeek V4 Pro** | Kimi K3 | **3** | T3 | Medium |
| **T9** | Step 3: cắt "Finalizing" 21–32 s | **DeepSeek V4 Pro** | — | **3** | T8 | Low |
| **T10** | Step 4: filter không restart layout | **Kimi K3** (principal) | — | **3** | T3 | Medium |
| **T11** | Step 5: xóa knob chết + sửa comment sai | **Kimi K2.7 Code** | — | **3** | T9 | Very low |
| **T6c** | Backend F1: parallel parsing (sau cùng) | **GLM-5.2** | Kimi K3 | **4** | T6a,b + diff check | **High** |
| **T12** | Unit tests: size conversion + settleScreenOverlaps | **MiniMax M3** | Kimi K3 | **2–3** | T8 | Low |
| **T13** | CI/CD gatekeeper: lint/typecheck/test trước khi đẩy | **MiniMax M3** | — | **1** | — | Low |
| **T14** | QA visual Step 1 (checklist §3) | **MiniMax M3** | — | **2** | T3–T7 | — |
| **T15** | QA Step 2–3 + đo K | **MiniMax M3** | — | **3** | T8, T9 | — |
| **T16** | QA Step 4 + full regression | **MiniMax M3** | — | **4** | T10 | — |
| **T17** | Verify A5: renderer freeze khi dùng bình thường | **MiniMax M3** | Kimi K3 | **4** | T16 | — |
| **L1** | Review mọi PR/commit + quyết định escalate | Lead | — | liên tục | — | — |

**Ghi chú vai trò theo đúng đề bài:**
- **Kimi K3** chỉ nhận T10 (task khó nhất) + vai trò escalation cho T3/T4/T5/T8/T12/T6c — **không làm bottleneck**, không giao việc lặt vặt.
- **DeepSeek V4 Pro** ôm chuỗi sizing vì T1 → T3 → T8 → T9 cùng chạm `useSigma.ts`, cần một người giữ context xuyên file.
- **Kimi K2.7 Code** nhận chùm config độc lập, token-efficient (T4/T5/T7/T11).
- **GLM-5.2** ôm riêng workstream backend — không phụ thuộc frontend.
- **MiniMax M3** gatekeeper: mọi task muốn lên tầng cao hơn phải qua pre-check của M3.

---

## 2. Chi tiết task

### Wave 1 — Chạy NGAY, song song hoàn toàn

#### T1 — Verify hit-testing Sigma (BLOCKING) · DeepSeek V4 Pro
**Mục tiêu:** trả lời câu hỏi A1 (`07` §A1): picking/hover của Sigma so sánh với **raw `size`** hay **`scaleSize(size)`**?
**Cách làm:**
1. Trong `vibegraph-web/node_modules/sigma/dist/sigma.esm.js`, tìm quadtree / `getNodeAtPosition` / mouse-move hover path.
2. Xác định bán kính dùng để so sánh có đi qua `scaleSize()` không.
3. Xác định cơ chế nào đang active: quadtree picking hay WebGL `PICKING_MODE` (shader `index-fad77a13.esm.js:789`, `:914`).
**Output:** ghi kết quả vào `07-OPEN-QUESTIONS.md` §A1 (đổi `NOT VERIFIED` → kết luận + `file:line` bằng chứng).
**Quyết định phân nhánh:**
- Nếu dùng `scaleSize` → Wave 2 chạy bình thường.
- Nếu dùng raw `size` → **dừng Wave 2**, báo Lead; cần compensating fix cho picking trước khi ship Step 1.
**Est:** 1–2 giờ.

#### T2 — Baseline capture · MiniMax M3
**Mục tiêu:** chụp baseline trước khi đổi gì (Step 0.3, `05` §0.3).
**Cách làm:** tại `http://localhost:5173/projects/431ee9dc/graph`:
- Screenshot fit view; screenshot fit + ~11 scroll ticks.
- Đo settle time bằng polling text `"Finalizing graph layout"` biến mất. Giá trị tham chiếu: 21 s / 32 s (`01` §6.1).
- Lưu vào `update/graph/baseline/` (đặt tên file có ngày giờ).
**Lưu ý:** hiện tượng freeze CDP ở `01` §6.6 chưa xác nhận — nếu gặp, ghi nhận nhưng đừng kết luận (task T17 xử lý sau).

#### T6a — Bỏ double file read · GLM-5.2 (`06` Finding 2)
**File:** `src/main/java/com/vibegraph/parser/service/impl/ParserServiceImpl.java`
**Cách làm:** `parseFileInternal` gọi `lineCount(filePath)` (:209) đọc lại toàn bộ file chỉ để đếm dòng → lấy số dòng từ AST (`CompilationUnit` đã có range/end-line). Giữ fallback cho file parse thất bại.
**Risk:** Low. Kỳ vọng: giảm ~50% file I/O của parse phase.

#### T6b — Progress thật khi DB write · GLM-5.2 (`06` Finding 4)
**Files:** `AnalyzeServiceImpl.java:98–115`, `Neo4jGraphRepository.java:174–193`
**Cách làm:** emit progress tăng dần từ trong `upsertAnalysis` — vòng lặp đã iterate theo label group (:183–189), thêm per-group callback là đủ. Thanh progress không được đứng yên ở 94% suốt lúc ghi DB.
**KHÔNG:** đụng vào single-transaction (trap B7).

#### T13 — CI/CD pre-check · MiniMax M3
Dựng script pre-check chạy trước khi đẩy commit lên tầng review: lint + typecheck + unit test (frontend: `vibegraph-web`; backend: Maven test). Mọi task từ Wave 2 trở lên muốn merge phải xanh pre-check này.

#### L2 — Branch (Lead)
`git checkout -b feat/graph-zoom-invariant` từ HEAD hiện tại. Thông báo cho cả team trước khi Wave 2 bắt đầu.

---

### Wave 2 — Sau khi T1 pass (một commit duy nhất: T3+T4+T5+T7)

#### T3 — `ZOOM_SIZE_POWER` 0.75 → 1.0 · DeepSeek V4 Pro
**File:** `vibegraph-web/src/composables/useSigma.ts:78`
```ts
const ZOOM_SIZE_POWER = 0.75   // → 1.0
```
Đây là thay đổi lõi (`02` §3–4). Tại fit view `r=1` nên `p` không ảnh hưởng → nếu fit view đổi khác thấy rõ = có gì đó sai, **dừng và báo Lead**.

#### T4 — `SIGMA_EDGE_SIZE` 0.25 → 0.02 · Kimi K2.7 Code
**File:** `vibegraph-web/src/lib/runtimeConfig.ts:166`
```ts
export const SIGMA_EDGE_SIZE = envFloat('VITE_SIGMA_EDGE_SIZE', 0.25, { min: 0.05 })
// → 0.02 và floor { min: 0.05 } cũng phải hạ (ví dụ 0.005), nếu không 0.02 bị clamp ngược về 0.05 và fix THẦM LẶNG không áp dụng
```
Công thức: `SIGMA_EDGE_SIZE < SIGMA_MIN_EDGE_THICKNESS · M^(−p)` = 2.8/100 = 0.028 cho edge mỏng tới 100× zoom (`02` §5).

#### T5 — Zoom clamp · Kimi K2.7 Code
**File:** `useSigma.ts`, block options `new Sigma(...)` (:146–181)
```ts
maxCameraRatio: <value>,   // chặn zoom OUT
minCameraRatio: <value>,   // chặn zoom IN (optional)
```
Chọn `maxCameraRatio` bằng thực nghiệm: zoom out tới mức nhỏ nhất chấp nhận được → đọc camera ratio → dùng giá trị đó. Tham chiếu: grapuco clamp cứng zoom-out (`01` §7.4).

#### T7 — Guard comments · Kimi K2.7 Code
Tại `useSigma.ts:160`:
```ts
// DO NOT change to 'positions'. In Sigma 3.0.3 it does NOT alter zoom scaling —
// it only multiplies all sizes by a large constant (~100×+), which looks broken.
// The zoom lever is ZOOM_SIZE_POWER. See update/graph/02-SIGMA-INTERNALS.md §2.
itemSizesReference: 'screen',
```

#### T14 — QA visual Step 1 · MiniMax M3 (dùng T2 làm comparator)
Checklist (`05` §Step 1 Verification):
- [ ] Fit view **gần như không đổi** so baseline
- [ ] Zoom in sâu: node to nhanh hơn hẳn và tách nhau rõ
- [ ] Qua ~11× và ~50× zoom: edge vẫn mảnh như sợi tóc (nếu dày lên = T4 chưa ăn, kiểm tra floor)
- [ ] Zoom out: dừng tại clamp
- [ ] Click node ở nhiều mức zoom: vùng click khớp vòng tròn (được T1 bảo vệ)
Pass → Lead review + merge commit Wave 2. Fail → mở escalation.

---

### Wave 3 — Sau khi Wave 2 merge

#### T8 — Fix đơn vị noverlap (Step 2) · DeepSeek V4 Pro — **task khó nhất frontend**
1. **Đo `K` runtime, không đoán** (`07` §A2, `05` §2.1):
```ts
const a = sigma.graphToViewport({ x: 0, y: 0 })
const b = sigma.graphToViewport({ x: 1, y: 0 })
const pxPerGraphUnit = Math.hypot(b.x - a.x, b.y - a.y)
```
Tái sử dụng logic `unitsPerPixel` có sẵn trong `settleScreenOverlaps` (`useSigma.ts:792`) — không viết phiên bản thứ hai.
2. **Chọn 1 trong 2** (`05` §2.2): (a) đổi size sang graph units trước khi đưa noverlap, `ratio: 1`, margin tính bằng graph units; hoặc (b) **bỏ hẳn `graphology-noverlap`**, để `settleScreenOverlaps` (giờ đã đúng và zoom-independent) làm toàn bộ với nhiều iteration hơn. Lead nghiêng về (b) — cần dữ liệu đo từ T12/T15 để chốt.
3. **Tính lại** `NOVERLAP_MARGIN` / `NOVERLAP_RATIO` (40 / 2.7 tại `runtimeConfig.ts:289–290`) — giá trị cũ tune trên hệ đơn vị hỏng, vô nghĩa sau fix.
**Escalate Kimi K3 nếu:** quyết định (a)/(b) không rõ ràng sau khi đo, hoặc còn collision sau khi tăng iteration.

#### T9 — Cắt 21–32 s "Finalizing" (Step 3) · DeepSeek V4 Pro
Sau T8: hạ mạnh `NOVERLAP_AUTO_STOP_MS` (`runtimeConfig.ts:292`). Dùng `onConverged` (đã wire tại `useSigma.ts:539`) làm exit chính, timer chỉ là fallback. Mục tiêu cảm nhận: gần instant như grapuco (`04` §9).

#### T10 — Filter không restart layout (Step 4) · Kimi K3
**Vấn đề:** `init(graph)` (`useSigma.ts:129–257`) dispose → new Sigma → `startLayout` → FA2 restart → node nhảy khi toggle filter.
**Mục tiêu:** như grapuco — vị trí node **đóng băng hoàn toàn** khi filter (`04` §6).
**Hướng:** hide/show qua node/edge reducers (`setReducers`, `useSigma.ts:948–965`) thay vì rebuild graph. `settleScreenOverlaps` đã biết `filterHidden` (:768) — khái niệm đã tồn tại.
**Cẩn thận:** interaction với `positionCache` (:265).
**Optional UX (chỉ làm nếu còn sức):** solo/isolate semantics như grapuco — click 1 type = chỉ hiện type đó.

#### T11 — Dọn knob chết + comment sai (Step 5) · Kimi K2.7 Code
1. Xóa (hoặc wire-up — mặc định là **xóa**) (`01` §5):
   - `FA2_ITERATIONS` (`runtimeConfig.ts:208`)
   - `FA2_ITERATIONS_LARGE` (:254)
   - `FA2_OUTLIER_CLAMP_PERCENTILE` (:238) — nguy hiểm nhất: comment mô tả hành vi không tồn tại
   - `NOVERLAP_MAX_ITERATIONS` (:291)
2. Sửa 3 comment sai đã chứng minh (`01` §4): `runtimeConfig.ts:258–259`, `:211–215`, `:207`.
**Risk:** Very low — nhưng kiểm tra `.env` / `.env.example` xem có khai báo các biến env tương ứng để dọn luôn.

#### T12 — Unit tests · MiniMax M3 (hỗ trợ DeepSeek)
- Pure math: size → graph-unit conversion (T8) — không cần browser.
- `settleScreenOverlaps` với synthetic node sets → assert **zero remaining collisions** (hiện chưa có assertion này ở đâu — `05` §Testing).
- Đặt trong `vibegraph-web/src/composables/__tests__/` và `src/lib/__tests__/` theo cấu trúc sẵn có.

#### T15 — QA Step 2–3 · MiniMax M3
Đo lại settle time sau fix (so baseline 21–32 s). Đo K tại runtime, ghi vào `07` §A2. Screenshot nhiều mức zoom sau fix.

---

### Wave 4

#### T6c — Parallel parsing (Finding 1) · GLM-5.2 — **chỉ chạy khi tất cả đã xanh**
**KHÔNG** `.parallelStream()` ngây thơ (trap B10). Đánh giá 2 hình an toàn (`06` §Finding 1):
- 1 `JavaParser` per worker thread trên shared type solver (phải xác nhận thread-safety của type solver)
- Partition files, mỗi thread parser độc lập (tốn RAM, mất cache chung)
**Bắt buộc:** validate bằng cách **diff node/edge counts** giữa run song song và run tuần tự trên cùng repo trước khi ship.
Kiểm tra thêm: `detectSourceRoots` có thể là lần đọc file thứ ba (`07` §A6).

#### T16/T17 — Full regression + verify A5 · MiniMax M3
- T16: full checklist regression: visual separation mọi mức zoom, edge thickness deep zoom, hit-testing deep zoom, toggle filter không làm node di chuyển.
- T17: tái hiện freeze bằng **tương tác bình thường** (scroll/click thật, không synthetic wheel burst) để xác nhận hoặc loại trừ A5 (`01` §6.6). Nếu có freeze thật → escalate Kimi K3.

---

## 3. Luồng phối hợp & escalation

```
Wave 1 (song song):  T1 [DeepSeek] ─┬─→ Wave 2: T3+T4+T5+T7 (1 commit) → T14 QA → Lead merge
                   T2 [M3] ─────────┘                     │
                   T6a+T6b [GLM] (song song, độc lập)     │
                   T13 [M3] ─────────────────────────────┴─ gatekeeper mọi wave sau

Wave 3:  T8 → T9 [DeepSeek]  ·  T10 [Kimi K3]  ·  T11 [Kimi K2.7]  ·  T12 [M3]
         → T15 QA → Lead merge

Wave 4:  T6c [GLM] (chỉ khi tất cả xanh)  ·  T16/T17 [M3]
```

**Escalation path:** Member gặp khó → MiniMax M3 pre-check/log → nếu chưa rõ → **Kimi K3** → nếu liên quan kiến trúc/quyết định scope → **Lead**.

**Quy tắc chặn:**
- T1 ra kết quả "picking dùng raw size" → dừng Wave 2, Lead quyết định compensating fix.
- Fit view đổi khác sau Wave 2 → dừng, điều tra trước khi đi tiếp (`05` §Step 1 Verification).
- Bất kỳ ai định đổi `itemSizesReference` → người đó phải đọc lại `02` §2 trước, Lead xác nhận.

---

## 4. Open questions còn lại (không block, theo dõi)

| ID | Câu hỏi | Xử lý |
|----|---------|-------|
| A2 | Giá trị thực của `K` | Đo tại T8/T15 |
| A3 | grapuco có precompute layout server-side? | Chỉ là suy luận — không thiết kế dựa trên giả định này |
| A5 | Renderer freeze khi dùng bình thường? | T17 |
| A6 | Chi phí inference passes + `detectSourceRoots` | T6c, chỉ đo nếu cần |

---

## 5. Ghi chú Lead

- **Nếu chỉ đủ ngân sách làm 1 thứ: làm Wave 2 (Step 1).** Đây là thay đổi biến vấn đề từ "không thể giải" thành "giải được một lần, đúng ở mọi zoom" (`05`).
- Tài liệu `01–07` chất lượng rất cao, mọi claim có `file:line` — **không làm lại research**, chỉ verify các mục `⚠️ UNVERIFIED` được giao.
- So sánh với grapuco chỉ dùng làm **mục tiêu tham chiếu**, không copy máy móc (đặc biệt: không copy label strategy — B9).


---

## 6. Execution Log (Lead cập nhật)

| Ngày | Sự kiện |
|------|---------|
| 2026-08-17 | Lập kế hoạch + tạo branch `feat/graph-zoom-invariant` (L2 ✅) |
| 2026-08-17 | **T1 ✅** — Hit-testing VERIFIED: Sigma dùng WebGL framebuffer picking, geometry pick buffer identical với vòng tròn vẽ (cùng `sizeRatio` từ `scaleSize`). Không cần compensating fix. Ghi vào `07` §A1. |
| 2026-08-18 | **T2 ✅** — Baseline: project gốc `431ee9dc` đã bị purge → re-import cùng source tạo `2c67c31c-b65d-42ba-b128-43cf88501339`. Settle 32s, overlap xác nhận. Screenshots trong `baseline/`. ⚠️ T14/T15 phải dùng đúng project ID này. |
| 2026-08-18 | **T6a ✅** — `endLineOf(cu, filePath)` từ AST, `lineCount` giữ làm fallback. |
| 2026-08-18 | **T6b ✅** — `UpsertProgressListener` + wiring 94→98% trong `AnalyzeServiceImpl`. Tests xanh. |
| 2026-08-18 | **T13 ✅** — `update/graph/scripts/precheck.ps1`. Phát hiện lint lỗi có sẵn: `DashboardView.vue:22 ChartTone unused`. |

**Đang chạy:** Wave 2 (T3+T4+T5+T7) + fix ChartTone lint.
