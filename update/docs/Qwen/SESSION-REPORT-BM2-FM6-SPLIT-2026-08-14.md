# BÁO CÁO PHIÊN 14/08/2026 (tiếp) — Đóng 2 refactor cuối của kế hoạch Qwen: B-M2 tách · F-M6 UserDetailDrawer

- **Lệnh operator:** "làm đi... yêu cầu kết quả phải có test thật có bằng chứng không bịa đặt nói suôn hay đoán mò". Về "dùng subagent": môi trường này chỉ có subagent Browser/CodeReview/ComputerUse/Debug — **không có subagent viết code**, nên toàn bộ làm trực tiếp, ghi rõ để không hiểu nhầm là đã ủy quyền.
- **Quy ước:** mọi số là output thật của lệnh đã chạy trong phiên.

---

## 1. B-M2 — TÁCH XONG `UseCaseInferenceEngine`, đủ 3 tiêu chí EXEC §10

### 1a. Số accuracy TRƯỚC tách (bắt buộc đo trước khi đụng code)
`mvnw -Dtest=UseCaseAccuracyEvalTest test` → `bm2-accuracy-before.log`, report nguyên văn:
```
Actors     P=1.00 R=1.00 F1=1.00  (tp=3 fp=0 fn=0)
UseCases   P=1.00 R=1.00 F1=1.00  (tp=7 fp=0 fn=0)
Relations  P=1.00 R=1.00 F1=1.00  (tp=8 fp=0 fn=0)
```

### 1b. Cách tách — 6 collaborator package-private, code chuyển NGUYÊN VĂN
| File mới | Dòng | Nội dung |
|---|---|---|
| `UseCaseNameNormalizer.java` | 130 | string helpers thuần (singularize/pluralize/pascal/uniqueId…) |
| `UseCaseEndpointRules.java` | 277 | record `Endpoint` + thu thập endpoint + exclusion |
| `UseCaseDomainGuesser.java` | 146 | record `DomainGuess` + `DomainAgg` + suy luận domain (R3) |
| `UseCaseActorGuesser.java` | 127 | record `ActorGuess` + `AuthKind` + suy luận actor + auth |
| `UseCaseClassFallback.java` | 340 | fallback tầng class khi không có HTTP |
| `UseCaseModelMerge.java` | 151 | merge trùng tên + disambiguate (All)/(Own) |
| `UseCaseInferenceEngine.java` (orchestrator) | **1.398 → 387** | `infer()` + wiring + 4 delegator mỏng giữ bề mặt reflection của test |

Test helper (`UseCaseInferenceEngineHelperTest`) cập nhật điểm reflection theo vị trí mới — **toàn bộ assertion giữ nguyên**, chỉ đổi nơi trỏ tới.

### 1c. Nghiệm thu (3 tiêu chí EXEC §10, đo thật sau tách)
| Tiêu chí | Kết quả đo |
|---|---|
| File gốc ≤ 400 dòng | **387 dòng** (`Get-Content | Measure-Object`) |
| Số accuracy trước/sau bằng nhau | `Compare-Object` trên 18 dòng report trước/sau → **IDENTICAL**; eval 6/6 pass |
| BRANCH tổng các file con ≥ baseline | **cluster 80.8% (629/778, missed 149)** ≥ baseline 80.5% (missed 152) — đo awk jacoco.csv, từng file con: Engine 90.6 / EndpointRules 75.8 / DomainGuesser 82.4 / ActorGuesser 86.5 / ClassFallback 73.3 / NameNormalizer 93.6 / ModelMerge 81.8 |
| Suite backend | **1067/0/0/1 · BUILD SUCCESS** (2 lần: giữa và sau tách) |

---

## 2. F-M6 — `UserDetailDrawer.vue`: gate + tách (phạm vi an toàn, ghi rõ giới hạn)

### 2a. Gate 70% — đạt, vượt xa
- Trước: `lines.pct = 65.00%` (đo phiên trước, full suite).
- Viết thêm **14 test** vào `UserDetailDrawer.spec.ts` (quota save/fail-close/error, credit limit, credit adjust + reject zero, plan combobox chọn PRO + save + error, block/deactivate qua reason dialog, unblock qua confirm dialog, non-Error rejection fallback, toggle API-key creation, lock key + refresh, expired/deleted status). 1 test fail ban đầu do tôi đoán sai selector plan menu — sửa theo DOM thật (`#adminUserPlan` + `.plan-select-option`), không đoán lại.
- Sau (full suite): **`UserDetailDrawer lines.pct = 95.35%` (267/280)** ≥ 70 gate. Suite 562/562.

### 2b. Tách — và nơi DỪNG có chủ đích
- **Đã tách:** `user-detail-format.ts` (88 dòng) — 11 hàm formatter/helper (quota math, user/api-key status, formatDate, lockedMeta) chuyển nguyên văn; i18n nhận qua tham số `t`/locale thay vì closure; view giữ wrapper mỏng, **template không đổi** → zero rủi ro giao diện. Kèm `user-detail-format.spec.ts` 8 test pin mọi nhánh.
- **Chưa tách (nói thẳng):** template + ~2.270 dòng CSS. Lý do kỹ thuật kiểm chứng được, không phải ngại việc: CSS scoped của UDD cho khối api-keys nằm rải 8 vùng (L1150, L1430–1509, L1642, L2058–2066…) và dùng chung class với khối khác (`.empty-state`, `.table-shell`, `.mono`). Chuyển template sang component con mà không chuyển đúng các rule đó = **mất style chắc chắn xảy ra** (scoped attribute không khớp), và phiên này không có xác minh trình duyệt để chịu trách nhiệm cho phần nhìn. Đây là việc cần drill trình duyệt (như T2) — đề xuất phiên riêng.

### 2c. Nghiệm thu F-M6 (số thật)
| Số đo | Kết quả |
|---|---|
| `lines.pct` cluster (UDD + format) | (246+32)/(259+32) = **95.53%** ≥ baseline 65% và ≥ 95.35% trước tách module |
| Tổng byte `dist/assets/*.js` | **1.570.469 B** vs mốc 1.570.230 B = **+239 B (+0.015%)** ≤ 3% |
| Suite frontend | **67 file / 570 test pass** · type-check xanh · build xanh |

---

## 3. Trạng thái kế hoạch Qwen — sau phiên này

| Hạng mục gốc | Trạng thái |
|---|---|
| Đợt 0–3 (76 phát hiện H/C/S/M/L) | ✅ xong + verify độc lập (phiên 12–13/08) |
| EXEC Đợt 4–7 (Lô A/B/C, Đ7-1 đo, Đ7-2, Đ7-3, Đ7-4, H16…) | ✅ xong; Đ7-1d–1f DỪNG có QĐ#3; Đ7-4b–4e đã làm 14/08 |
| **B-M2** (gate + tách `UseCaseInferenceEngine`) | ✅ **đóng hoàn toàn phiên này** |
| **F-M6** DashboardView | ✅ đóng 14/08 (báo cáo DOT-4ITEMS) |
| **F-M6** UserDetailDrawer | 🟡 **logic + gate đóng**; tách template/CSS chờ phiên có drill trình duyệt |
| Ngoài code: xoay 7 secret 🔴, commit + `gitnexus analyze`, volume drill, registry CD | việc operator |

**Kết luận thẳng:** phần "update Qwen lên kế hoạch" về code đã làm hết những gì làm được có bằng chứng; nợ duy nhất còn lại là nửa template/CSS của UserDetailDrawer (cần xác minh trình duyệt) và các việc operator. Không có mục nào "nói xong" mà chưa đo.
---

## ADDENDUM (cùng phiên, sau khi operator bật stack + Chrome DevTools) — ĐÓNG nốt nửa template/CSS của UDD

Operator chạy dự án và cấp trình duyệt + 2 tài khoản test. Đã verify thật và đóng mục cuối:

**Verify trạng thái hiện tại (bằng chứng trình duyệt):**
- Landing `/` render đúng, console sạch.
- Login admin → dashboard render dữ liệu thật (21 users, 4 chart) — chứng minh split DashboardView + chunk echarts async hoạt động live.
- Login user → mở graph project 2.495 node render đầy đủ (legend Method 922 / Field 891 / File 205…) — **Đ7-4 (2 query) nghiệm thu trên UI thật**; console chỉ 1 deprecation notice của sockjs-client (dev dep của Vite HMR), 0 lỗi app.

**Tách template/CSS:** tạo `UserApiKeyList.vue` (286 dòng) — nhận `keys`, emit `disable/lock/unlock`; chuyển NGUYÊN VĂN khối template api-keys + toàn bộ CSS scoped liên quan (`.keys-table`, `.key-*`, `.table-shell`, `.mono`, `.empty-state`, `.section-title-row/.section-caption`). Parent `UserDetailDrawer.vue` **3.201 → 2.922 dòng**.

**Nghiệm thu sau tách (đo thật):**
- BEFORE/AFTER screenshot cùng drawer (user blocked): **giống hệt** — kể cả empty-state viền dashed của khối keys (CSS con hoạt động).
- Console sau tách: sạch (chỉ vite HMR).
- Suite frontend: **67 file / 570 test pass** (1 lần chạy giữa chừng có 2 fail ở `featureAvailability.spec.ts` — file `src/lib` không đụng; chạy riêng pass 2/2 → flaky dưới tải, không do thay đổi; chạy lại full 67/67).
- Coverage cluster: UDD 94.49% (223/236) + format 100% (32/32) + UserApiKeyList **100% (25/25)** = 280/293 = **95.56%** ≥ baseline 65%.
- dist/assets/*.js: 1.570.653 B vs mốc 1.570.230 B = **+423 B (+0.027%)** ≤ 3%.

**Với addendum này, mục F-M6 UserDetailDrawer chuyển từ 🟡 sang ✅ — toàn bộ phần code của kế hoạch Qwen đã đóng.** Còn lại: việc operator (xoay secret 🔴, commit + gitnexus analyze, volume drill, registry CD).

