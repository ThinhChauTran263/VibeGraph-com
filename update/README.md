# VibeGraph — Audit Reports (`update/`)

Thư mục này chứa báo cáo audit dự án VibeGraph. Mọi kết luận đều **kèm bằng chứng** (đường dẫn file + trích đoạn code thật), không nói suông.

| File | Nội dung |
|------|----------|
| [`AUDIT-REPORT.md`](./AUDIT-REPORT.md) | Báo cáo tổng hợp: hình dạng graph (zoom + generate), tốc độ import 3 phương thức, và audit rộng (bảo mật, đồng thời, dữ liệu, hiệu năng). |

## Nguyên tắc của báo cáo
- **Bằng chứng trước, kết luận sau.** Mỗi finding trích dẫn file + dòng/đoạn code.
- **Giải pháp có so sánh.** Mỗi đề xuất nêu rõ *hơn hiện trạng ở điểm nào* và *cách kiểm chứng*.
- **Phân biệt sự thật vs suy luận.** Phần dựa trên tài liệu/đo lường được ghi rõ nguồn; phần suy luận được đánh dấu.
- **Chưa sửa code.** Đây là tài liệu phân tích.

## Lưu ý quy trình (theo `AGENTS.md`)
Trước khi hiện thực bất kỳ đề xuất nào đụng vào symbol nhiều caller (vd `parseProject`, `apiToGraphology`, `upsertNodes/Edges`, `startLayout`), cần chạy `gitnexus_impact` để đánh giá mức độ ảnh hưởng.

> Trạng thái: Vòng 1 (các vùng rủi ro cao). Chưa bao phủ toàn bộ codebase. Các finding "hiệu năng/hình dạng" nên được củng cố thêm bằng harness đo lường (xem mục cuối báo cáo).
