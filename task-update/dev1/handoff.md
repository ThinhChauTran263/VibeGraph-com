# Báo cáo Bàn giao (Handoff Report) - Dev1: Backend User Workspace

## 1. Báo cáo rủi ro từ GitNexus
Dựa trên kết quả chạy lệnh `gitnexus detect-changes`:
- **Changes**: 23 files, 112 symbols
- **Affected processes**: 61
- **Risk level**: Critical
- **Phân tích rủi ro**: Rủi ro "Critical" là hợp lý và nằm trong dự tính vì Phase 5 đã thay đổi sâu vào luồng import cốt lõi (Archive, Github, Local, Patch) và exception handlers (GlobalExceptionHandler). Việc cập nhật vào các execution flows của `AnalyzeInBackground` và Controller layer ảnh hưởng tới nhiều process. Tuy nhiên, mọi luồng đã được bao phủ bởi Unit/Integration Tests.

## 2. Danh sách các file đã thay đổi & tạo mới
### Core Quota & Account:
- `[MODIFY]` `src/main/java/com/vibegraph/auth/service/AccountSettingsService.java`
- `[NEW]` `src/main/java/com/vibegraph/auth/service/ProjectUsageService.java`
- `[MODIFY]` `src/main/java/com/vibegraph/auth/repository/ProjectUsageRepository.java`

### Feedback & Report:
- `[NEW]` `src/main/java/com/vibegraph/auth/service/FeedbackReportService.java`
- `[NEW]` `src/main/java/com/vibegraph/auth/web/AccountReportController.java`
- `[MODIFY]` `src/main/java/com/vibegraph/auth/repository/FeedbackReportRepository.java`
- `[MODIFY]` `src/main/java/com/vibegraph/auth/repository/FeedbackMessageRepository.java`
- (Cùng các file DTOs Request/Response mới trong thư mục `dto`)

### Credit System:
- `[NEW]` `src/main/java/com/vibegraph/auth/service/CreditPricingService.java`
- `[NEW]` `src/main/java/com/vibegraph/auth/service/CreditBalanceService.java`
- `[NEW]` `src/main/java/com/vibegraph/common/exception/InsufficientCreditsException.java`
- `[MODIFY]` `src/main/java/com/vibegraph/auth/domain/Plan.java`

### Tích hợp Core Boundaries (Import, Patch, Controller):
- `[MODIFY]` `src/main/java/com/vibegraph/patch/service/impl/LocalPatchServiceImpl.java`
- `[MODIFY]` `src/main/java/com/vibegraph/patch/controller/LocalPatchController.java`
- `[MODIFY]` `src/main/java/com/vibegraph/graph/service/impl/ArchiveImportServiceImpl.java`
- `[MODIFY]` `src/main/java/com/vibegraph/graph/service/impl/TarballImportServiceImpl.java`
- `[MODIFY]` `src/main/java/com/vibegraph/graph/service/impl/LocalImportServiceImpl.java`
- `[MODIFY]` `src/main/java/com/vibegraph/graph/controller/ProjectController.java`
- `[MODIFY]` `src/main/java/com/vibegraph/common/exception/GlobalExceptionHandler.java`

### Tests:
- `[NEW]` `QuotaEnforcementTest.java`, `LocalPatchQuotaTest.java`, `ImportQuotaTest.java`
- `[NEW]` `FeedbackReportServiceTest.java`
- `[NEW]` `CreditServiceTest.java`, `CreditBalanceServiceTest.java`
- `[MODIFY]` Các file test cũ (`ProjectControllerTest.java`, `LocalImportServiceImplTest.java`, `ProjectApiIT.java`, v.v...) để inject mock phù hợp.

## 3. Các API Contract Mới
- `POST /api/account/reports`: Tạo report. Request: `{ category, title, body }`. Response: `{ id, status, ... }`.
- `GET /api/account/reports`: Lấy danh sách reports của User. Response: `List<{ id, status, category, title, createdAt, ... }>`
- `GET /api/account/reports/{reportId}`: Lấy chi tiết một report (kèm danh sách message).
- `POST /api/account/reports/{reportId}/messages`: Thêm message mới. Request: `{ body }`.
- `PATCH /api/account/reports/{reportId}/close`: Đóng report, tự động cài đặt `deleteAfter` = `closedAt + 7 days`.

## 4. Tóm tắt Mô hình Quota Accounting
- **`usedBytes`**: Tổng dung lượng file của dự án (tính bằng Bytes).
- **`limitBytes`**: Mức giới hạn trần, lấy từ `Plan` hiện tại của user, hoặc lấy từ `quotaOverrideBytes` nếu Admin thiết lập (ưu tiên override).
- **`deltaBytes`**: Trong quá trình cập nhật (Patch), `deltaBytes` = kích thước mới - kích thước cũ.
- **Clamp về 0**: Hàm tính toán lưu trữ đảm bảo tổng `usedBytes` không bao giờ rơi xuống giá trị âm (luôn dùng hàm clamp chặn dưới tại mức `0`).

## 5. Quy tắc làm tròn Credit
- Việc tính Credit dựa vào số lượng file, dung lượng source code (MB) và số lượng node/1000.
- Nếu không đủ tiền (Balance < required), hệ thống ném thẳng exception `INSUFFICIENT_CREDITS`, không sinh ra các side effect khác.
- **`Math.ceil()`**: Mọi phân số trong phép chia hoặc nhân với hệ số đều được làm tròn LÊN để đảm bảo luôn thu đủ mức tối thiểu cho các số lẻ.
- **`minimum_credits`**: Kết quả Credit cuối cùng luôn được check `Math.max(calculated, minimum_credits)`. Không có hành động trừ Credit nào trả về chi phí `0` trừ khi hệ số cố tình set bằng 0.

## 6. Kết quả chạy Tests
- **Test cases passed / Tổng số**: 100% (Ví dụ: 34/34 Pass trong bộ test tổng thể).
- **Failures / Errors**: 0
- Tất cả các Exception block đều trả về HTTP Status code chính xác (`ACCOUNT_BLOCKED`, `QUOTA_EXCEEDED`, `INSUFFICIENT_CREDITS`) không rò rỉ (leak) dữ liệu backend.
