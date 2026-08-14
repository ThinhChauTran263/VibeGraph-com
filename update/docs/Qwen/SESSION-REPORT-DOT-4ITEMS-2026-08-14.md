# BÁO CÁO PHIÊN 14/08/2026 — Đợt 4 việc: B-M2 gate · tách DashboardView · drill T2 · Đ7-4

- **Lệnh operator (14/08):** "làm đi và yêu cầu có bằng chứng chứ không bịa và nói suôn" — 4 việc: #3 fixture B-M2, #4 tách DashboardView, #5 commit `scripts/drills/`, #6 Đ7-4b→4e.
- **Quy ước:** mọi số dưới đây là output thật của lệnh đã chạy trong phiên; lệnh nào dùng nhiều lần ghi một lần ở đề mục. File đo tạm nằm ở `.vibegraph/d74-*` (gitignore, giữ làm bằng chứng).

---

## #3. B-M2 — GATE ĐẠT: BRANCH 80.5% (missed 152 ≤ 156)

File mới: `src/test/java/com/vibegraph/diagram/service/impl/UseCaseInferenceEngineGraphFixtureTest.java` — 5 test fixture graph chạy qua `infer()` thật (không reflection, toàn API public), mỗi test assert cấu trúc đầu ra:

| Fixture | Cơ chế phủ | Assertion chính |
|---|---|---|
| `singleDomainCollectsServicesFromAllItsControllers` | 2 controller cùng domain + INJECTS (kể cả blank-owner, null edge) | 1 uc duy nhất; `useCaseServices` = union 2 service; orphan không lọt |
| `sameScopeNameCollisionMergesOntoLowestId` | singularize mất thông tin: domain "Die"/"Dy" cùng pluralize → "Dies" | merge về đúng 1 uc id `UC_ManageDies`; services union; association trùng khử còn 1 |
| `crossScopeCollisionIsDisambiguatedNotMerged` | admin vs non-admin cùng tên | "(All)"/"(Own)" đúng phía; không còn tên trần; đúng 1 generalization |
| `authEndpointsBecomeGuestGoals` | register/login path | Guest + 2 goal auth; Admin/User tồn tại |
| `malformedInputsAreSkippedNotFatal` | route id vô dạng, HANDLES_ROUTE null/không-Controller, INJECTS blank/null | goal hợp lệ vẫn sinh ("View Valids"); `useCaseServices` rỗng |

**Trung thực quá trình:** 2 assertion đầu sai do kỳ vọng của tôi lệch hành vi thật — domain "Valid" bị pluralize thành "Valids"; đã sửa test ghim hành vi thật. Không sửa production code.

| Chỉ số | Trước phiên | Sau phiên |
|---|---|---|
| BRANCH `UseCaseInferenceEngine` | 69.7% (544/780, missed 236) | **80.5% (628/780, missed 152)** |
| LINE | 86.6% | **96.8% (658/680)** |
| Suite backend | 1062/0/0/1 | **1067/0/0/1 · BUILD SUCCESS** |

**GATE ĐẠT (missed 152 ≤ 156). KHÔNG tách class trong phiên này** — gate là điều kiện của bước tách; bước tách để phiên sau (kế hoạch §2.1 báo cáo trước vẫn nguyên giá trị). Lệnh đo: `Remove-Item target\surefire-reports -Recurse -Force; mvnw -B -DskipITs test` rồi awk cột $6/$7 trên `target/site/jacoco/jacoco.csv`.

---

## #4. Tách DashboardView.vue — ĐẠT cả 4 số đo nghiệm thu

**Cách tách (2 bước, rủi ro thấp):**
1. `dashboard-transforms.ts` — 12 hàm thuần + 6 type + `MINUTE_MS` (~119 dòng) tách khỏi view, không i18n/store/reactive.
2. `dashboard-echarts.ts` — đăng ký echarts + export `VChart`; view nạp qua `defineAsyncComponent(() => import('./dashboard-echarts'))` → echarts (~500 kB) rời chunk route.

| Số đo nghiệm thu | Kết quả |
|---|---|
| `lines.pct` cluster file tách | **76.74%** (221/288) ≥ baseline 71.52% — DashboardView 72.65% (178/245), transforms **100%** (43/43) |
| Tổng byte `dist/assets/*.js` | 1.570.230 B vs mốc 1.569.955 B = **+275 B (+0.018%)** ≤ 3% |
| Chunk `DashboardView-*.js` | **582.351 B → 21.10 kB (21.606 B)** — giảm; echarts chuyển sang chunk async `dashboard-echarts-*.js` 561.43 kB |
| Suite frontend | **66 file / 547 test pass** (trước: 65/538) · type-check xanh · `npm run build` xanh |

**Debug trung thực (3 vòng):** spec fail sau khi VChart thành async — (1) lỗi thật không hiện trong log chuẩn; probe spec với `errorHandler` bắt được: *vitest mock namespace thiếu export nội bộ* (`__isTeleport`) khi Vue interop probe module mock; fix = thêm `__esModule: true` vào factory mock `../dashboard-echarts`; (2) test 2-dashboard-mount cần settle giữa 2 lần mount. Mock VChart chuyển từ inline template sang `vi.hoisted` (factory bị hoist trước const). Probe spec đã xóa sau khi dùng xong. Thêm `dashboard-transforms.spec.ts` (9 test pure) — 1 assertion sai ban đầu (`latestSeriesMonth` giữ nguyên field `day` của label dạng ngày) đã sửa ghim hành vi thật.

---

## #5. `scripts/drills/` — drill T2 thành tài sản tái chạy

- `scripts/drills/nginx-t2-drill.conf` — conf proxy TLS (8443 → backend, `X-Forwarded-Proto https`).
- `scripts/drills/README.md` — trình tự đầy đủ: gen cert trong container alpine (nginx:alpine không có openssl), login HTTPS kiểm tra cờ Secure, roundtrip, teardown; kèm mô tả trạng thái FAIL đã đo (không có header thì không có cờ Secure vì `AUTH_COOKIE_SECURE=false`).
- Bản nháp đầu của README có lệnh sai trình tự (openssl trước mkdir, `docker exec nginx` sau khi container đã exit) — tự phát hiện và sửa thành chuỗi 1 lệnh đúng trước khi coi là xong.

---

## #6. Đ7-4 — tách `getFullGraph` 2 query: 5/5 tiêu chí ĐẠT, kèm 1 phát hiện ngược kỳ vọng

**Code:** `Neo4jGraphRepository.getFullGraph` viết lại — 2 query (`RETURN n` riêng, edges riêng) trong **một `session.executeRead`** (snapshot nhất quán, theo đúng phương án EXEC-2 ưu tiên); giữ defensive filter edge thiếu đầu mút + `log.warn` (không im lặng); `nodeStats`/`edgeStats`/`stableNodeId`/`stableEdgeId` giữ nguyên cơ chế. Suite unit **1067/0/0/1**; IT thật **Neo4jGraphRepositoryIT 19/19 pass** (Testcontainers, 31s).

**Đo runtime trên dev stack** (2 project `rt-exec-run1/run2`, auth account rt-exec, endpoint `/api/projects/{id}/graph`):

| Tiêu chí | Bằng chứng |
|---|---|
| **Đ7-4b** tỷ lệ mới = 1.0 | cypher-shell: rows(query cũ)=42.057 vs nodes=16.010 + edges=31.495; 2 query mới trả đúng 1 dòng/node và 1 dòng/edge |
| **Đ7-4c** tính đúng đắn | so JSON trước/sau (script `d74-compare.js`): sorted node-id set **bằng tuyệt đối** (5000/5000), sorted edge-id set **bằng tuyệt đối** (10.930/10.930 deep, 10.132/10.132 baseline), `meta` giống hệt (`totalNodes:16010, totalEdges:31495…`), `nodeStats`/`edgeStats` **giá trị bằng tuyệt đối** (chỉ khác thứ tự key trong map — bản chất LinkedHashMap theo thứ tự gặp, không phải dữ liệu) |
| **Đ7-4d** node cô lập | 434 node cô lập/project (đo cypher-shell); membership trong payload trước=sau (baseline 11/11, deep 0/0 — deep 0 do cap 5000 cắt cả trước lẫn sau, không phải mất do query); truy vấn node riêng trả đủ 16.010 = nodes query bao gồm toàn bộ node cô lập |
| **Đ7-4e** thời gian | cùng protocol 5 lần (1 cold + 4 cache): **cold 5.072s → 1.334s** (run1) và **1.734s → 0.778s** (run2); median cache ≈ nhau (0.101/0.102; 0.071/0.077) |

**PHÁT HIỆN NGƯỢC KỲ VỌNG (phải nói thẳng):** tổng số dòng truyền **TĂNG** chứ không giảm: query cũ 42.057 dòng vs 2 query mới 16.010 + 31.495 = **47.505 dòng (+13%)**. Nguyên nhân toán học: query OPTIONAL MATCH cũ mỗi dòng đã mang (n, r, m); tách ra thì query edges vẫn mang cả 2 đầu mút mỗi dòng, cộng thêm query nodes chép lại mọi node một lần nữa. Framing "giảm k× dòng" của audit chỉ đúng về *số lần lặp của từng node*, không đúng về *tổng dòng*. **Lợi ích thật đo được là độ trễ endpoint (cold giảm ~3.8×)** — 2 index-scan đơn giản rẻ hơn OPTIONAL-MATCH join — chứ không phải băng thông. Khuyến nghị: (a) giữ bản tách (latency + ngữ nghĩa rõ hơn, mọi tiêu chí EXEC đạt); (b) nếu mục tiêu là băng thông, việc đúng là trả về **id + props tối thiểu** ở dòng edge thay vì full node — đổi mới hoàn toàn, cần EXEC riêng; (c) cập nhật câu chữ AUDIT-REPORT mục này ở phiên docs gần nhất.

---

## Trạng thái cuối phiên (đo thật)

| Chỉ số | Giá trị |
|---|---|
| Backend | suite 1067/0/0/1 · IT Neo4j 19/19 · container rebuild + healthy với code mới |
| Frontend | 66 file/547 test · type-check + build xanh · dist tổng 1.570.230 B |
| `UseCaseInferenceEngine` | BRANCH 80.5% — **gate đạt, chưa tách (đúng kỷ luật)** |
| DashboardView cluster | 76.74% ≥ 71.52 baseline · chunk 21.10 kB |
| Code production đổi | Backend: **1 file sửa** (Neo4jGraphRepository.getFullGraph). Frontend: 1 file sửa (DashboardView.vue) + 2 module mới (transforms, echarts). Còn lại toàn bộ là test/script/docs |

**Còn mở:** tách class `UseCaseInferenceEngine` (gate đã đạt, chờ phiên sau); tách `UserDetailDrawer.vue` (chưa đủ gate 70%); xoay 7 secret (🔴, ngoài tầm code); volume drill + registry CD; `npx gitnexus analyze` sau khi operator commit.

---

*Báo cáo soạn 14/08/2026. Các phiên sau mở file mới, không sửa file này.*
