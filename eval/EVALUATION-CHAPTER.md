# Chương Đánh giá — Bản nháp (VibeGraph)

> Bản nháp tổng hợp từ số liệu đo thực tế trong `eval/structural-accuracy/`.
> Con số là thật, chưa chỉnh. Anh chỉnh văn phong/format cho khớp mẫu luận văn.

## 1. Mục tiêu và câu hỏi đánh giá

Đánh giá độ chính xác của VibeGraph khi trích xuất mô hình mã nguồn (đồ thị + sơ đồ)
từ dự án Java thật. Ba câu hỏi nghiên cứu (RQ):

- **RQ1 — Trích xuất cấu trúc:** VibeGraph nhận diện đúng bao nhiêu phần trăm lớp,
  interface, entity so với mã nguồn?
- **RQ2 — Quan hệ:** Các cạnh quan trọng (gọi hàm CALLS, phụ thuộc INJECTS, endpoint)
  có chính xác không?
- **RQ3 — Suy luận use case:** Thuật toán suy luận use case có ổn định và đúng trên
  các cấu trúc dự án khác nhau không?

## 2. Phương pháp

**Ground truth độc lập.** Để tránh "đo vòng" (dùng chính JavaParser để chấm JavaParser),
ground truth cấu trúc được đếm trực tiếp từ mã nguồn `.java` bằng biểu thức chính quy
trên văn bản **đã loại bỏ comment và chuỗi**. Tín hiệu chính là **so khớp theo tên**
(tập tên lớp/interface/enum của tool so với của nguồn) chứ không phải đếm thô, vì đếm thô
dễ nhiễu.

**Phạm vi.** Chỉ mã production (`src/main/java`); loại trừ test và mã sinh lúc build.
Tool và oracle được ép cùng phạm vi file.

**Đo precision quan hệ CALLS.** Lấy mẫu ngẫu nhiên có hạt giống cố định (seed=42) trên tập
cạnh CALLS; với mỗi cạnh, kiểm tra trong mã nguồn của phương thức gọi có lời gọi tới tên
phương thức đích hay không; báo cáo precision kèm khoảng tin cậy Wilson 95%.

**Đối tượng.**
- `spring-petclinic` (MVC cổ điển, 30 file main).
- `spring-petclinic-rest` (REST, 87 file main).
- 6 fixture gán nhãn tổng hợp cho thuật toán use case (regression harness trong CI).

**Công cụ.** Harness tái lập `eval/structural-accuracy/run-eval.ps1` và
`mcall-precision.ps1`; báo cáo `report.md`/`.csv`, `SUMMARY.md`, `mcall-precision.md`.

## 3. Kết quả

### 3.1 RQ1 — Trích xuất cấu trúc

| Hạng mục | spring-petclinic | spring-petclinic-rest |
|---|---:|---:|
| Types (class/interface/enum) | 25/25 = 100% | 84/85 = 98.8%¹ |
| Class (gồm DBModel) | 22/22 = 100% | 57/57 = 100% |
| Entity (@Entity) | 6/6 = 100% | 8/8 = 100% |
| Tên type sai (thiếu/thừa) | 0 / 0 | 1 / 0 |

¹ 1 "thiếu" là `@interface PetAgeValidation` được tool phân loại thành node `Annotation`
thay vì `Interface` — khác biệt phân loại, không phải bỏ sót. Thực chất ~100%.

**Nhận xét:** trích xuất lớp/entity gần như tuyệt đối; đặc biệt **0 type thừa** ở cả hai
dự án (không bịa ra lớp không tồn tại).

### 3.2 RQ2 — Quan hệ

**Method-call (CALLS) precision** — spring-petclinic:

| Chỉ số | Giá trị |
|---|---:|
| Tổng cạnh CALLS | 64 |
| Mẫu | 30 |
| Đúng | 30 |
| **Precision** | **100%** |
| Wilson 95% CI | [88.6%, 100%] |

Không phát hiện lời gọi bịa. (Proxy mức file — xem mục 4.)

**Endpoint (REST/MVC):** spring-petclinic đạt 16/17 = **94.1%**. Xem thêm mục 4 về
bài học phạm vi với spring-petclinic-rest.

**Dependency injection (INJECTS):** trước khi cải tiến, dự án dùng constructor injection
cho kết quả `INJECTS = 0`. Sau khi bổ sung nhận diện constructor injection, spring-petclinic
đo được **INJECTS = 6** (đúng: controller → service → repository).

### 3.3 RQ3 — Suy luận use case

Thuật toán suy luận được kiểm bằng harness 6 fixture gán nhãn phủ các cấu trúc: có REST
+ role, service-only, entity-only, auth, interface+impl, và MVC. Tất cả đạt **F1 = 1.0**
trên cả ba chiều (actor / use case / quan hệ) và chạy tự động trong CI như lưới chống hồi
quy. Bổ sung: suy luận actor từ `@PreAuthorize` (Seller, Store Manager...) thay vì gom về
"User"; fallback nhiều tầng để dự án không có REST API vẫn sinh được use case.

## 4. Đe dọa tính hợp lệ (Threats to validity)

- **Oracle bằng regex** thô, nhưng đã giảm thiểu bằng (a) loại bỏ comment/chuỗi trước khi
  khớp, (b) dùng so khớp theo TÊN thay vì đếm — cho kết quả 0 sai lệch tên trên petclinic.
- **Precision CALLS là proxy mức file:** một phương thức trùng tên trong cùng file có thể
  gây "chấp nhận nhầm". Do đó 100% nên hiểu là ước lượng trên với N=30 (CI [88.6, 100]).
  Có thể siết bằng kiểm ở mức thân phương thức trong công việc sau.
- **Phạm vi chỉ mã nguồn:** endpoint chỉ tồn tại trong mã sinh lúc build (OpenAPI generator)
  hoặc kế thừa từ interface không được đếm — đây là lựa chọn phạm vi, không phải lỗi.
- **Bài học phương pháp (đã sửa):** chẩn đoán ban đầu "endpoint petclinic-rest 1/11 = 9%"
  là **sai** — đã so method-endpoint với `@RequestMapping` cấp class (chỉ là path prefix).
  Endpoint thật nằm trong interface sinh lúc build, ngoài phạm vi phân tích; endpoint duy
  nhất trong mã nguồn đã được nhận diện đúng. Việc tool và oracle phải cùng phạm vi file là
  bài học then chốt.
- **Bề rộng hạn chế:** 2 dự án thật cho phần cấu trúc; nên mở rộng thêm để khái quát hơn.

## 5. Kết luận

VibeGraph đạt độ chính xác cao ở lõi trích xuất: type/entity ~100%, CALLS precision 100%
(N=30, CI [88.6,100]), endpoint 94.1% với controller kiểu verb-mapping. Hai giới hạn thực
được ghi nhận và xử lý một phần: (1) constructor injection nay đã bắt (INJECTS 0→6);
(2) `@RequestMapping(method=)` nay suy ra đúng verb. Giới hạn còn mở: endpoint từ mã sinh
lúc build / kế thừa interface, và cần thêm dự án để mở rộng bề rộng đánh giá.

## Phụ lục — cách tái lập

Xem `eval/structural-accuracy/README.md`. Lệnh chính:

```powershell
# cấu trúc
./eval/structural-accuracy/run-eval.ps1 -RepoPath <repo> -Name <name> -ProjectId <id>
# precision gọi hàm
./eval/structural-accuracy/mcall-precision.ps1 -RepoPath <repo> -ProjectId <id> -SampleSize 30
```
