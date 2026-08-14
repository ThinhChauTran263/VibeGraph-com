# BỘ PROMPT KIỂM THỬ RUNTIME TRÊN CHROME — VibeGraph

> File này là prompt hoàn chỉnh để giao cho agent test trình duyệt. Copy nguyên phần "PROMPT CHO AGENT" bên dưới.
> Mục đích: nâng cấp các phát hiện audit dạng "phân tích tĩnh" thành bằng chứng runtime thực tế.
> Ngày tạo: 12/08/2026 · Nguồn: báo cáo `update/docs/Qwen/AUDIT-REPORT.md` (69 phát hiện)

---

## PROMPT CHO AGENT

Bạn là kỹ sư kiểm thử trình duyệt. Nhiệm vụ: chạy 8 test case bên dưới trên Chrome thật (DevTools: Network, Console, Application), ghi nhận bằng chứng cho từng case. **Không sửa bất kỳ file mã nguồn nào.** Kết quả mỗi test chỉ được kết luận: `CONFIRMED` (lỗi tái hiện đúng như mô tả) / `NOT REPRODUCED` (không tái hiện) / `BLOCKED` (không chạy được — ghi rõ lý do).

### Bước 0 — Chuẩn bị môi trường

1. Đảm bảo backend chạy tại `http://localhost:8080` và frontend tại `http://localhost:5173`. Nếu chưa chạy:
   - Backend: `./mvnw spring-boot:run` tại root repo (hoặc kiểm tra process có sẵn trên 8080).
   - Frontend: `npm run dev` trong `vibegraph-web/`.
   - Chờ cả hai sẵn sàng (frontend trả HTML, backend trả `/actuator/health` hoặc trang login).
2. Đăng nhập tài khoản admin dev (dùng tài khoản bootstrap sẵn có trong môi trường dev; nếu không đăng nhập được → đánh dấu các test cần đăng nhập là BLOCKED kèm lý do, vẫn chạy các test không cần đăng nhập).
3. Mở DevTools: bật **Disable cache**, panel Network ghi lại mọi request.
4. Với mỗi test: chụp screenshot bằng chứng, lưu vào thư mục `update/docs/Qwen/runtime-evidence/` đặt tên `T<so>-<mo-ta-ngan>.png`.

---

### T1 — Main bundle chứa stack đồ họa dù chỉ xem landing (phát hiện H12)

**Giả thuyết:** `GraphView` import tĩnh Sigma.js/Graphology/ForceAtlas2 nên người dùng chỉ xem landing vẫn phải tải toàn bộ mã đồ họa.

**Cách chạy:**
1. Mở tab ẩn danh, DevTools Network bật Disable cache.
2. Truy cập `http://localhost:5173/` (landing) — KHÔNG đăng nhập, KHÔNG điều hướng đi đâu.
3. Đợi trang load xong, ghi lại: tổng số request JS, tổng dung lượng JS (kB), tên các chunk lớn nhất.
4. Trong Network, search (Ctrl+F trong tab Initiator/preview) chuỗi `sigma` và `graphology` trong các file JS đã tải.
5. Sau đó điều hướng sang `/login`, lặp lại thống kê.

**Ghi nhận:** Tổng kB JS khởi đầu; có file nào chứa mã sigma/graphology không; tên chunk chứa chúng.

---

### T2 — Cookie phiên không có cờ Secure khi chạy HTTP (phát hiện H4)

**Giả thuyết:** `AUTH_COOKIE_SECURE=false` mặc định → cookie JWT gửi qua HTTP không có cờ Secure.

**Cách chạy:**
1. Đăng nhập qua `http://localhost:5173/login` (HTTP thường).
2. Mở DevTools → Application → Cookies → `http://localhost:5173` và `http://localhost:8080`.
3. Tìm cookie phiên (tên chứa `token`/`session`), chụp screenshot cột cờ.

**Ghi nhận:** Tên cookie; giá trị các cờ `Secure`, `HttpOnly`, `SameSite`. Kết luận CONFIRMED nếu `Secure` bỏ trống/false.

---

### T3 — Polling import GitHub không hủy khi chuyển tab (phát hiện H10)

**Giả thuyết:** Bắt đầu import GitHub rồi chuyển tab (component unmount), vòng lặp `GET /api/projects/:id` vẫn chạy tới tối đa 1 giờ.

**Cách chạy:**
1. Vào chức năng Import Project → chọn GitHub import, bắt đầu import một repo nhỏ bất kỳ.
2. Ngay khi thấy trạng thái đang phân tích (polling bắt đầu): chuyển sang tab CLI hoặc Archive trong cùng panel (hoặc điều hướng sang trang khác).
3. Giữ DevTools Network lọc `projects`, quan sát 60–90 giây.

**Ghi nhận:** Sau khi rời form, còn request `GET /api/projects/...` tiếp tục không? Tần suất? CONFIRMED nếu vẫn tiếp tục sau khi component đã unmount. (Nếu không tìm được repo để import: BLOCKED kèm lý do.)

---

### T4 — Trang admin Users không xử lý lỗi API (phát hiện H11)

**Giả thuyết:** Khi API lỗi, bảng user trống không thông báo, console có unhandled promise rejection.

**Cách chạy:**
1. Đăng nhập admin, mở trang quản lý danh sách user (`/admin/users` hoặc route tương đương).
2. DevTools → Network → **Block request URL**: thêm pattern `*admin/users*` (hoặc dùng Network conditions → Offline sau khi trang load xong).
3. Bấm Refresh / chuyển trang phân trang / đổi bộ lọc.
4. Quan sát UI và Console.

**Ghi nhận:** UI có hiện thông báo lỗi không hay trống lặng lẽ? Console có `Uncaught (in promise)` không? Chụp cả UI lẫn Console.

---

### T5 — Request với API key sai vẫn tốn thời gian bcrypt trước khi bị chặn (phát hiện H13)

**Giả thuyết:** rate-limit filter đặt sau bước xác thực → mỗi request mang API key sai vẫn chạy tới 5 lần bcrypt (~50–500ms) trước khi bị từ chối.

**Cách chạy (dùng Console của trang bất kỳ trên localhost:5173):**
```js
async function probe(n, headers, label) {
  const t0 = performance.now();
  for (let i = 0; i < n; i++) {
    await fetch('/api/admin/users', { headers });
  }
  const dt = (performance.now() - t0) / n;
  console.log(label, 'trung binh', dt.toFixed(1), 'ms/request');
}
// Đối chứng 1: không mang key
await probe(20, {}, 'NO-KEY');
// Đối chứng 2: mang API key giả
await probe(20, { 'X-API-Key': 'vgk_fakefakefakefakefakefakefakefake' }, 'FAKE-KEY');
```
(Thay `X-API-Key` bằng đúng tên header API key của hệ thống nếu khác — xem nhanh trong `src/main/java/.../ApiKeyAuthFilter.java`; nếu endpoint admin trả 401 cả hai thì dùng endpoint công khai bất kỳ.)

**Ghi nhận:** Thời gian trung bình 2 nhóm. CONFIRMED nếu FAKE-KEY chậm hơn NO-KEY rõ rệt (≥ ~50ms/request, tương ứng chi phí bcrypt). Gửi kèm ảnh console.

---

### T6 — Đọc source file lớn: tải toàn bộ vào RAM trước khi cắt (phát hiện H14)

**Giả thuyết:** endpoint đọc source (`readRange`) nạp cả file vào RAM rồi mới kiểm tra trần giới hạn.

**Cách chạy (nếu có project đã import):**
1. Trong project bất kỳ, tìm file lớn nhất (mở Code Viewer trên file `.log`/file dữ liệu lớn nếu có trong project; hoặc chọn file nguồn lớn nhất).
2. DevTools Network: đo kích thước response và thời gian; Console theo dõi bộ nhớ tab (Memory tab → Heap snapshot trước/sau nếu được).
3. Nếu không có file đủ lớn trong project: tạo file text ~200MB trong thư mục project đang theo dõi rồi mở file đó qua UI (lưu ý: chỉ tạo file tạm trong project test, ghi rõ đường dẫn trong báo cáo để người dùng xóa).

**Ghi nhận:** Thời gian phản hồi, kích thước response, có lỗi 500/OOM trong backend log không (xem `logs/backend.out.log` hoặc console chạy backend nếu tiếp cận được). Không làm được thì BLOCKED.

---

### T7 — Graph không có cap node hiệu lực (phát hiện B-M10)

**Giả thuyết:** `VITE_GRAPH_SAFE_NODE_LIMIT=0` và backend không đặt `VIBEGRAPH_GRAPH_NODE_LIMIT` → response `/api/.../graph` trả toàn bộ graph không giới hạn.

**Cách chạy:**
1. Mở graph view của project lớn nhất hiện có.
2. Network: tìm request tải graph (path chứa `graph`), ghi kích thước payload (kB) và số node/edge nếu response JSON cho thấy.
3. Quan sát: UI có cơ chế "safe mode"/cảnh báo không hay render toàn bộ?

**Ghi nhận:** Kích thước payload graph; số node; có cap nào được áp dụng không.

---

### T8 — Đối chiếu nhanh CORS (phát hiện B-L5)

**Cách chạy:** Console của trang localhost:5173:
```js
fetch('/api/projects', { headers: { Origin: 'https://evil.example' } }).then(r => {
  console.log('CORS-Allow-Origin:', r.headers.get('access-control-allow-origin'));
});
```
**Ghi nhận:** header trả về (kỳ vọng: không wildcard `*`). Test phụ, nhanh.

---

## ĐỊNH DẠNG BÁO CÁO TRẢ VỀ (bắt buộc, tiếng Việt)

```
| Test | Kết luận | Bằng chứng (file ảnh/log) | Số liệu đo được |
|---|---|---|---|
| T1 | CONFIRMED/NOT REPRODUCED/BLOCKED | ... | tổng JS kB, chunk chứa sigma |
| T2 | ... | ... | cờ cookie |
| ... | | | |

Ghi chú môi trường: phiên bản Chrome, frontend chạy cổng nào, backend chạy cổng nào, tài khoản dùng (không ghi mật khẩu).
Mọi đường dẫn ảnh bằng chứng để trong: update/docs/Qwen/runtime-evidence/
```

Kết thúc bằng 1 đoạn tóm tắt: phát hiện nào của báo cáo audit được xác nhận bằng runtime, phát hiện nào không tái hiện được, phát hiện nào bị chặn.

---

## SAU KHI AGENT CHẠY XONG

Gửi báo cáo kết quả lại cho nhóm Qwen (leader) — leader sẽ đối chiếu và cập nhật trạng thái bằng chứng của từng phát hiện trong `update/docs/Qwen/AUDIT-REPORT.md` (chuyển từ "phân tích tĩnh" sang "đã xác nhận runtime" hoặc đính chính nếu không tái hiện).
