# 09 — Wave 3 Handoff Brief (copy nguyên văn gửi cho model nhận Wave 3)

**Branch:** `feat/graph-zoom-invariant` — checkout và làm việc TRÊN branch này (HEAD hiện tại `3923a58`). KHÔNG tạo branch mới, KHÔNG đụng `backup-full-fixed-20260728`.
**Repo root:** `D:\Users\User\IdeaProjects\VibeGraph` · Frontend: `vibegraph-web/`

---

## 1. Đọc bắt buộc, theo đúng thứ tự (không skip)

1. `update/graph/README.md` — tổng quan + ground rules
2. `update/graph/02-SIGMA-INTERNALS.md` — **quan trọng nhất**, chứa trap chết người §2
3. `update/graph/03-ROOT-CAUSE.md` — toán học vấn đề
4. `update/graph/05-IMPLEMENTATION-PLAN.md` — đặc tả Step 2/3/4/5 mà bạn sẽ làm
5. `update/graph/07-OPEN-QUESTIONS.md` — Part B (traps), §A1 đã VERIFIED (WebGL picking dùng scaled size → không cần fix bù)
6. `update/graph/01-EVIDENCE-LOG.md` — tra cứu `file:line` khi cần
7. `update/graph/08-TASK-ASSIGNMENT.md` — bảng task tổng

## 2. ĐÃ XONG — không làm lại, không revert

| Việc | Bằng chứng |
|---|---|
| T3–T7 (Step 1): `ZOOM_SIZE_POWER = 1.0` tại `useSigma.ts:83`; `SIGMA_EDGE_SIZE = 0.02, {min: 0.005}` tại `runtimeConfig.ts:176`; `maxCameraRatio` clamp trong Sigma options; guard comment cạnh `itemSizesReference` | commit `b3b6455`, `b61b6a3` |
| T1: hit-testing đã verify dùng scaled size (WebGL PICKING_MODE) | commit `b353dd7`, ghi trong 07 §A1 |
| T12: `vibegraph-web/src/composables/__tests__/settleScreenOverlaps.spec.ts` — 5/5 pass | commit `3923a58` |
| Backend (T6a/T6b) đã commit riêng — KHÔNG đụng `src/main/java/**` | commit `04874ab` |

## 3. TASK CỦA BẠN (Wave 3)

### T8 — Fix đơn vị noverlap (Step 2) — task khó nhất, làm trước
- **Đo `K` runtime, KHÔNG hardcode** (`05` §2.1, `07` §A2):
  ```ts
  const a = sigma.graphToViewport({ x: 0, y: 0 })
  const b = sigma.graphToViewport({ x: 1, y: 0 })
  const pxPerGraphUnit = Math.hypot(b.x - a.x, b.y - a.y)
  ```
  Tái sử dụng logic `unitsPerPixel` trong `settleScreenOverlaps` (`useSigma.ts` ~:792) — KHÔNG viết phiên bản thứ hai. Ghi giá trị K đo được vào report.
- **Chọn 1 trong 2** (`05` §2.2): (a) đổi size sang graph units trước khi đưa noverlap (`ratio: 1`, margin bằng graph units); hoặc (b) bỏ hẳn `graphology-noverlap`, để `settleScreenOverlaps` làm toàn bộ với nhiều iteration hơn. Ghi rõ quyết định + lý do vào commit message.
- Tính lại `NOVERLAP_MARGIN`/`NOVERLAP_RATIO` (40/2.7 tại `runtimeConfig.ts:289–290`) — giá trị cũ tune trên hệ đơn vị hỏng.
- ⚠️ **Nếu chọn (b): BẮT BUỘC cập nhật `settleScreenOverlaps.spec.ts`** vì spec này drive qua seam noverlap → settle. Spec phải xanh trước khi commit.

### T9 — Cắt 21–32 s "Finalizing" (Step 3) — sau T8
- Hạ mạnh `NOVERLAP_AUTO_STOP_MS` (`runtimeConfig.ts:292`, hiện 22000).
- Dùng `onConverged` (đã wire ~`useSigma.ts:539`) làm exit chính, timer chỉ fallback.
- Nếu T8 chọn bỏ noverlap, task này gần như tự xong — vẫn phải verify số đo.

### T10 — Filter không restart layout (Step 4) — độc lập T8/T9
- Vấn đề: `init(graph)` dispose → new Sigma → `startLayout` → FA2 restart → node nhảy khi toggle filter.
- Mục tiêu: vị trí node đóng băng hoàn toàn khi filter (như grapuco, `04` §6).
- Hướng: hide/show qua node/edge reducers (`setReducers` ~`useSigma.ts:948–965`) thay vì rebuild + re-layout. `settleScreenOverlaps` đã biết `filterHidden` (~:768).
- Cẩn thận interaction với `positionCache` (~:265).
- Solo/isolate UX: optional, chỉ làm nếu còn sức.

### T11 — Xóa knob chết + sửa comment sai (Step 5) — độc lập, risk very low
- Xóa 4 knob (đã verify không referenced — `01` §5): `FA2_ITERATIONS` (:208), `FA2_ITERATIONS_LARGE` (:254), `FA2_OUTLIER_CLAMP_PERCENTILE` (:238), `NOVERLAP_MAX_ITERATIONS` (:291) trong `runtimeConfig.ts`.
- Sửa 3 comment sai đã chứng minh (`01` §4): `runtimeConfig.ts:207`, `:211–215`, `:258–259`.
- Kiểm tra `.env` / `.env.example` có khai báo `VITE_FA2_ITERATIONS`… thì dọn luôn.

### T15 — QA Step 2–3
- Đo lại settle time sau fix (baseline: 21 s / 32 s — `01` §6.1), ghi số đo vào report.
- Screenshot nhiều mức zoom sau fix, lưu `update/graph/baseline/` đặt tên `2026-08-XX-after-*.png`.

## 4. KỶ LUẬT BẮT BUỘC

1. **Trước khi sửa mọi function/class:** chạy `gitnexus_impact({target: "<symbol>", direction: "upstream"})` (repo rule). Nếu risk HIGH/CRITICAL → dừng, report lead.
2. **Trước mỗi commit:** chạy `gitnexus_detect_changes()`, xác nhận chỉ đụng symbol mong đợi.
3. Commit theo conventional commits, kèm task ID: `feat(graph): ... [T8]`, `perf(graph): ... [T9]`, `refactor(graph): ... [T11]`…
4. Test: `cd vibegraph-web && npx vitest run` — **toàn bộ suite phải xanh**, không chỉ file của bạn.
5. Mỗi kết quả phải có BẰNG CHỨNG: output test thật, số đo thật, screenshot thật, commit hash. Không báo cáo suông, không bịa số liệu.

## 5. TRAPS — vi phạm = vứt cả ngày

- 🔴 KHÔNG đổi `itemSizesReference` → `'positions'` (node phình ~100×, không đổi zoom scaling — `02` §2)
- 🔴 KHÔNG tin comment trong `runtimeConfig.ts` (3 chỗ sai đã chứng minh)
- 🟠 KHÔNG chỉnh 4 knob chết "để xem sao" (T11 xóa chúng)
- 🟠 KHÔNG cắt edge types / ẩn Field nodes (không phải nguyên nhân — `03`)
- 🟠 KHÔNG copy label strategy của grapuco (`07` B9)

## 6. RANH GIỚI — tránh giẫm chân

- **Bạn chỉ sửa:** `vibegraph-web/src/**` (chủ yếu `useSigma.ts`, `runtimeConfig.ts`, tests)
- **KHÔNG đụng:** `src/main/java/**` (backend — lead đang làm T6c), `update/graph/01–07` (chỉ đọc + append kết quả đo vào §A2 của 07 nếu cần)
- Lead (Qwen 3.8 Max) đang chạy song song: T14 QA visual Step 1 + T6c backend. Nếu T14 phát hiện fit view bất thường, lead sẽ flag trên branch — khi đó dừng T8, chờ sync.

## 7. ĐỊNH NGHĨA XONG (mỗi task)

- Code + test xanh (`npx vitest run` toàn suite)
- `gitnexus_detect_changes` sạch phạm vi
- Commit riêng từng task kèm bằng chứng
- Report cuối: quyết định T8 (a hay b), giá trị K đo được, settle time trước/sau, danh sách commit hash

## 8. Escalation

Gặp một trong các tình huống sau → DỪNG, report, không tự chế:
- Quyết định T8(a)/(b) không rõ sau khi đo
- Fit view trông khác thường (so `update/graph/baseline/2026-08-18-before-fit.png`)
- Còn collision sau khi tăng iteration đáng kể
- Test suite đỏ mà không hiểu vì sao
