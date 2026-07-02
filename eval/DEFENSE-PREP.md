# Chuẩn bị bảo vệ — Truy vết số liệu + Hỏi đáp (VibeGraph)

> Tài liệu nội bộ để anh HIỂU và BẢO VỆ được số liệu, không phải để nộp.
> Mục B: truy 3 con số từ code → cách đo → kết quả. Mục C: câu hỏi hội đồng + gợi ý trả lời.

---

# B. Truy vết số liệu end-to-end

Nguyên tắc trả lời khi bị hỏi "con số này ở đâu ra": luôn kể theo mạch
**(1) tool sinh dữ liệu thế nào → (2) ground truth/đo lấy từ đâu → (3) so sánh ra số**.

## B1. "INJECTS: 0 → 6" trên spring-petclinic

**Bối cảnh.** INJECTS là cạnh biểu diễn quan hệ "lớp A phụ thuộc (được tiêm) lớp B".

**(1) Tool sinh cạnh thế nào — trong `SpringAnnotationVisitor.java`:**
- *Trước cải tiến:* chỉ quét field có `@Autowired`/`@Inject` (`processFieldAnnotations`).
  spring-petclinic dùng **constructor injection** (không có `@Autowired` trên field), nên
  không field nào khớp → **0 cạnh INJECTS**.
- *Sau cải tiến:* thêm `processConstructorInjection()`. Logic:
  1. Chỉ xét lớp là **Spring bean** (có `@Service/@Repository/@Controller/@RestController/
     @Component/@Configuration`) — để không coi mọi POJO có constructor là DI.
  2. Chọn constructor được tiêm: nếu 1 constructor → dùng nó (Spring auto-wire); nếu nhiều →
     chỉ cái có `@Autowired`.
  3. Với mỗi tham số constructor: bỏ primitive + kiểu giá trị (`String/Integer/...`), còn lại
     phát ra cạnh `INJECTS` (lớp → kiểu tham số).

**(2) Đo lấy từ đâu:** phân tích lại petclinic → lấy đồ thị qua API
`GET /api/projects/{id}/graph` → đếm cạnh `type == "INJECTS"`. Việc đếm nằm trong
`run-eval.ps1` (mục "Edges (tool distribution)").

**(3) Ra số:** trước = 0, sau = **6**. Sáu cạnh này đúng chuỗi controller → service →
repository của petclinic (mỗi controller tiêm 1–2 service, service tiêm repository).

**Câu chốt để nói:** *"Ban đầu tôi chỉ bắt tiêm qua field @Autowired nên bỏ sót constructor
injection — vốn là mặc định của Spring hiện đại. Tôi bổ sung nhận diện tham số constructor
cho các Spring bean, chạy lại phép đo trên petclinic thì số cạnh INJECTS tăng từ 0 lên 6,
khớp đúng quan hệ tiêm thực tế."*

## B2. "CALLS precision = 100%, CI 95% [88.6%, 100%]"

**(1) Tool sinh cạnh CALLS:** `MethodVisitor` phát cạnh CALLS khi resolve được lời gọi
phương thức trong cùng project (dùng JavaParser Symbol Solver). petclinic có 64 cạnh CALLS.

**(2) Cách đo precision — `mcall-precision.ps1`:**
- Lấy toàn bộ 64 cạnh CALLS từ đồ thị.
- **Lấy mẫu ngẫu nhiên có hạt giống cố định (seed=42)** → chọn 30 cạnh (tái lập được).
- Với mỗi cạnh (caller → callee): mở **mã nguồn file của caller**, kiểm tra có xuất hiện lời
  gọi tới tên phương thức callee (regex `tênCallee(`) hay không. Có → đúng.
- Precision = đúng / kiểm-chứng-được. Khoảng tin cậy dùng công thức **Wilson 95%**.

**(3) Ra số:** 30/30 đúng → precision 100%; với N=30, Wilson CI = **[88.6%, 100%]**.

**Điểm trung thực phải chủ động nói (nếu không hội đồng sẽ bắt):** đây là **proxy mức file** —
nếu trong cùng file có phương thức trùng tên thì có thể "chấp nhận nhầm". Vì vậy 100% là ước
lượng, và tôi báo cáo kèm CI + cỡ mẫu; muốn chặt hơn thì kiểm ở mức thân phương thức.

**Vì sao dùng Wilson mà không phải khoảng chuẩn:** khi tỉ lệ = 100% (p=1), khoảng chuẩn cho
ra [100%,100%] vô lý; Wilson vẫn cho cận dưới hợp lý (88.6%) — phù hợp mẫu nhỏ và tỉ lệ biên.

## B3. "Type extraction ~100%, 0 type thừa"

**(1) Tool sinh node type:** `ClassVisitor` phát node `Class`/`DBModel`/`Interface`/`Enum`.

**(2) Ground truth ĐỘC LẬP — `run-eval.ps1`:** không dùng JavaParser (tránh đo vòng), mà đếm
trực tiếp trên mã nguồn `.java` bằng regex, **sau khi xóa comment + chuỗi**. Rồi so **theo TÊN**:
tập tên type của tool so với tập tên type nguồn → liệt kê "thiếu" (có ở nguồn, thiếu ở tool)
và "thừa" (có ở tool, không ở nguồn).

**(3) Ra số:** petclinic 25/25 (0 thiếu, 0 thừa); petclinic-rest 84/85 (1 "thiếu" là
`@interface` bị phân loại thành node Annotation — khác biệt phân loại, không phải sót).

**Bài học phải kể (ăn điểm phương pháp):** lúc đầu recall types chỉ 78% — hóa ra regex đếm
nhầm chữ "class" trong comment (vd `// lớp này...`). Sau khi xóa comment và **đổi từ đếm thô
sang so theo tên**, ra ~100% với 0 thừa. → cho thấy tôi kiểm soát được tính hợp lệ của chính
phép đo.

---

# C. Câu hỏi hội đồng hay hỏi + gợi ý trả lời

> Trả lời ngắn, thẳng, có số. Nếu là giới hạn thì THỪA NHẬN + nói hướng khắc phục
> (thừa nhận giới hạn một cách chủ động luôn ghi điểm hơn là chống chế).

## C1. Về đo lường & độ chính xác

**H: Em đo độ chính xác thế nào?**
Đ: Định lượng với ground truth độc lập. Cấu trúc: đếm từ mã nguồn (regex trên mã đã xóa
comment) rồi so theo tên → precision/recall. Quan hệ CALLS: lấy mẫu ngẫu nhiên + kiểm chứng
trên mã nguồn, báo cáo kèm khoảng tin cậy Wilson 95%. Tất cả tái lập bằng script trong `eval/`.

**H: Vì sao ground truth đáng tin, không phải "tự chấm mình"?**
Đ: Vì ground truth KHÔNG dùng JavaParser — engine mà công cụ dựa vào. Tôi đếm độc lập bằng
regex trên văn bản nguồn, nên phép đo không "đo vòng".

**H: 100% có phải quá đẹp/đáng ngờ không?**
Đ: 100% chỉ ở precision CALLS trên mẫu 30, và tôi báo cáo kèm CI [88.6%, 100%] chứ không nói
tuyệt đối; đây là proxy mức file nên là ước lượng cận trên. Còn endpoint chỉ 94.1%, và có các
giới hạn tôi nêu rõ — tức không phải mọi thứ đều 100%.

**H: Cỡ mẫu 30 có đủ không?**
Đ: Đủ để cho một ước lượng khoảng: Wilson 95% CI là [88.6%, 100%]. Tăng mẫu sẽ thu hẹp
khoảng; đây là đánh đổi giữa công sức kiểm tay và độ chặt, và tôi đã nêu là hướng mở rộng.

## C2. Về tính hợp lệ / giới hạn

**H: Điểm yếu lớn nhất của đánh giá là gì?**
Đ: Ba điểm: (1) precision CALLS là proxy mức file; (2) độ chính xác use case đo trên fixture
tổng hợp chứ chưa có user study; (3) bề rộng mới 2 dự án thật, đều Spring/Java. Cả ba đều được
ghi trong mục Threats to validity, kèm hướng khắc phục.

**H: Vụ endpoint petclinic-rest 9% là sao?**
Đ: Đó là một chẩn đoán SAI ban đầu của tôi mà tôi đã phát hiện và sửa: tôi lỡ so endpoint mức
phương thức với `@RequestMapping` mức lớp (chỉ là tiền tố đường dẫn). Endpoint thật của
petclinic-rest nằm trong interface sinh lúc build (OpenAPI), ngoài phạm vi phân tích mã nguồn.
Bài học: công cụ và oracle phải cùng phạm vi file. Tôi giữ lại câu chuyện này trong luận văn vì
nó cho thấy quy trình đo có tính tự kiểm.

## C3. Về kỹ thuật

**H: Vì sao chọn JavaParser + Symbol Solver mà không phân tích bytecode?**
Đ: Để phân tích trực tiếp mã nguồn (kể cả dự án chưa build được), và giữ được thông tin cấp
nguồn (vị trí dòng, annotation). Đánh đổi: khó resolve hơn bytecode ở vài trường hợp generic/DI.

**H: Vì sao lưu đồ thị bằng Neo4j?**
Đ: Vì bài toán bản chất là đồ thị (node = phần tử mã, cạnh = quan hệ); truy vấn quan hệ nhiều
bước (vd impact, call chain) tự nhiên bằng Cypher, hiệu quả hơn mô hình bảng quan hệ.

**H: LLM đóng vai trò gì? Có làm kết quả bất định không?**
Đ: LLM CHỈ tinh chỉnh NHÃN use case cho dễ đọc (relabel), không sinh ra actor/use case — phần
suy luận là heuristic tất định. LLM chạy temperature 0 và có cache theo hash đầu vào nên cùng
input cho cùng output; nếu LLM lỗi thì lùi về nhãn tất định. Vậy kết quả không phụ thuộc may rủi.

**H: Nếu dự án không phải Spring / không có API thì sao?**
Đ: Có fallback nhiều tầng: suy luận use case từ tầng service, rồi controller, rồi entity, rồi
lớp nghiệp vụ thường — nên vẫn sinh được sơ đồ thay vì rỗng. Đã kiểm bằng fixture tương ứng.

## C4. Về sơ đồ Use Case (điểm mới)

**H: Làm sao suy ra được actor và use case từ mã?**
Đ: Actor: từ phân quyền `@PreAuthorize` (vd ROLE_SELLER → actor Seller), hoặc heuristic từ
đường dẫn (/admin → Admin), mặc định là User; nếu không có API thì từ tầng lớp. Use case: gom
theo domain (từ tên controller/service/entity), chọn động từ "Manage/View" theo việc có thao
tác ghi hay không.

**H: Đánh giá use case "đúng" kiểu gì khi nó chủ quan?**
Đ: Chính vì chủ quan nên tôi không tuyên bố % trên dự án thật; thay vào đó dùng 6 fixture gán
nhãn (mô hình đúng do tôi định nghĩa) để kiểm tính đúng đắn & ổn định của thuật toán (F1=1.0),
chạy trong CI. Mức độ "hữu ích với người dùng" thì cần user study — tôi nêu là hướng phát triển.

## C5. Câu "bẫy" / mở rộng

**H: Công cụ của em khác gì IntelliJ/Structure101/jQAssistant?**
Đ: Chúng mạnh về cấu trúc tĩnh (sơ đồ lớp, phụ thuộc) hoặc điều hướng mã. Điểm khác biệt của
VibeGraph là **sinh sơ đồ Use Case "as-built" trực tiếp từ mã** (suy luận actor/goal) — thứ các
công cụ đó không có — cộng đồ thị Neo4j + giao diện web tương tác + cập nhật realtime.

**H: Nếu cho em thêm thời gian, em làm gì tiếp?**
Đ: (1) Kiểm precision CALLS ở mức thân phương thức + tăng mẫu; (2) mở rộng thêm dự án và
framework (JAX-RS/Quarkus); (3) user study cho sơ đồ use case; (4) bắt endpoint từ mã sinh
lúc build / kế thừa interface.

**H: Độ chính xác trên dự án lớn thì sao?**
Đ: Hiện đo trên dự án nhỏ–vừa (30–87 file). Với dự án rất lớn cần kiểm hiệu năng khâu tải đồ
thị — tôi nêu là giới hạn và hướng tối ưu (truy vấn có chọn lọc thay vì tải toàn bộ).

---

## Mẹo trình bày
- Luôn gắn con số với **cơ chế**: đừng nói "đạt 100%", hãy nói "đo thế này, ra thế này, nghĩa là...".
- Chủ động nêu giới hạn trước khi bị hỏi — thể hiện làm chủ vấn đề.
- Thuộc 3 mạch truy vết ở mục B; từ đó suy ra mọi con số khác.
