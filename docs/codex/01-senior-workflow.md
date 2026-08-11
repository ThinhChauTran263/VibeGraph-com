# Senior Workflow: Giao việc như một senior engineer

Codex không thiếu “thông minh”; phần lớn kết quả kém đến từ task không có đầu ra, scope hoặc tiêu chí hoàn thành rõ. Dùng workflow dưới đây để biến prompt bình thường thành một ticket có thể thực thi.

## 1. Chọn loại task trước khi gõ

### A. Hỏi/hiểu

Không cho sửa file. Yêu cầu evidence và đường đi thực tế.

```text
Giải thích cách <flow> chạy trong repo này.
Chỉ đọc, không sửa file. Trace từ entry point đến persistence/UI,
nêu file và symbol quan trọng, rồi chỉ ra điểm rủi ro và test liên quan.
```

### B. Chẩn đoán

Không nhảy thẳng vào fix.

```text
Điều tra lỗi: <triệu chứng + cách tái hiện>.
Chưa sửa file. Hãy:
1) tái hiện hoặc tìm bằng chứng,
2) khoanh nguyên nhân theo xác suất,
3) nêu giả thuyết bị loại,
4) đề xuất patch nhỏ nhất và test chứng minh.
```

### C. Thực hiện

Cho phép sửa nhưng giữ scope.

```text
Implement <mục tiêu> trong phạm vi <module/file>.
Không đổi API/schema ngoài yêu cầu, không commit.
Trước khi sửa symbol, chạy GitNexus impact analysis.
Viết regression test trước hoặc cùng patch; chạy test liên quan.
Báo cáo file/dòng đã đổi, lệnh đã chạy, kết quả và rủi ro còn lại.
```

### D. Review

Reviewer không được biến thành implementer.

```text
Review working tree như owner.
Findings trước, theo severity: correctness, security, regression,
performance, missing tests. Cite file và line. Không sửa file, không commit.
```

## 2. Quy trình 7 bước

### Bước 0 - Xác nhận thế giới thật

```text
/status
```

Kiểm tra cwd, model, effort, sandbox, approval và token trước khi task dài.

### Bước 1 - Map code

Với VibeGraph, yêu cầu Codex dùng GitNexus trước khi sửa:

```text
Map flow <tên flow> bằng gitnexus_query và gitnexus_context.
Chỉ trả về entry points, callers/callees, execution flow và file liên quan.
```

### Bước 2 - Đánh giá blast radius

```text
Trước khi sửa <symbol>, chạy gitnexus_impact({target: "<symbol>", direction: "upstream"}).
Báo direct callers, affected processes, risk level. Nếu HIGH/CRITICAL, dừng
và báo mình trước khi edit.
```

### Bước 3 - Plan vừa đủ

Dùng `/plan` cho feature lớn, migration, auth, WebSocket hoặc refactor liên module. Không cần plan mode cho đổi text, một test nhỏ hoặc một lệnh chẩn đoán.

Plan tốt phải có:

- outcome kiểm chứng được;
- file/module sở hữu thay đổi;
- thứ tự và dependency;
- test/rollback;
- điều kiện dừng nếu phát hiện rủi ro.

### Bước 4 - Implement nhỏ

Chia patch theo vertical slice: domain/service → adapter/controller → test → docs. Sau mỗi slice chạy feedback nhanh (`mvn compile`, `npm run type-check`) thay vì chờ đến cuối.

### Bước 5 - Verify độc lập

Không coi “code compiles” là hoàn thành. Chọn bằng chứng phù hợp:

| Loại thay đổi | Bằng chứng |
|---|---|
| Java service | unit test + integration nếu có DB/Neo4j |
| REST/auth | test controller + integration/reproducer HTTP |
| WebSocket | test message flow + session/revoke case |
| Vue/TS | unit test + type-check + build |
| Migration | validate/migrate trên DB test + query/index check |

### Bước 6 - Review và handoff

```text
/diff
/review
```

Cuối lượt luôn yêu cầu format:

```text
Báo cáo theo 4 mục:
1. Đã thay đổi (file + mục đích)
2. Đã kiểm chứng (lệnh + kết quả)
3. Chưa kiểm chứng / giả định
4. Rủi ro và bước tiếp theo
```

## 3. Khi nào dùng subagent

Dùng khi các phần độc lập và output có thể tóm tắt. Ví dụ: security, test gaps, frontend và architecture review. Không dùng để chia một hàm nhỏ thành nhiều agent rồi phải hợp nhất xung đột.

Prompt chuẩn:

```text
Dùng 4 subagent song song, mỗi agent sở hữu đúng một lane:
- java_reviewer: backend correctness và transaction
- typescript_reviewer: Vue/TypeScript và async UI
- security_reviewer: auth, session, input, secrets
- architect: boundary, scaling, failure modes
Tất cả read-only. Chờ đủ kết quả rồi tổng hợp findings theo severity.
```

## 4. Quy tắc chống “AI drift”

- Nhắc lại mục tiêu nếu Codex bắt đầu tối ưu thứ ngoài scope.
- Dừng retry cùng một lệnh; chuyển sang một check phân biệt giả thuyết.
- Không dán log khổng lồ nếu chỉ cần 20 dòng quanh lỗi.
- Không cho phép “sửa luôn” trong task chẩn đoán.
- Không commit nếu bạn chưa xem `/diff` và chưa chạy checklist.

