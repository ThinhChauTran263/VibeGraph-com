# Prompt Library: Copy, sửa vài chỗ, chạy

Các prompt dưới đây cố ý rõ hơn prompt nói chuyện bình thường. Bạn không cần dùng từ chuyên môn; chỉ cần giữ các trường **mục tiêu**, **scope**, **ràng buộc**, **kiểm chứng** và **đầu ra**.

## 1. Hiểu một flow mà không sửa code

```text
Mục tiêu: giải thích flow <tên flow> trong repo này.

Chỉ đọc, không sửa file và không commit.
Trace từ entry point đến service, repository/database và frontend consumer nếu có.
Ưu tiên GitNexus query/context thay vì grep rộng.

Đầu ra:
- sơ đồ luồng ngắn;
- file và symbol chính;
- state/error transitions;
- test đang bảo vệ flow;
- điểm chưa chắc chắn.
```

## 2. Chẩn đoán bug, chưa fix

```text
Chẩn đoán lỗi sau, chưa sửa file:

Triệu chứng: <mô tả>
Cách tái hiện: <các bước>
Kỳ vọng: <expected>
Thực tế: <actual>

Hãy kiểm tra state thật, log/test liên quan và execution flow.
Xếp các giả thuyết theo xác suất, chỉ ra bằng chứng ủng hộ/phản bác.
Kết thúc bằng root cause khả dĩ nhất, patch nhỏ nhất và regression test cần có.
```

## 3. Fix bug có regression test

```text
Fix bug: <mô tả>.

Phạm vi: <module/file>.
Không thay đổi API/schema ngoài yêu cầu, không commit.
Trước khi sửa symbol, chạy gitnexus_impact upstream và báo blast radius.
Viết regression test chứng minh lỗi trước khi/đồng thời sửa.
Chạy test hẹp trước, sau đó chạy verification phù hợp với module.

Báo cáo:
1. root cause;
2. file/dòng đã đổi;
3. test/lệnh và kết quả;
4. behavior trước/sau;
5. rủi ro còn lại.
```

## 4. Feature backend Spring Boot

```text
Implement backend feature: <mục tiêu>.

Yêu cầu:
- giữ layering của RULES.md;
- validate input ở controller boundary;
- authorization phải ở server-side;
- transaction boundary rõ ràng;
- query PostgreSQL/Cypher được parameterize;
- không log token, secret hoặc dữ liệu nhạy cảm;
- không commit.

Trước mỗi symbol edit, chạy GitNexus impact analysis.
Thêm unit test; thêm integration test nếu liên quan DB, Neo4j, auth hoặc WebSocket.
Chạy compile/test liên quan và báo bằng chứng.
```

## 5. Feature frontend Vue/TypeScript

```text
Implement frontend feature: <mục tiêu>.

Phạm vi: <view/component/store>.
Giữ Vue 3 <script setup>, strict TypeScript, Pinia và convention hiện có.
Kiểm tra loading/error/empty state, async race, cleanup, accessibility và mobile.
Không thêm package hoặc framework mới, không commit.

Thêm/cập nhật Vitest; chạy type-check, test liên quan và build.
Báo cáo behavior trước/sau và file đã đổi.
```

## 6. Security review read-only

```text
Review read-only phần <scope> theo hướng exploitability thực tế.

Ưu tiên:
- authentication/authorization;
- JWT, refresh session, cookies, CSRF, CORS;
- input/path/query validation;
- rate limit và replay/concurrency;
- secrets và sensitive logging.

Không sửa file. Findings trước, theo severity, cite file/line.
Mỗi finding phải có attack path, impact, điều kiện khai thác và regression test đề xuất.
Phân biệt vulnerability với hardening/optimization.
```

## 7. Multi-agent review toàn diện

```text
Dùng các subagent song song và chờ đủ kết quả:

- java_reviewer: backend, transaction, query, concurrency, tests
- typescript_reviewer: Vue/TS, async behavior, router/store, accessibility
- security_reviewer: auth/session/input/secrets
- architect: boundary, failure modes, scaling và operational cost

Tất cả read-only, không commit. Mỗi agent chỉ sở hữu lane của mình.
Main agent loại findings trùng, kiểm tra evidence, rồi tổng hợp theo severity
với file/line và test gaps. Không biến style preference thành bug.
```

## 8. Codex tự chọn số subagent

```text
Tự đánh giá độ phức tạp và chỉ dùng subagent khi có lane độc lập.
Được dùng tối đa 8 subagent và depth 2 theo config hiện tại;
không cần chạy đủ 8. Nêu ngắn cách chia ownership, chờ kết quả và tổng hợp.
```

## 9. Phân tích test failure

```text
Điều tra test đang fail: <test/command/error>.

Không sửa hàng loạt. Chạy đúng test lỗi, đọc assertion và production path.
Phân loại: bug production, test sai, flaky, environment hay shared state.
Chạy một check có khả năng phân biệt các giả thuyết trước khi patch.
Nếu sửa, làm patch nhỏ nhất và chạy lại test hẹp rồi suite liên quan.
```

## 10. Review trước commit

```text
Review toàn bộ staged/unstaged/untracked changes trước commit.
Không sửa file.

Kiểm tra:
- diff ngoài scope;
- behavior regression;
- security và secrets;
- migration/API compatibility;
- missing/weak tests;
- log/debug artifact không nên commit.

Findings trước theo severity. Sau findings, đưa checklist lệnh verification còn thiếu.
```

## 11. Production readiness

```text
Đánh giá production readiness của <feature/release>, read-only.

Kiểm tra config/env, migration, rollback, observability, health check,
timeouts/retries, rate limit, security, data retention, backup/restore,
concurrency và capacity. Không suy đoán bằng số liệu không có.

Đầu ra: blocker, must-fix, acceptable risk, evidence đã có và test/runbook còn thiếu.
```

## 12. Handoff sang session mới

```text
Tạo báo cáo handoff tại docs/<task>-handoff.md cho session tiếp theo.

Ghi:
- mục tiêu và quyết định đã chốt;
- file/symbol đã đổi;
- lệnh/test đã chạy và kết quả;
- lỗi/rủi ro chưa giải quyết;
- trạng thái service/process nếu liên quan;
- bước tiếp theo chính xác.

Không chép toàn bộ log; chỉ giữ bằng chứng có giá trị quyết định.
```

## 13. Yêu cầu báo cáo cuối chuẩn

```text
Khi hoàn thành, báo cáo ngắn nhưng đủ:

1. Outcome đạt được.
2. File đã đổi và lý do.
3. Verification: command + pass/fail/skipped.
4. Behavior hoặc dữ liệu trước/sau.
5. Việc chưa làm, giả định và rủi ro còn lại.

Không nói “đã xong” nếu chưa có evidence.
```

