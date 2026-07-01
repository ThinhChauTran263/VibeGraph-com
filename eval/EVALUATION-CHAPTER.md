# Chương: Đánh giá (Evaluation)

> Bản nháp đầy đủ, tổng hợp từ số liệu đo thực tế trong `eval/structural-accuracy/`.
> Mọi con số là kết quả đo thật, tái lập được. Tác giả biên tập văn phong/định dạng và
> bổ sung trích dẫn cho khớp mẫu luận văn của trường. Những chỗ ghi "[cần trích dẫn]" là
> nơi cần thêm nguồn tham khảo chính thức.

## 1. Giới thiệu chương

Chương này đánh giá **độ chính xác** và **tính hữu dụng** của VibeGraph trong việc dịch
ngược (reverse-engineering) một dự án Java thành mô hình mã nguồn: đồ thị tri thức mã
(code knowledge graph) và các sơ đồ UML (đặc biệt là sơ đồ Use Case "as-built"). Mục tiêu
là chứng minh bằng số liệu rằng công cụ trích xuất đúng những gì có trong mã nguồn, đồng
thời nêu rõ giới hạn của phương pháp — thay vì chỉ khẳng định định tính.

Đánh giá được thiết kế theo hướng **định lượng, có ground truth độc lập và tái lập được**:
mọi số liệu đều sinh ra từ các script trong thư mục `eval/`, chạy lại cho kết quả như nhau.

## 2. Mục tiêu và câu hỏi đánh giá

Ba câu hỏi nghiên cứu (Research Questions):

- **RQ1 — Trích xuất cấu trúc.** VibeGraph nhận diện đúng bao nhiêu phần trăm phần tử cấu
  trúc (lớp, interface, enum, entity) so với mã nguồn thật?
- **RQ2 — Trích xuất quan hệ.** Các cạnh quan hệ quan trọng — lời gọi phương thức (CALLS),
  phụ thuộc tiêm (INJECTS), điểm cuối API (endpoint) — có chính xác không?
- **RQ3 — Suy luận Use Case.** Thuật toán suy luận sơ đồ Use Case có đúng và ổn định trên
  nhiều kiểu cấu trúc dự án khác nhau không?

## 3. Thiết kế đánh giá

### 3.1 Định nghĩa độ đo

Gọi TP (true positive) là phần tử tool sinh ra và có thật; FP (false positive) là phần tử
tool sinh ra nhưng không có thật; FN (false negative) là phần tử có thật nhưng tool bỏ sót.

- **Precision** P = TP / (TP + FP) — trong những gì tool nói, bao nhiêu là đúng.
- **Recall** R = TP / (TP + FN) — trong những gì có thật, tool bắt được bao nhiêu.
- **F1** = 2·P·R / (P + R) — trung bình điều hòa của P và R.

Với ước lượng tỉ lệ từ mẫu (precision của CALLS), dùng **khoảng tin cậy Wilson 95%** thay
cho khoảng chuẩn normal, vì Wilson chính xác hơn khi tỉ lệ gần 0/1 và cỡ mẫu vừa phải
[cần trích dẫn: Wilson 1927].

### 3.2 Đối tượng đánh giá (subjects)

| Dự án | Loại | Quy mô (main) | Vì sao chọn |
|---|---|---|---|
| spring-petclinic | Spring MVC cổ điển (@Controller, @GetMapping) | 30 file, 300 node, 912 cạnh | Chuẩn tham chiếu phổ biến của cộng đồng Spring [cần trích dẫn] |
| spring-petclinic-rest | Spring REST (OpenAPI-first) | 87 file, 987 node, 3668 cạnh | Kiểm hành vi trên kiến trúc REST + mã sinh lúc build |
| 6 fixture gán nhãn | Tổng hợp, có chủ đích | nhỏ | Phủ các nhánh thuật toán use case, chạy trong CI |

Việc chọn dự án là **có chủ đích** (purposive sampling) nhằm phủ các kiểu kiến trúc khác
nhau, không phải chọn ngẫu nhiên.

### 3.3 Môi trường

Backend Spring Boot 4 + Neo4j 5 (Docker); phân tích bằng JavaParser + Symbol Solver. Ground
truth đo trên cùng máy, cùng phiên bản mã nguồn dự án.

## 4. Phương pháp

**Ground truth độc lập (tránh đo vòng).** Nếu dùng chính JavaParser để lập ground truth rồi
chấm công cụ (cũng dựa trên JavaParser) thì phép đo vô nghĩa. Do đó ground truth cấu trúc
được đếm trực tiếp từ mã nguồn `.java` bằng biểu thức chính quy, **sau khi loại bỏ comment
và chuỗi ký tự** (nếu không, chuỗi như `// lớp này ...` sẽ bị đếm nhầm). Tín hiệu chính là
**so khớp theo TÊN** — so tập tên type của tool với tập tên type trong nguồn — thay vì đếm
thô, vì so theo tên phát hiện được cả thiếu lẫn thừa và không bị nhiễu bởi đếm trùng.

**Cùng phạm vi file.** Công cụ và oracle bị ép cùng phân tích `src/main/java` (mã production),
loại trừ test và mã sinh lúc build. Đây là điều kiện bắt buộc để phép so sánh công bằng
(xem bài học ở mục 7).

**Đo precision quan hệ CALLS bằng lấy mẫu.** Tập cạnh CALLS thường lớn, không kiểm tay hết
được, nên dùng lấy mẫu ngẫu nhiên có hạt giống cố định (seed = 42, để tái lập). Với mỗi cạnh
mẫu (phương thức gọi → phương thức đích), kiểm tra trong mã nguồn của lớp gọi có xuất hiện
lời gọi tới tên phương thức đích hay không. Precision được báo cáo kèm khoảng tin cậy Wilson.

**Công cụ đo.** Toàn bộ tái lập bằng script: `eval/structural-accuracy/run-eval.ps1` (cấu
trúc + quan hệ) và `mcall-precision.ps1` (precision CALLS); kết quả lưu ở `report.md`/`.csv`,
`SUMMARY.md`, `mcall-precision.md`.

## 5. Kết quả

### 5.1 RQ1 — Trích xuất cấu trúc

| Hạng mục | spring-petclinic | spring-petclinic-rest |
|---|---:|---:|
| Types (class/interface/enum) | 25/25 = **100%** | 84/85 = **98.8%**¹ |
| Class (gồm DBModel) | 22/22 = 100% | 57/57 = 100% |
| Interface | 3/3 = 100% | 27/28 = 96.4%¹ |
| Entity (@Entity → DBModel) | 6/6 = 100% | 8/8 = 100% |
| Tên type sai (thiếu / thừa) | 0 / 0 | 1 / 0 |

¹ Một type "thiếu" ở petclinic-rest là `@interface PetAgeValidation`; công cụ phân loại nó
thành node `Annotation` thay vì `Interface`. Đây là **khác biệt phân loại**, không phải bỏ
sót — nếu tính gộp thì recall type thực chất là ~100%.

**Diễn giải.** Kết quả cho thấy công cụ trích xuất cấu trúc gần như tuyệt đối trên cả dự án
nhỏ (30 file) lẫn dự án vừa (87 file). Điểm đáng chú ý nhất là **không có type thừa (FP = 0)**
ở cả hai dự án: công cụ không "bịa" ra lớp không tồn tại. Đây là tính chất quan trọng cho một
công cụ dịch ngược, vì FP làm người dùng mất niềm tin nhanh hơn cả FN. Sai lệch duy nhất là
ranh giới phân loại giữa "annotation type" và "interface" — một quyết định thiết kế, có thể
tinh chỉnh, không phải lỗi trích xuất.

### 5.2 RQ2 — Trích xuất quan hệ

**Precision lời gọi phương thức (CALLS)** — spring-petclinic:

| Chỉ số | Giá trị |
|---|---:|
| Tổng cạnh CALLS | 64 |
| Cỡ mẫu (seed = 42) | 30 |
| Kiểm chứng được | 30 |
| Đúng | 30 |
| **Precision** | **100%** |
| Wilson 95% CI | **[88.6%, 100%]** |

**Diễn giải.** Trên mẫu 30/64 cạnh, mọi cạnh CALLS đều được xác nhận có lời gọi tương ứng
trong mã nguồn — không phát hiện cạnh bịa. Với cỡ mẫu 30, khoảng tin cậy Wilson là
[88.6%, 100%], nghĩa là precision thực (nếu kiểm toàn bộ) rất khả năng nằm trên ~89%. Đây là
proxy ở mức file (xem giới hạn ở mục 7); để chặt hơn có thể kiểm ở mức thân phương thức.

**Điểm cuối API (endpoint).** Trên spring-petclinic (kiểu verb-mapping cổ điển): 16/17 =
**94.1%**. Một endpoint bị thiếu là ứng viên cho phân tích nguyên nhân chi tiết ở công việc
sau. Với spring-petclinic-rest, xem bài học phạm vi ở mục 7 (endpoint thật nằm trong mã sinh
lúc build).

**Phụ thuộc tiêm (INJECTS).** Ban đầu công cụ chỉ bắt tiêm qua field `@Autowired`, nên với
dự án dùng **constructor injection** (mặc định Spring hiện đại) kết quả là `INJECTS = 0` —
đồ thị phụ thuộc bị thiếu. Sau khi bổ sung nhận diện constructor injection cho các Spring
bean, spring-petclinic đo được **INJECTS = 0 → 6** (đúng chuỗi controller → service →
repository). Đây là minh chứng cụ thể cho vòng lặp "đo → phát hiện thiếu → sửa → đo lại".

### 5.3 RQ3 — Suy luận Use Case

Thuật toán suy luận được kiểm bằng bộ **6 fixture gán nhãn** phủ các cấu trúc dự án: (a) REST
có phân quyền `@PreAuthorize`, (b) chỉ có service, (c) chỉ có entity, (d) auth (register/login),
(e) interface + impl, (f) MVC. Với mỗi fixture, mô hình đúng (ground truth) được gán nhãn thủ
công; công cụ đạt **F1 = 1.0** trên cả ba chiều actor / use case / quan hệ. Bộ đánh giá này
chạy **tự động trong CI** như lưới chống hồi quy: mọi thay đổi thuật toán sau này đều được
chấm điểm bằng số.

Hai cải tiến nổi bật được xác nhận qua bộ fixture:
- **Suy luận actor từ phân quyền thật:** vai trò `@PreAuthorize` (Seller, Store Manager…)
  trở thành actor riêng thay vì gộp hết về "User".
- **Fallback nhiều tầng:** dự án không có REST API (chỉ service / entity / lớp thường) vẫn
  sinh được sơ đồ use case, thay vì trả về rỗng.

## 6. So sánh với công cụ liên quan (Related work)

VibeGraph nằm trong nhóm công cụ **dịch ngược & trực quan hóa kiến trúc mã nguồn**. Bảng sau
so sánh định tính theo các tiêu chí chính. (Các đặc điểm của công cụ ngoài là mô tả tổng quát
từ tài liệu chính thức của chúng — tác giả cần **kiểm chứng và trích dẫn** trước khi nộp.)

| Tiêu chí | VibeGraph | jQAssistant | Structure101 | Sourcetrail | SciTools Understand | IntelliJ Diagrams |
|---|---|---|---|---|---|---|
| Nguồn phân tích | Mã nguồn Java (AST) | Bytecode + scan | Bytecode | Mã nguồn (đa ngôn ngữ) | Mã nguồn (đa ngôn ngữ) | Mã nguồn (trong IDE) |
| Lưu trữ đồ thị | Neo4j | Neo4j | Nội bộ | Nội bộ | CSDL riêng | Nội bộ |
| Truy vấn đồ thị | Có (qua API) | Cypher | Hạn chế | Tìm kiếm | API/script | Không |
| Sơ đồ UML lớp | Có | Không (báo cáo) | Có | Không | Có | Có |
| **Sơ đồ Use Case "as-built"** | **Có (điểm mới)** | Không | Không | Không | Không | Không |
| Suy luận actor/goal | Có (heuristic + LLM) | Không | Không | Không | Không | Không |
| Giao diện web tương tác | Có (Sigma.js) | Hạn chế | Desktop | Desktop | Desktop | Trong IDE |
| Cập nhật realtime khi sửa file | Có (watcher) | Không | Không | Không | Không | Một phần |
| Mã nguồn mở / miễn phí | Đồ án | Có | Thương mại | Có (đã lưu trữ) | Thương mại | Kèm IDE |

**Định vị của VibeGraph.** Đa số công cụ hiện có tập trung vào **cấu trúc tĩnh** (sơ đồ lớp,
phụ thuộc gói/lớp) hoặc **điều hướng mã**. Điểm khác biệt chính của VibeGraph là:

1. **Sinh sơ đồ Use Case "as-built" trực tiếp từ mã** — suy luận actor và mục tiêu nghiệp vụ
   từ controller/endpoint/phân quyền và, khi không có API, từ tầng service/entity. Đây là
   khả năng gần như không công cụ phổ biến nào cung cấp: chúng dừng ở mức lớp/phụ thuộc.
2. **Đồ thị tri thức trên Neo4j + giao diện web tương tác + realtime** trong một sản phẩm
   thống nhất. jQAssistant cũng dùng Neo4j nhưng thiên về kiểm tra ràng buộc kiến trúc bằng
   Cypher, không có sơ đồ use case hay giao diện đồ thị tương tác cho người dùng cuối.

**So sánh đánh giá.** Nhiều công cụ thương mại không công bố số liệu độ chính xác trích xuất
công khai; luận văn này đóng góp một **bộ đánh giá định lượng, tái lập được** với ground truth
độc lập — bản thân điều này cũng là một giá trị (đo được thay vì tuyên bố).

## 7. Đe dọa tính hợp lệ (Threats to validity)

**Tính hợp lệ nội tại (internal).**
- *Oracle bằng regex* là gần đúng. Đã giảm thiểu bằng (a) loại bỏ comment/chuỗi trước khi
  khớp, (b) dùng so khớp theo tên thay vì đếm — cho 0 sai lệch tên trên petclinic. Regex vẫn
  có thể lệch với khai báo generic/lồng nhau phức tạp.
- *Precision CALLS là proxy mức file:* một phương thức trùng tên trong cùng file có thể gây
  "chấp nhận nhầm". Do đó 100% nên hiểu là ước lượng **cận trên** với N = 30 (CI [88.6, 100]);
  công việc sau nên kiểm ở mức thân phương thức.

**Tính hợp lệ về xây dựng (construct).**
- Độ chính xác Use Case được đo trên **fixture tổng hợp** gán nhãn, không phải dự án thật —
  vì "sơ đồ use case đúng" mang tính chủ quan, khó có ground truth khách quan trên dự án thật.
  Fixture đóng vai trò kiểm chứng hành vi thuật toán, không phải đo mức độ "hài lòng" của con
  người. Đánh giá người dùng (user study) là hướng bổ sung.

**Bài học phương pháp (đã phát hiện và sửa trong quá trình đánh giá).** Chẩn đoán ban đầu
"endpoint spring-petclinic-rest chỉ đạt 1/11 ≈ 9%" là **sai**: đã so sánh endpoint mức phương
thức với `@RequestMapping` mức lớp (vốn chỉ là tiền tố đường dẫn). Các endpoint thật của
petclinic-rest nằm trong **interface sinh lúc build** (OpenAPI generator, thư mục
`target/generated-sources/`), ngoài phạm vi phân tích mã nguồn; endpoint duy nhất thực sự có
trong mã nguồn đã được nhận diện đúng. Bài học: **công cụ và oracle phải cùng phạm vi file**;
so lệch phạm vi tạo ra khoảng cách giả. Việc ghi lại và sửa sai này thể hiện tính nghiêm túc
của quy trình đo.

**Tính hợp lệ ngoại tại (external / khái quát hóa).**
- Chỉ 2 dự án thật cho phần cấu trúc và đều là Spring/Java. Kết quả chưa khái quát cho
  framework khác (JAX-RS, Quarkus) hay ngôn ngữ khác. Cần mở rộng số dự án để tăng độ tin cậy
  của khái quát hóa.

## 8. Bàn luận

Số liệu ủng hộ giả thuyết rằng cách tiếp cận dựa trên AST + đồ thị của VibeGraph trích xuất
cấu trúc và lời gọi chính xác cao cho mã Java thông thường. Các sai lệch quan sát được đều
**giải thích được bằng nguyên nhân cụ thể** (annotation-type vs interface; endpoint trong mã
sinh; constructor injection trước khi bổ sung) chứ không phải lỗi ngẫu nhiên — điều này quan
trọng vì nó cho biết đường hướng cải tiến rõ ràng. Hai giới hạn đã được xử lý ngay trong quá
trình đánh giá (constructor injection: INJECTS 0→6; `@RequestMapping(method=)` → verb đúng),
minh họa giá trị của việc có bộ đo: phát hiện được thì sửa được và chứng minh được mức cải
thiện.

## 9. Kết luận chương

VibeGraph đạt độ chính xác cao ở lõi trích xuất: type/entity ~100% (không có phần tử thừa),
precision lời gọi 100% trên mẫu (CI 95% [88.6%, 100%]), nhận diện endpoint 94.1% với controller
kiểu verb-mapping, và suy luận use case đạt F1 = 1.0 trên bộ fixture đại diện. Đóng góp phương
pháp là một quy trình đánh giá **định lượng, độc lập, tái lập được**. Giới hạn còn mở — endpoint
từ mã sinh lúc build / kế thừa interface, và bề rộng dự án — được nêu rõ làm hướng phát triển.

## Phụ lục — Cách tái lập

Chi tiết ở `eval/structural-accuracy/README.md`. Lệnh chính:

```powershell
# Cấu trúc + quan hệ + endpoint
./eval/structural-accuracy/run-eval.ps1 -RepoPath <repo> -Name <name> -ProjectId <id>

# Precision lời gọi phương thức (CALLS)
./eval/structural-accuracy/mcall-precision.ps1 -RepoPath <repo> -ProjectId <id> -SampleSize 30
```

Báo cáo sinh ra: `report.md`/`report.csv` (per-repo), `SUMMARY.md` (tổng hợp),
`mcall-precision.md` (precision + CI).
