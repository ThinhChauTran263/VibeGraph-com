# BÁO CÁO TRIỂN KHAI — ĐỢT 2 + ĐỢT 3 (hoàn tất)

> Ngày: 12/08/2026. Nguồn sự thật: `AUDIT-REPORT.md`, `REMEDIATION-PLAN.md`, `FIX-DETAILS-BACKEND.md`, `FIX-DETAILS-FRONTEND-DEVOPS.md`, `IMPLEMENTATION-PROMPT.md`.
> Báo cáo này KHÔNG sửa tài liệu gốc — chỉ ghi nhận kết quả triển khai và phán xử của operator.

---

## 1. Trạng thái từng mã

### Đợt 2 — Backend logic & hiệu năng (16 mã: H6–H9, B-M1–B-M11, B-M13)

| Mã | Trạng thái | Ghi chú / bằng chứng trong code |
|---|---|---|
| H6 | ✅ Đã sửa (trước đó) | `ProjectServiceImpl` — Postgres ownership là nguồn sự thật name/status |
| H7 | ✅ Đã sửa (trước đó) | Full 128-bit UUID + chống trùng; không còn `substring(0, 8)` (grep xác nhận) |
| H8 | ✅ Đã sửa (trước đó) | `ProjectAnalysisScheduler` + `202 Accepted` ở Project/Import/Local controller |
| H9 | ✅ Đã sửa (trước đó) | `AdminService` batch `findAllById` + `sumStorageByOwners` (2 query/trang) |
| B-M1 | ✅ Đã sửa (trước đó) | `instantOrNull` có `log.warn` (Neo4jGraphRepository) |
| **B-M2** | ⏸ **BACKLOG** | Phán xử operator 12/08/2026 — xem mục 4 |
| B-M3 | ✅ Đã sửa (trước đó) | `MethodVisitor` nhận flag qua constructor từ Spring config |
| **B-M4** | ✅ **Sửa phiên này** | Enum `AccountStatus` mới + validate plan qua bảng `plans` (`existsByCode`) |
| B-M5 | ✅ Đã sửa (trước đó) | `FileChangeBroadcaster` diff theo file slice (`getFileSlice`) |
| B-M6 | ✅ Đã sửa (trước đó) | `LlmUseCaseRefiner` cache Caffeine maximumSize + expireAfterWrite |
| **B-M7** | ✅ **Sửa phiên này** | `application.yaml` mặc định `com.vibegraph: INFO`; DEBUG chỉ trong `application-dev.yaml` |
| **B-M8** | ✅ **Sửa phiên này** | `seed_dev.sql` chú thích nêu đích danh `AdminBootstrapRunner` (nhánh "ghi chú" của nghiệm thu) |
| B-M9 | ✅ Đã sửa (trước đó) | `IpBlockService` `@Cacheable` TTL ngắn + evict khi admin đổi |
| **B-M10** | ✅ **Sửa phiên này** | Chi tiết mục 2 |
| B-M11 | ✅ Đã sửa (trước đó) | Upsert trong 1 write transaction + dọn graph project FAILED |
| B-M13 | ✅ Đã sửa (trước đó) | 3 file test 0 byte đã xóa; failsafe không còn chạy file rỗng |

### Đợt 3 — Frontend (10 mã: H10–H12, F-M1–F-M7)

| Mã | Trạng thái | Ghi chú / bằng chứng trong code |
|---|---|---|
| H10 | ✅ Đã sửa (trước đó) | `onScopeDispose(cancel/stop)` trong useGitHubImport/useGraphRealtime/useReportRealtime |
| H11 | ✅ Đã sửa (trước đó) | `loadUsers` guarded + `loadError` i18n banner (UsersTableView) |
| H12 | ✅ Đã sửa (trước đó) | Toàn bộ route lazy import + `manualChunks` |
| F-M1 | ✅ Đã sửa (trước đó) | 9 file dead code đã xóa (glob xác nhận 0 file) |
| **F-M2** | ✅ **Sửa phiên này** | Xóa `PROJECTS_AUTO_REFRESH_INTERVAL_MS` + khai báo `env.d.ts` + dòng `.env.example` (không còn consumer sau F-M1) |
| F-M3 | ✅ Đã sửa (trước đó) | axios + `lib/http.ts` đã gỡ |
| F-M4 | ✅ Đã sửa (trước đó) | `vi-VN` lazy qua dynamic import |
| F-M5 | ✅ Đã sửa (trước đó) | `manualChunks` tách vendor (build ra `vendor-graph`, `vendor-charts` riêng) |
| **F-M6** | ⏸ **BACKLOG** | Phán xử operator 12/08/2026 — xem mục 4 |
| F-M7 | ✅ Đã sửa (trước đó) | `GitHubImportForm.vue` dùng `t('user.import.success', …)` |

---

## 2. Chi tiết các mã sửa trong phiên này (12/08/2026)

| Mã | File đã sửa | Kiểm tra đã chạy | Kết quả | Lệch so với FIX-DETAILS (nếu có) |
|---|---|---|---|---|
| B-M4 | `auth/domain/AccountStatus.java` (mới, 25 dòng); `auth/service/AdminService.java` (+7/−3) | 4 unit test mới trong `AdminServiceTest`; `./mvnw verify` | ✅ xanh | Không — status validate qua enum, plan validate qua bảng `plans` đúng hướng sửa |
| B-M7 | `application.yaml` (+3/−1) | `./mvnw verify` | ✅ xanh | Không — `application-dev.yaml` đã có sẵn `com.vibegraph: DEBUG` |
| B-M8 | `database/seed_dev.sql` (+2/−1, chỉ chú thích) | đọc soát | ✅ đạt | Chọn nhánh "ghi chú dựa AdminBootstrapRunner"; đã nêu đích danh class + ghi chú đọc `ADMIN_*` từ `.env` |
| B-M10 | Backend: `GraphController.java` (clamp: `nodeLimit=0` hết bypass, fallback default), `GraphPayloadProperties.java` (default 5000/15000), `application.yaml`. Frontend: `runtimeConfig.ts` (`GRAPH_SAFE_NODE_LIMIT` default 0 → 3000), `graphCap.ts` (doc). Config: `.env` + `.env.example` (sửa đích danh 1 dòng VITE_GRAPH_SAFE_NODE_LIMIT, không đụng phần còn lại) | 1 test backend đổi + 2 test frontend đổi; `./mvnw verify` + vitest 533/533 | ✅ xanh | Giá trị "cap hợp lý" do triển khai chọn: **backend 5000 node / 15000 edge, frontend 3000** — đổi được qua env không cần sửa code. **Đổi ngữ nghĩa API có chủ đích:** `nodeLimit=0&edgeLimit=0` không còn nghĩa "uncapped" mà fallback về default cap (đúng nghiệm thu T7 của B-M10) |
| F-M2 | `runtimeConfig.ts` (−8), `env.d.ts` (−3), `.env.example` (−4) | `npm run type-check` + vitest + `npm run build` | ✅ xanh | Chọn nhánh "xóa" — grep toàn repo xác nhận 0 consumer |

**Test mới thêm trong phiên:** `AdminServiceTest` +4 test (reject status lạ / accept status không phân biệt hoa thường / reject plan không có trong bảng plans / accept plan bất kỳ có trong DB — chứng minh hết hardcode). Test cập nhật: `GraphControllerTest.shouldFallBackToDefaultCapWithZeroLimits` (thay test "zero = uncapped" cũ), `graphCap.spec.ts` (default > 0), `useGraphData.spec.ts` (Safe Mode truncate mặc định).

---

## 3. Nghiệm thu

| Hạng mục | Lệnh | Kết quả |
|---|---|---|
| Đợt 2 | `./mvnw verify -B` | **BUILD SUCCESS** — 1021 unit test + 71 integration test (Testcontainers Postgres/Neo4j) pass, 1 skipped có chủ đích; **JaCoCo coverage gate pass** |
| Đợt 3 | `npm run type-check` | ✅ pass |
| Đợt 3 | `npx vitest run` | ✅ **533/533 test, 64 file** |
| Đợt 3 | `npm run build` | ✅ pass — 954 module |

**Số đo bundle (sau Đợt 3, so mốc T1 = 4,17 MB / 117 module cho landing):**
- `LandingView` chunk: **30,99 kB** (gzip 8,91 kB) — không còn kéo sigma/graphology.
- `vendor-graph` (sigma + graphology): 173,79 kB — tách riêng, chỉ tải khi vào GraphView.
- `vendor-charts` (echarts): 671,37 kB — tách riêng; còn warning chunk > 500 kB (đã biết, thuộc phạm vi F-M6/tối ưu sau).

**Còn nợ đo runtime (cần server sống, theo RUNTIME-VERIFICATION-PROMPT):** chạy lại T1 (xác nhận landing không tải vendor-graph), T4 (Offline + Search hiện banner lỗi), T7 (request `nodeLimit=0&edgeLimit=0` giờ phải nhận `meta.truncated=true` với cap mặc định 5000/15000). Phần code đã sẵn sàng; phép đo cần backend + frontend đang chạy.

---

## 4. Phán xử của operator — B-M2 và F-M6

**Mâu thuẫn tài liệu:** bảng Đợt 2/Đợt 3 trong `REMEDIATION-PLAN.md` liệt kê B-M2 và F-M6, nhưng IMPLEMENTATION-PROMPT (giao việc chính thức) **không nêu tên** 2 mã này ở cả 2 đợt, và dòng "Backlog refactor" (§7 `AUDIT-REPORT.md`) xếp chính xác B-M2 + F-M6 vào backlog.

**Phán xử (operator, 12/08/2026):** IMPLEMENTATION-PROMPT không nói → **theo AUDIT-REPORT: B-M2 và F-M6 thuộc BACKLOG**, không nằm trong phạm vi Đợt 2+3.

**Hệ quả:** Đợt 2+3 coi như **hoàn tất 100% phạm vi được giao**. BACKLOG nhận thêm:
- **B-M2** — tách `UseCaseInferenceEngine.java` (1.398 dòng) thành Strategy/heuristic classes + `StringNormalizer` util; bắt buộc viết test bao phủ TRƯỚC khi tách; file ≤ ~400 dòng.
- **F-M6** — tách 10 file > 400 dòng (`UserDetailDrawer.vue` 3.202 dòng, `LandingView.vue` 2.959, …); từng file kèm test component; chunk echarts > 500 kB hiện tại có thể xử lý dọc đường.

---

## 5. Việc chưa làm ngoài phạm vi (nhắc lại)

- **BACKLOG đầy đủ** (26 mục Thấp + Trung bình còn lại) — chưa bắt đầu.
- **Đợt 0** (xoay secret + dọn git object) — thuộc operator.
- KHÔNG có commit nào được tạo (đúng luật cứng — chờ lệnh tường minh).
