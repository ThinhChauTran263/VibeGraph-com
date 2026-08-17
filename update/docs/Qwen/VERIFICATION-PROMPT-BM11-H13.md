# BỘ PROMPT KIỂM CHỨNG CHUYÊN SÂU — B-M11 VÀ H13

> Prompt hoàn chỉnh để giao cho agent kiểm thử (Chrome + terminal). Mục đích: xử lý 2 phát hiện còn "treo" trong `update/docs/Qwen/AUDIT-REPORT.md`:
> - **B-M11** — Upsert Neo4j không nguyên tử (trạng thái CC: mới xác nhận chéo từ 2 báo cáo độc lập, chưa tự kiểm chứng sâu).
> - **H13** — Rate-limit chạy sau bước băm BCrypt (phân tích tĩnh CÓ, nhưng test runtime T5 ngày 12/08 KHÔNG tái hiện — lý do: key giả prefix ngẫu nhiên không khớp key nào trong DB nên `findTop6ByKeyPrefix...` trả rỗng, bcrypt không bao giờ được gọi).
> Ngày tạo: 12/08/2026.

---

## PROMPT CHO AGENT

Bạn là kỹ sư kiểm thử. Nhiệm vụ: chạy 2 nhóm kiểm chứng V1 (B-M11) và V2 (H13) bên dưới, ghi nhận bằng chứng. **Không sửa mã nguồn, không commit.** Mỗi test kết luận: `CONFIRMED` / `NOT REPRODUCED` / `BLOCKED` (kèm lý do). Bằng chứng lưu vào `update/docs/Qwen/runtime-evidence/` đặt tên `V1-*` / `V2-*`.

### Bước 0 — Chuẩn bị

1. Backend chạy tại `http://localhost:8080`, frontend `http://localhost:5173`, Neo4j khả dụng (browser Neo4j `http://localhost:7474` hoặc bolt). Nếu chưa chạy, khởi động như lần kiểm thử trước.
2. Đăng nhập tài khoản admin dev.
3. Đọc nhanh 2 file nguồn này trước để nắm cơ chế (không sửa):
   - `src/main/java/com/vibegraph/graph/repository/impl/neo4j/Neo4jGraphRepository.java` — vùng upsert node/edge (các lệnh `session.run`, xem có dùng transaction `session.executeWrite` hay autocommit từng lệnh).
   - `src/main/java/com/vibegraph/auth/web/ApiKeyAuthFilter.java` — dòng ~80–94 (`findTop6ByKeyPrefix...` + `passwordEncoder.matches`) và tên header API key thật (dùng đúng tên header này cho V2).

---

## V1 — Kiểm chứng B-M11: Upsert Neo4j không nguyên tử

**Giả thuyết:** upsert graph chạy bằng nhiều lệnh autocommit `session.run` riêng lẻ (không transaction); khi phân tích FAILED giữa chừng, project giữ graph dở dang một phần.

### V1.1 — Kiểm chứng tĩnh (bắt buộc, luôn chạy được)
1. Mở `Neo4jGraphRepository.java`, tìm các hàm upsert node/edge. Ghi nhận:
   - Có dùng `session.executeWrite(...)` / `Transaction` hay gọi `session.run(...)` autocommit từng câu?
   - Có bao nhiêu lệnh run riêng lẻ cho 1 lần upsert (node, edge, index...)?
2. Tìm nơi xử lý project FAILED khi phân tích (comment đại loại "keep the FAILED project") — ghi nhận hành vi: graph đã upsert dở có bị dọn không?
3. **Kết luận V1.1:** CONFIRMED nếu upsert là autocommit nhiều lệnh + FAILED không dọn graph.

### V1.2 — Kiểm chứng runtime (nếu tạo được tình huống)
Phương án A (khuyến nghị):
1. Tạo/import một project test nhỏ (dùng thư mục test có vài file Java hợp lệ — ghi rõ đường dẫn để dọn sau).
2. Bật backend log mức theo dõi được tiến trình upsert (nếu có log sẵn thì dùng).
3. Khởi động phân tích; khi đang giữa chừng (trạng thái ANALYZING), **stop backend đột ngột** (kill process).
4. Khởi động lại backend. Kiểm tra Neo4j: đếm số node/edge của project test đó (Cypher: `MATCH (n {projectId: '<id>'}) RETURN count(n)` — thay tên property đúng theo schema thực tế; xem schema trong code).
5. Nếu tồn tại node/edge dở dang của project không bao giờ đạt ANALYZED → CONFIRMED runtime.

Phương án B (nếu không muốn kill process): tạo project chứa 1 file Java cố tình lỗi cú pháp nặng để phân tích FAILED; sau đó đếm node/edge của project FAILED trong Neo4j — nếu > 0 nghĩa là graph dở được giữ lại (khớp "keep the FAILED project").

**Ghi nhận V1:** phương án đã chạy, số node/edge đo được, ảnh chụp Neo4j browser hoặc kết quả Cypher, kết luận.

---

## V2 — Đo lại H13: chi phí bcrypt khi mang key TRÙNG PREFIX

**Bối cảnh:** T5 trước đây dùng key giả prefix ngẫu nhiên → lookup rỗng → bcrypt không chạy → không đo được. Muốn tái hiện phải dùng key có **prefix trùng với key thật trong DB** nhưng phần còn lại sai.

### V2.1 — Tạo dữ liệu đo
1. Vào UI quản trị (hoặc endpoint API key của user) tạo 1 API key TEST cho tài khoản dev. **Chỉ ghi nhớ 8–12 ký tự đầu (prefix) của key; KHÔNG ghi key đầy đủ vào báo cáo.**
2. Sau khi đo xong: XÓA key test này (ghi nhận đã xóa).

### V2.2 — Ma trận đo thời gian (Console trình duyệt hoặc curl — đo từ máy chạy backend để loại nhiễu mạng)
Đo 30 request mỗi nhóm vào cùng 1 endpoint (endpoint bất kỳ nhận API key, xem trong `ApiKeyAuthFilter`), ghi thời gian trung bình/median:
- **A. Không mang key**
- **B. Key giả prefix NGẪU NHIÊN** (lặp lại test cũ để đối chứng)
- **C. Key có prefix TRÙNG key thật + phần sau sai** (ví dụ: prefix đúng + chuỗi random đủ dài cho đúng định dạng)
```js
async function probe(n, headers, label) {
  const t = [];
  for (let i = 0; i < n; i++) {
    const s = performance.now();
    await fetch('/api/projects', { headers });   // endpoint nhận API key
    t.push(performance.now() - s);
  }
  t.sort((a, b) => a - b);
  console.log(label, 'median', t[15].toFixed(1), 'ms');
}
await probe(30, {}, 'A-NO-KEY');
await probe(30, { '<HEADER>': 'vgk_randomrandom123456' }, 'B-RANDOM');
await probe(30, { '<HEADER>': '<PREFIX_THẬT>0000000000000000' }, 'C-PREFIX-MATCH');
```
(thay `<HEADER>` bằng tên header thật đọc ở Bước 0; nếu endpoint yêu cầu thêm gì để nhận diện API key thì làm đúng).

**Tiêu chí CONFIRMED:** nhóm C chậm hơn A và B rõ rệt (≥ ~50ms/request — tương ứng chi phí bcrypt × số candidate), trong khi B ≈ A.

### V2.3 — Kiểm chứng thứ tự filter (bổ trợ)
Ghi nhận lại từ code: `SecurityConfig.java` đặt `rateLimitFilter` trước/sau filter nào; kết hợp kết quả V2.2 để kết luận tổng thể H13: CÓ THẬT (cơ chế + chi phí đo được) hay CHƯA ĐỦ TÁI HIỆN (nếu C vẫn không chậm hơn — khi đó ghi rõ khả năng cache/prefix lookup đã chặn trước bcrypt, kèm bằng chứng log nếu bật được DEBUG `com.vibegraph`).

**Ghi nhận V2:** bảng median 3 nhóm, ảnh console/log, tên header đã dùng, xác nhận đã xóa key test.

---

## ĐỊNH DẠNG BÁO CÁO TRẢ VỀ (bắt buộc, tiếng Việt)

```
| Test | Kết luận | Bằng chứng | Số liệu |
|---|---|---|---|
| V1.1 (B-M11 tĩnh) | ... | file chụp màn hình/trích code | autocommit hay transaction, số lệnh run |
| V1.2 (B-M11 runtime) | ... | kết quả Cypher/ảnh | số node/edge dở dang |
| V2.2 (H13 timing) | ... | ảnh console | median A/B/C (ms) |
| V2.3 (H13 thứ tự filter) | ... | trích dòng code | kết luận tổng hợp |

Ghi chú môi trường + xác nhận: đã xóa API key test; đã dọn project test (hoặc để lại — ghi đường dẫn).
```

Kết thúc bằng tóm tắt 2 dòng: B-M11 kết luận gì, H13 kết luận gì — để leader cập nhật trạng thái bằng chứng trong AUDIT-REPORT.md (CC → RT hoặc bác bỏ/điều chỉnh mức độ).
