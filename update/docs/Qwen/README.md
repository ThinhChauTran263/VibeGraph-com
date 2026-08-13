# BỘ TÀI LIỆU KẾ HOẠCH TRIỂN KHAI SỬA CHỮA & NÂNG CẤP — VibeGraph

- **Ngày tạo:** 12/08/2026
- **Trạng thái:** dựa trên `AUDIT-REPORT.md` bản mới nhất (**76 phát hiện**: 2 Nghiêm trọng · 17 Cao · 31 Trung bình · 26 Thấp), đã nạp kết quả kiểm chứng runtime (mục §9) và 2 vòng ghi chú đối chiếu chéo (mục §8). **Triển khai: Đợt 1 HOÀN THÀNH + nghiệm thu đạt 12/08/2026** (12/13 mục; H16 chờ `npm audit`) — 1.008 test backend xanh, chi tiết tại AUDIT-REPORT §10; Đợt 2/3 sẵn sàng khởi động; Đợt 0 (xoay secret) chờ xác nhận.
- **Phạm vi:** đây là bộ tài liệu **kế hoạch** — mô tả chính xác nhóm sẽ sửa/nâng cấp từng phát hiện như thế nào để các agent hoặc bên khác đối chiếu. KHÔNG chứa thay đổi mã nguồn nào.

## 1. Mục đích

Bộ tài liệu này trả lời 3 câu hỏi cho mỗi phát hiện trong AUDIT-REPORT:

1. **Sửa khi nào?** → `REMEDIATION-PLAN.md` (lộ trình theo đợt, tiêu chí nghiệm thu, rủi ro khi sửa, cách kiểm tra sau sửa).
2. **Sửa như thế nào (backend)?** → `FIX-DETAILS-BACKEND.md` (snippet cụ thể: hiện trạng file:dòng → code đề xuất → nghiệm thu).
3. **Sửa như thế nào (frontend/DevOps)?** → `FIX-DETAILS-FRONTEND-DEVOPS.md`.

## 2. Chỉ mục file trong thư mục `update/docs/Qwen/`

| File / Thư mục | Mô tả |
|---|---|
| `AUDIT-REPORT.md` | Báo cáo audit toàn diện của nhóm — nguồn sự thật duy nhất: 76 phát hiện kèm bằng chứng file + dòng, mục §8 đối chiếu chéo, mục §9 kiểm chứng runtime, mục §10 kết quả triển khai Đợt 1. |
| `RUNTIME-VERIFICATION-PROMPT.md` | Bộ prompt kiểm thử runtime trên Chrome dùng cho agent bên ngoài (8 test T1–T8). |
| `runtime-evidence/` | Bằng chứng kiểm thử runtime: `RUNTIME-VERIFICATION-REPORT.md` + ảnh/log `T1-landing.png`…`T8-cors.txt`. |
| `REMEDIATION-PLAN.md` | Lộ trình triển khai theo đợt (Đợt 0 → Đợt 3 → Backlog) với bảng phát hiện, tiêu chí nghiệm thu, rủi ro, cách kiểm tra và người thực hiện đề xuất. |
| `FIX-DETAILS-BACKEND.md` | Snippet sửa chi tiết cho các phát hiện backend mức Cao + Trung bình nổi bật (S2, H6–H9, H13–H15, H17, B-M…). |
| `FIX-DETAILS-FRONTEND-DEVOPS.md` | Snippet sửa chi tiết cho H1–H5 (Docker/bảo mật vận hành), H10–H12, F-M… và D-M… |
| `README.md` | File này — chỉ mục, quy ước mã, bảng tham chiếu chéo. |

## 3. Quy ước

### 3.1. Mã phát hiện (nhất quán với AUDIT-REPORT)

| Tiền tố | Ý nghĩa | Ví dụ |
|---|---|---|
| `S` (S1, S2) | Nghiêm trọng (P0) — secret/bảo mật phải sửa ngay | S1, S2 |
| `H` (H1…H17) | Mức Cao (P1), đánh số liên thông mọi lĩnh vực | H1, H13, H17 |
| `B-M` / `B-L` | Trung bình / Thấp — Backend & Database | B-M9, B-L8 |
| `F-M` / `F-L` | Trung bình / Thấp — Frontend | F-M1, F-L1 |
| `S-M` / `S-L` | Trung bình / Thấp — Bảo mật | S-M2, S-L5 |
| `D-M` / `D-L` | Trung bình / Thấp — DevOps | D-M1, D-L3 |

### 3.2. Trạng thái bằng chứng

| Ký hiệu | Ý nghĩa | Phát hiện áp dụng |
|---|---|---|
| **RT** | Đã xác nhận bằng kiểm thử runtime (Chrome, 12/08/2026 — xem mục §9 AUDIT-REPORT) | H4 (T2), H11 (T4), H12 (T1), H13 (V2.2/V2.3), H14 (T6), B-M10 (T7) |
| **SA** | Phân tích tĩnh — bằng chứng file + dòng code thực tế, chưa chạy runtime | đa số phát hiện còn lại, gồm B-M11 (đã tự kiểm chứng tĩnh V1.1; runtime V1.2 BLOCKED) |
| **CC** | Xác nhận chéo từ báo cáo độc lập khác, chưa tự kiểm chứng sâu | hiện không còn — B-M11 đã chuyển sang SA sau vòng kiểm chứng V1 |

Ghi chú đặc biệt (không đổi trạng thái chuẩn ở trên):
- **H13**: test T5 ban đầu KHÔNG tái hiện (key giả prefix ngẫu nhiên → lookup rỗng, `passwordEncoder.matches()` không được gọi); **đo lại V2 (12/08/2026) đã tái hiện — CONFIRMED** (median trùng prefix 54,84ms vs 4,25/4,64ms; V2.3 xác nhận thứ tự filter) → xử lý dứt điểm trong Đợt 1, không cần đo lại trước khi sửa (xem REMEDIATION-PLAN Đợt 1).
- **B-M11**: đã tự kiểm chứng tĩnh (V1.1 CONFIRMED); runtime V1.2 BLOCKED (không có project FAILED, không kill backend dùng chung) — khi sửa cần test với project FAILED tạo chủ động trong môi trường riêng.
- **H10**: test T3 **BLOCKED** (backend không liên hệ được GitHub) — phát hiện giữ trạng thái phân tích tĩnh.

## 4. Tham chiếu chéo giữa các báo cáo

Bảng mapping mã phát hiện của nhóm (Qwen) ↔ codex v2.3 (`docs/audit-report-v2-2026-08-12.md`) ↔ `update/docs/claude/AUDIT-REPORT.md`:

| Qwen (báo cáo nhóm) | codex v2.3 | `update/docs/claude` | Chủ đề |
|---|---|---|---|
| H13 | H9 | — | Rate-limit chạy sau bước băm BCrypt / xác thực API key |
| H14 | H10 | — | `readRange()` nạp cả file vào RAM trước khi áp giới hạn |
| H15 | H11 | — | Private-key redaction chỉ che dòng header BEGIN |
| H7 | M17 | — | Project ID 32 bit ngẫu nhiên, ghi đè lặng lẽ khi trùng |
| H9 | M18 | — | N+1 query khi liệt kê user trong admin |
| H10 | M19 | — | Polling GitHub import không hủy khi unmount |
| S-M2 | M20 | — | `X-Forwarded-For` chọn token trái nhất khi bật trust proxy |
| B-M9 | M21 | — | IP-block truy vấn Postgres trên mọi request, không cache |
| B-M6 | M22 | — | Cache response LLM không giới hạn kích thước/TTL |
| H17 | — | H1 | Scheduler đơn luồng — 7 job `@Scheduled` chung 1 thread |
| B-M12 | — | M1 | `IllegalStateException` → 409 trả raw `getMessage()` |
| B-M13 | — | M2 | 3 file test 0 byte được git track |
| B-M14 | — | M3 | `purgeExpiredProjects` truy vấn không phân trang |
| B-L9 | — | L1 | 6 DTO chết, 98 dòng, 0 tham chiếu |
| B-L10 | — | L2 | Chỉ entity JPA `UserNotification` chết (bảng `user_notifications` vẫn dùng qua JDBC) |
| B-L11 | — | L3 | `TarballImportServiceTest` 8/8 `@Disabled` |

> **Cập nhật codex v2.4 (chốt 17:10 ngày 12/08/2026, 54 finding):** xác nhận lại toàn bộ mapping trên; không có mã mới ngoài C2–M31/L8–L10 đã map về mã Qwen hiện có.

Lưu ý đối chiếu (từ AUDIT-REPORT §8):
- Phát hiện "serve `projects/` qua HTTP port 8080" từng nêu trong đối chiếu đã xác minh là **KHÔNG tồn tại** trong code — không có trong bất kỳ tài liệu kế hoạch nào của bộ này.
- Đối chiếu lần 2 với `update/docs/claude`: 7 phát hiện của claude đã nạp vào AUDIT-REPORT (H17, B-M12–B-M14, B-L9–B-L11); phần bác bỏ (3 mục) và các trục đã kiểm chứng sạch (§4) + danh sách trục chưa phủ (§7) xem trực tiếp `update/docs/claude/` — KHÔNG suy diễn rằng bộ Qwen đã bao gồm chúng.

### Các claim đã bị bác bỏ (không tồn tại trong code)

Ghi lại từ phần bác bỏ của `update/docs/claude/AUDIT-REPORT.md` (§5) — mục đích: ngăn agent khác đi tìm code ma:

1. `getNeighborhood` ném `UnsupportedOperationException` — method **KHÔNG tồn tại** trong codebase.
2. `ImpactController` scaffold rỗng — file **KHÔNG tồn tại**.
3. "Không có `.dockerignore` ở root" — **SAI**: root có `.dockerignore` (112 bytes). (Chỉ `vibegraph-web/.dockerignore` là thiếu — xem H5.)

## 5. Nguyên tắc sử dụng bộ tài liệu

1. Mọi mã phát hiện, file, dòng, mức độ trong bộ tài liệu này **phải khớp** `AUDIT-REPORT.md`; nếu có mâu thuẫn, AUDIT-REPORT là nguồn đúng.
2. Không bịa thêm phát hiện ngoài 76 mục đã có; phát hiện mới (nếu có ở vòng sau) phải đi qua quy trình kiểm chứng bằng chứng như các vòng trước.
3. Khi triển khai sửa thật: KHÔNG sửa AUDIT-REPORT.md trong cùng commit với code; kết quả sửa được ghi nhận riêng.
