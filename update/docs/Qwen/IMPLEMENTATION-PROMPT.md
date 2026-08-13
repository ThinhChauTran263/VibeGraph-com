# IMPLEMENTATION PROMPT — TRIỂN KHAI REMEDIATION VibeGraph

> File này là prompt hoàn chỉnh để giao cho agent triển khai sửa chữa. Copy nguyên phần "PROMPT CHO AGENT TRIỂN KHAI".
> Nguồn sự thật: bộ tài liệu trong `update/docs/Qwen/` (76 phát hiện, đã kiểm chứng). Ngày tạo: 12/08/2026.

---

## PROMPT CHO AGENT TRIỂN KHAI

Bạn là kỹ sư triển khai senior cho dự án VibeGraph tại `d:\Users\User\IdeaProjects\VibeGraph`. Nhiệm vụ: sửa các phát hiện audit theo đúng kế hoạch có sẵn. Bạn KHÔNG được tự ý sáng tạo phạm vi — mọi thay đổi phải truy vết được về một mã phát hiện trong tài liệu.

### Bước 0 — Đọc bắt buộc theo thứ tự (không bỏ bước)

1. `update/docs/Qwen/README.md` — quy ước mã phát hiện, trạng thái bằng chứng, bảng tham chiếu chéo, danh sách claim đã bác bỏ.
2. `update/docs/Qwen/AUDIT-REPORT.md` — nguồn sự thật: 76 phát hiện với file + dòng + snippet.
3. `update/docs/Qwen/REMEDIATION-PLAN.md` — lộ trình theo đợt, thứ tự, tiêu chí nghiệm thu, rủi ro khi sửa.
4. `update/docs/Qwen/FIX-DETAILS-BACKEND.md` và `FIX-DETAILS-FRONTEND-DEVOPS.md` — snippet hiện trạng → code đề xuất → nghiệm thu cho từng mục.

### Luật cứng (vi phạm = dừng ngay và báo operator)

- **KHÔNG commit** trừ khi operator ra lệnh tường minh.
- **KHÔNG chạy lệnh git phá hủy** (`stash drop`, `reflog expire`, `gc --prune`, `clean -fdX`...) trừ khi operator duyệt từng lệnh.
- **KHÔNG đọc ghi giá trị secret**: không in nội dung `.env` vào báo cáo/log; không tự xoay secret.
- **Code thực tế lệch tài liệu → DỪNG và báo**, không tự ứng biến. (Tài liệu đã qua 3 vòng kiểm chứng; lệch nghĩa là hoặc code đổi, hoặc tài liệu sai — cần người phán xử.)
- Mỗi lần sửa một cụm nguyên nhân (không xé lẻ), sửa xong chạy kiểm tra của cụm đó rồi mới sang cụm khác.

---

### ĐỢT 1 — Bảo mật + Docker (chạy 2 mũi, nghiệm thu chung)

**Mũi 1a — Docker/DevOps (file: `Dockerfile`, `vibegraph-web/Dockerfile`, `docker-compose.yml`, tạo mới `vibegraph-web/.dockerignore`, `pom.xml` nếu cần `<finalName>`):** theo FIX-DETAILS-FRONTEND-DEVOPS mục H1–H5 + D-M2/D-M3: non-root user; copy jar tường minh; bỏ mount `./.env:/app/.env:ro` (dòng ~128); bind DB ports về `127.0.0.1:`; `AUTH_COOKIE_SECURE` default true; bỏ `NEO4J_dbms_security_procedures_unrestricted`; tạo `.dockerignore` frontend.

**Mũi 1b — Backend security, THEO ĐÚNG THỨ TỰ SAU:**
1. **S-M2 TRƯỚC** — `ClientAddressResolver.java`: lấy token **phải nhất ngoài trusted range** thay vì `.findFirst()` trái nhất.
2. **Rồi mới H13** — `SecurityConfig.java`: đẩy `rateLimitFilter` lên trước các filter xác thực.
3. H14 — `SourceFileServiceImpl.java`: chốt `Files.size()` trước `readAllLines` trong readRange.
4. H15 — cùng file: redact theo block từ `-----BEGIN` tới `-----END`.
5. H17 — `application.yaml`: `spring.task.scheduling.pool.size: 4` (+ B-M14 phân trang purge `ProjectTrashService`).
6. B-M12 — `GlobalExceptionHandler.java`: log warn + message chung cho `IllegalStateException`.

**Nghiệm thu Đợt 1 (bắt buộc đủ 4):**
- `./mvnw -q -DskipITs test` pass (hoặc tập test khoanh vùng nếu operator chỉ định).
- Đo lại V2.2 (3 nhóm timing) — nhóm C trùng prefix giờ phải bị **429** sau ngưỡng, không còn 90/90 401 chậm.
- **Biến thể XFF:** lặp V2.2 nhưng mỗi request kèm `X-Forwarded-For` xoay vòng — vẫn phải chạm 429 (chứng minh S-M2 đã khóa bypass).
- Chạy lại T6 (file 200MiB): heap KHÔNG được tăng ~1,06× kích thước file; đo thêm 3 request đồng thời và heap sau 60s (spike hay leak).

### ĐỢT 2 — Backend logic & hiệu năng (sau nghiệm thu Đợt 1)

Theo REMEDIATION-PLAN Đợt 2 + FIX-DETAILS-BACKEND: H6+H7 (registry đọc từ Postgres + full UUID, sửa cùng đợt), H8 (`/analyze` trả 202 + scheduler nền), H9 (batch query admin), B-M1/B-M3/B-M5/B-M6/B-M9/B-M11/B-M13... Riêng **B-M11**: tạo project FAILED chủ động trong môi trường riêng để test transaction bọc upsert — KHÔNG kill backend dùng chung. Kèm alert rule Prometheus cho H17 (bắt buộc khi ship): `rate(security_events.dropped.total[5m]) > 0` + gauge queue > 80% của 10.000.

**Nghiệm thu:** `./mvnw verify` pass; test đơn vị mới cho từng fix; B-M11 có bằng chứng project FAILED không để lại graph dở.

### ĐỢT 3 — Frontend (song song Đợt 2, khác module)

Theo FIX-DETAILS-FRONTEND-DEVOPS: H10 (cancellation token + `onScopeDispose`), H11 (`loadUsers` try/catch + error state), H12 (lazy route + `manualChunks`), F-M1 (xóa 9 file dead code ~1.319 dòng + test mồ côi), F-M3 (gỡ axios + `lib/http.ts`), F-M4 (lazy locale), F-M7 (i18n text cứng).

**Nghiệm thu:** `npm run type-check` + `npm run build` pass; đo lại T1: landing KHÔNG còn tải chunk sigma/graphology (tổng JS giảm mạnh so với mốc 4,17MB/117 module); chạy lại T4: API lỗi phải hiện thông báo, không còn `Uncaught (in promise)`.

### BACKLOG — sau Đợt 2+3

26 mục Thấp + Trung bình chưa xếp đợt theo REMEDIATION-PLAN (B-L1–L11, F-L1–L4, S-L1–L5, D-L1–L6, S-M1/S-M3–S-M5, D-M1–D-M5...). Dọn rác root BẰNG LỆNH ĐÍCH DANH — **tuyệt đối không `git clean -fdX`** (sẽ xóa ~985MB gồm target/, node_modules/, .gitnexus/, .vibegraph/).

---

### ĐỊNH DẠNG BÁO CÁO SAU MỖI ĐỢT (bắt buộc)

```
| Mã | File đã sửa | Dòng thay đổi | Kiểm tra đã chạy | Kết quả | Lệch so với FIX-DETAILS (nếu có) |
```
Kèm: danh sách test mới thêm, số đo trước/sau (timing, bundle kB, heap MiB), mọi điểm dừng-do-lệch và câu hỏi cần operator phán xử. KHÔNG tự cập nhật tài liệu trong `update/docs/Qwen/` trừ khi operator yêu cầu.

---

## DÀNH CHO NGƯỜI VẬN HÀNH — ĐỢT 0 (bạn tự làm hoặc duyệt)

1. **Xoay secret** (tài khoản của bạn, agent không làm thay): Supabase DB password, `JWT_SECRET`, Google/GitHub OAuth client secret, 8 Gemini API key → cập nhật `.env` + các nơi tiêu thụ.
2. **Dọn git object** — chỉ sau khi xoay xong, duyệt từng lệnh:
```powershell
git stash list                                   # hiện có 4 stash — kiểm từng cái
git stash drop stash@{0}                         # lặp cho stash chứa secret
git reflog expire --expire=now --all
git gc --prune=now
# xóa 2 file backup trong working tree:
#   .env.codex_backup-before-9e1dfed-20260725-140618
```
3. Kiểm tra sạch: `git grep -l 'GOCSPX-' $(git rev-list --all)` phải trả về trống (lưu ý `--all` bao gồm `refs/stash`).
