# Walkthrough mã nguồn lõi — để bảo vệ (VibeGraph)

> Giải thích 2 file hội đồng dễ đào sâu nhất, theo mạch dữ liệu. Đọc kèm code thật:
> - `src/main/java/com/vibegraph/parser/visitor/SpringAnnotationVisitor.java`
> - `src/main/java/com/vibegraph/diagram/service/impl/UseCaseInferenceEngine.java`

---

## 1. SpringAnnotationVisitor — từ mã Spring ra APIEndpoint / HANDLES_ROUTE / INJECTS

Đây là một **Visitor** (mẫu Visitor trên cây cú pháp AST của JavaParser). JavaParser dựng AST
của mỗi file `.java`; visitor "thăm" từng khai báo lớp và rút thông tin.

**Điểm vào:** `visit(ClassOrInterfaceDeclaration n, ...)` — chạy cho mỗi lớp/interface:
1. Lấy FQCN của lớp, tiền tố đường dẫn từ `@RequestMapping` cấp lớp (`extractRequestMappingPath`),
   vai trò bảo mật cấp lớp (`extractRole`), và cờ lớp có phải "render view" (@Controller thường).
2. Duyệt method → `processMethodAnnotations(...)`.
3. Duyệt field → `processFieldAnnotations(...)` (tiêm qua field).
4. Gọi `processConstructorInjection(...)` (tiêm qua constructor — phần cải tiến F2).

### 1.1 Endpoint (RQ2 – endpoint)
Trong `processMethodAnnotations`, với mỗi annotation trên method gọi
`resolveHttpMethods(name, annotation)` để ra danh sách HTTP verb:
- `@GetMapping/@PostMapping/...` → một verb cố định (bảng `httpMethodFor`).
- `@RequestMapping` → mặc định là "REQUEST"; **nếu có `method = RequestMethod.X`** thì
  `extractRequestMethods` bóc ra verb thật (GET/POST/...) — đây là **cải tiến F1**. Nếu khai
  báo nhiều verb `{GET, POST}` thì sinh nhiều endpoint.

Với mỗi verb + đường dẫn (ghép tiền tố lớp qua `combinePaths`), tạo:
- node `APIEndpoint` (thuộc tính `httpMethod`, `routePath`, có thể `requiredRole`, `view`),
- cạnh `HANDLES_ROUTE` từ FQCN method → routeId.

Vai trò bảo mật lấy trong `extractRole` + `pickRole` (đọc `@PreAuthorize/@Secured/@RolesAllowed`,
regex token vai trò, ưu tiên ADMIN). Đây là dữ liệu cho suy luận actor ở engine use case.

### 1.2 INJECTS – tiêm phụ thuộc (RQ2 – INJECTS, cải tiến F2)
- `processFieldAnnotations`: field có `@Autowired/@Inject/@Resource`, hoặc field `final` khi lớp
  có Lombok `@RequiredArgsConstructor` → cạnh `INJECTS` (lớp → kiểu field).
- `processConstructorInjection` (mới): **chỉ với Spring bean** (`isSpringBean` kiểm các stereotype
  `@Service/@Repository/@Controller/@RestController/@Component/@Configuration`):
  - Chọn constructor: 1 cái → dùng luôn; nhiều cái → chỉ cái có `@Autowired`.
  - Mỗi tham số (bỏ primitive và kiểu giá trị như `String/Integer` qua `NON_BEAN_TYPES`) →
    cạnh `INJECTS` (lớp → kiểu tham số), thuộc tính `annotation = "Constructor"`.
  - **Vì sao chặn ở Spring bean:** để không coi mọi POJO có constructor là "tiêm" (tránh FP).

**Câu hỏi dễ bị hỏi:** *"Vì sao trước đây INJECTS=0 trên petclinic?"* → vì petclinic dùng
constructor injection (không `@Autowired` field), mà code cũ chỉ bắt field. Thêm
`processConstructorInjection` → bắt đúng → 6 cạnh.

---

## 2. UseCaseInferenceEngine — từ đồ thị ra mô hình Use Case

Đầu vào: đồ thị dự án (`GraphDataResponse`). Đầu ra: `InferenceResult` (actors, useCases,
relations, warnings). Điểm vào: `infer(graph, mode)`. Mô hình là **tất định** (không phụ thuộc
LLM); LLM chỉ làm đẹp nhãn ở tầng sau.

### 2.1 Luồng chính (khi có API)
1. **`collectEndpoints(graph)`**: gom node `APIEndpoint`/`Route` + cạnh `HANDLES_ROUTE` thành
   danh sách `Endpoint (httpMethod, path, controller, requiredRole)`. Loại trừ đường tĩnh/hạ
   tầng/trang view (`isExcluded`, `isViewPageRoute`).
2. Với mỗi endpoint:
   - **Auth?** `authKind` phát hiện register/login → gom vào goal của actor **Guest**
     ("Register account" / "Log in").
   - **Domain:** `inferDomainGuess` lấy tên nghiệp vụ (ưu tiên từ tên controller, sau đó path),
     kèm độ tin cậy.
   - **Actor:** `inferActor` — mạnh nhất là role thật từ `@PreAuthorize`; nếu không thì heuristic
     path (`/admin` → Admin), mặc định User (đánh dấu "guessed"). **Cải tiến:** role có tên riêng
     (SELLER, STORE_MANAGER) → actor riêng qua `roleToActorName` (strip `ROLE_`, Title Case).
   - Gom theo `domainKey` (tách admin vs public), cộng dồn số endpoint và số endpoint ghi
     (`isMutating`: POST/PUT/PATCH/DELETE).
3. **Sinh goal/domain:** mỗi domain thành 1 use case "Manage X" hoặc "View X". Quyết định
   Manage/View theo **tỉ lệ ghi** vượt ngưỡng `WRITE_RATIO_THRESHOLD = 0.25` (hoặc admin-scoped),
   trừ domain báo cáo (analytics...) giữ "View".
4. **Dọn trùng & phân cấp:** `mergeDuplicateNamedUseCases` (gộp goal trùng tên), 
   `disambiguateScopedDuplicates` (gắn hậu tố "(All)"/"(Own)"), và tổng quát hóa actor
   Admin `--|>` User (`generalization`) + bỏ liên kết Admin trùng với User.

### 2.2 Fallback khi KHÔNG có endpoint (điểm nổi bật)
Nếu `collectEndpoints` rỗng (dự án không REST API), `inferDomainsFromClasses(graph, domains)`
suy luận từ **tầng lớp** theo thứ tự ưu tiên (tier):
1. **Service** (`@Service` hoặc tên `*Service/*Manager/*Facade/*UseCase/*Interactor`, kể cả
   interface): mỗi method public là một thao tác nghiệp vụ.
2. **Controller** (nếu không có service).
3. **Entity** (`@Entity`/DBModel) → "Manage X".
4. **Generic** (last-resort): lớp nghiệp vụ thường có method (vd CLI).

Chi tiết quan trọng:
- **Bỏ accessor** (`isAccessorName`: get*/set*/is*) khi xét entity/generic → entity chỉ có
  getter vẫn ra "Manage X" chứ không tụt thành "View".
- Verb Manage/View dựa trên tên method ghi (`MUTATING_PREFIXES`: create/add/update/delete/ship/
  approve/...). **Cải tiến:** đã bổ sung nhiều động từ nghiệp vụ (ship, dispatch, approve...).
- Auth (register/login trong tên method) → Guest goals; tên lớp bắt đầu "Admin" → actor Admin.
- Đặt tên domain từ tên lớp qua `classDomainName` (bỏ hậu tố Service/Controller/Entity...).

Nhờ chuỗi tier này, dự án Java bất kỳ **gần như luôn ra được use case**, không báo rỗng oan.

### 2.3 Tầng sau (ngoài engine)
- `BaLabelBeautifier`: làm đẹp nhãn (User → "Registered User", chuẩn hoá).
- `GenericRelationInferer`: suy ra `<<include>>` cho service dùng chung ≥ 2 use case.
- `UseCaseSemanticRefiner` (LLM, tùy chọn): chỉ **đổi nhãn** cho bớt gượng, temperature 0 +
  cache theo hash → tất định; lỗi thì giữ nhãn gốc. **Không** thêm/bớt actor/use case.
- `UmlUseCaseRenderer` + FE `umlUseCaseSvg.ts`: render PlantUML/Mermaid/SVG.

**Câu hỏi dễ bị hỏi:**
- *"Use case có phải do AI bịa?"* → Không. Suy luận là heuristic tất định từ cấu trúc mã; LLM
  chỉ chỉnh chữ cho dễ đọc và có thể tắt.
- *"Dự án không có controller thì sao?"* → fallback service/entity/generic (mục 2.2).
- *"Phân biệt Manage vs View kiểu gì?"* → theo tỉ lệ thao tác ghi (ngưỡng 0.25) hoặc động từ
  method; có ngưỡng rõ ràng, không tùy tiện.

---

## 3. Bản đồ nhanh: câu hỏi → chỗ trả lời trong code

| Hội đồng hỏi | Trả lời ở |
|---|---|
| Bắt endpoint thế nào | `SpringAnnotationVisitor.processMethodAnnotations` / `resolveHttpMethods` |
| `@RequestMapping(method=)` | `extractRequestMethods` (F1) |
| Bắt tiêm phụ thuộc | `processFieldAnnotations` + `processConstructorInjection` (F2) |
| Lấy vai trò bảo mật | `extractRole` / `pickRole` |
| Suy luận actor | `UseCaseInferenceEngine.inferActor` / `roleToActorName` |
| Suy luận domain/goal | `inferDomainGuess` + vòng sinh goal + `WRITE_RATIO_THRESHOLD` |
| Dự án không có API | `inferDomainsFromClasses` (tier service/controller/entity/generic) |
| Vai trò của LLM | `UseCaseSemanticRefiner` / `LlmUseCaseRefiner` (chỉ relabel, tất định) |
