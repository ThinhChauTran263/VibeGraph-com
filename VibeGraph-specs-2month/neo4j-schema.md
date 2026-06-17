# VibeGraph — Neo4j Schema v1.0

**Phiên bản:** 1.0.0
**Ngày:** 2026-05-20
**Trạng thái:** Đã duyệt (đã xác nhận 7 quyết định thiết kế)

---

## 1. Các quyết định thiết kế (đã chốt)

| # | Quyết định | Lựa chọn |
|---|----------|--------|
| 1 | Multi-tenancy | `projectId` property trên mỗi node |
| 2 | Node identity | Composite key `(projectId, fullName, paramTypes)` |
| 3 | Polymorphism | 1 edge tới target tĩnh + `targetType` property |
| 4 | Constructor | `:Method` label + `kind="CONSTRUCTOR"` property |
| 5 | Source code | Snippet trên node + filesystem cho file đầy đủ |
| 6 | Rename | Xóa và tạo mới (không theo dõi rename) |
| 7 | CALLS integrity | `MERGE` theo composite key + stub fallback |

> **Implementation status sau audit 2026-05-30:** tài liệu này là schema/data-contract mục tiêu. Code Sprint 1 dùng raw Neo4j Driver và migration `V1__init_schema.cypher`, nhưng `Neo4jGraphRepository.upsertNodes` hiện MERGE node theo `{projectId, fullName}` rồi set label/properties; `upsertEdges` tạo `External` stub cho endpoint thiếu. Parser hiện chưa emit `Package`/`File` node, chưa emit `OWNS`/`CONTAINS`/`DEFINES`, và chưa tạo Method stub khi CALLS unresolved. Các ví dụ Cypher ở mục 5 vì vậy là target patterns, không phải copy chính xác từ implementation hiện tại.

---

## 2. Nhãn node

### 2.1 `:Project`
Project gốc, là điểm vào cho mỗi truy vấn.

```cypher
(:Project {
  id: STRING,                  // UUID, khóa chính
  name: STRING,                // tên hiển thị
  rootPath: STRING,            // contract mục tiêu; implementation hiện tại ghi property `path`
  createdAt: DATETIME,
  lastAnalyzedAt: DATETIME,
  analysisStatus: STRING       // "PENDING" | "ANALYZING" | "READY" | "ERROR"
})
```

### 2.2 `:Package`

```cypher
(:Package {
  projectId: STRING,           // FK → Project.id
  fullName: STRING,            // "com.example.service"
  name: STRING                 // "service"
})
```

### 2.3 `:File`

```cypher
(:File {
  projectId: STRING,
  filePath: STRING,            // đường dẫn tương đối từ rootPath, ví dụ "example/source/path/User.java"
  name: STRING,                // "User.java"
  lineCount: INTEGER,
  checksum: STRING,            // SHA-256, dùng để bỏ qua khi quét incremental
  lastModified: LONG           // epoch millis
})
```

### 2.4 `:Class`

```cypher
(:Class {
  projectId: STRING,
  fullName: STRING,            // "com.example.UserService"
  name: STRING,                // "UserService"
  filePath: STRING,
  lineNumber: INTEGER,
  endLine: INTEGER,
  visibility: STRING,          // "public" | "protected" | "private" | "package"
  isAbstract: BOOLEAN,
  isFinal: BOOLEAN,
  isStatic: BOOLEAN,           // chỉ true với inner static class
  isInner: BOOLEAN,
  springLayer: STRING,         // "CONTROLLER" | "SERVICE" | "REPOSITORY" | "COMPONENT" | "CONFIG" | "ENTITY" | "NONE"
  springAnnotations: LIST<STRING>,  // ["@RestController", "@Validated"]
  signatureSnippet: STRING     // "public class UserService implements IUserService"
})
```

### 2.5 `:Interface`

```cypher
(:Interface {
  projectId: STRING,
  fullName: STRING,
  name: STRING,
  filePath: STRING,
  lineNumber: INTEGER,
  endLine: INTEGER,
  visibility: STRING,
  isInner: BOOLEAN,
  signatureSnippet: STRING
})
```

### 2.6 `:Enum`

```cypher
(:Enum {
  projectId: STRING,
  fullName: STRING,
  name: STRING,
  filePath: STRING,
  lineNumber: INTEGER,
  endLine: INTEGER,
  visibility: STRING,
  values: LIST<STRING>,        // ["ACTIVE", "INACTIVE", "BANNED"]
  signatureSnippet: STRING
})
```

### 2.7 `:Method`
**Bao gồm cả constructor và static initializer.**

```cypher
(:Method {
  projectId: STRING,
  fullName: STRING,            // "com.example.UserService.save"
  name: STRING,                // "save"
  paramTypes: LIST<STRING>,    // ["User"], ["User", "Options"]
  filePath: STRING,
  lineNumber: INTEGER,
  endLine: INTEGER,
  kind: STRING,                // "METHOD" | "CONSTRUCTOR" | "static_init"
  visibility: STRING,
  isAbstract: BOOLEAN,
  isStatic: BOOLEAN,
  isFinal: BOOLEAN,
  isSynchronized: BOOLEAN,
  returnType: STRING,          // "User" | "void" | "List<User>"
  paramNames: LIST<STRING>,    // ["user", "options"]
  throwsTypes: LIST<STRING>,   // ["IOException", "ParseException"]
  httpMethod: STRING,          // "GET"|"POST"|"PUT"|"DELETE"|"PATCH"|null
  routePath: STRING,           // "/api/users/{id}" | null
  springAnnotations: LIST<STRING>,
  isStub: BOOLEAN,             // true nếu được tạo do CALLS edge tới method chưa parse
  snippet: STRING              // 5-20 dòng mã nguồn
})
```

### 2.8 `:Field`

```cypher
(:Field {
  projectId: STRING,
  fullName: STRING,            // "com.example.UserService.userRepository"
  name: STRING,
  paramTypes: LIST<STRING>,    // [] (để đồng nhất composite key với Method)
  filePath: STRING,
  lineNumber: INTEGER,
  visibility: STRING,
  isStatic: BOOLEAN,
  isFinal: BOOLEAN,
  isInjected: BOOLEAN,         // có @Autowired hoặc constructor injection
  declaredType: STRING,        // "UserRepository" | "List<User>"
  springAnnotations: LIST<STRING>
})
```

### 2.9 `:Annotation`
Định nghĩa annotation tùy chỉnh (không phải nơi sử dụng).

```cypher
(:Annotation {
  projectId: STRING,
  fullName: STRING,            // "com.example.AuditLog"
  name: STRING,                // "AuditLog"
  filePath: STRING,
  lineNumber: INTEGER,
  retention: STRING,           // "RUNTIME" | "CLASS" | "SOURCE"
  target: LIST<STRING>         // ["METHOD", "TYPE"]
})
```

### 2.10 `:Route`
Endpoint HTTP được phát hiện từ các Spring annotation.

```cypher
(:Route {
  projectId: STRING,
  fullName: STRING,            // "GET /api/users/{id}"
  paramTypes: LIST<STRING>,    // [] (để đồng nhất)
  httpMethod: STRING,          // "GET"|"POST"|"PUT"|"DELETE"|"PATCH"
  routePath: STRING,           // "/api/users/{id}"
  handlerFullName: STRING,     // "com.example.UserController.getUser"
  filePath: STRING,
  lineNumber: INTEGER,
  consumesType: STRING,        // "application/json" | null
  producesType: STRING,        // "application/json" | null
  pathVariables: LIST<STRING>, // ["id"]
  middleware: LIST<STRING>     // ["@PreAuthorize", "@Validated"]
})
```

---

## 3. Loại edge

### 3.1 Edge cấu trúc

```cypher
// Project sở hữu (duyệt từ gốc)
(:Project)-[:OWNS]->(:Package|:File)

// Package chứa thành phần
(:Package)-[:CONTAINS]->(:Class|:Interface|:Enum|:Annotation)

// File định nghĩa
(:File)-[:DEFINES]->(:Class|:Interface|:Enum|:Annotation)

// Thành viên của Class
(:Class|:Interface|:Enum)-[:HAS_METHOD]->(:Method)
(:Class|:Enum)-[:HAS_FIELD]->(:Field)
(:Class|:Interface)-[:HAS_INNER]->(:Class|:Interface|:Enum)
```

> **Phase 2 — containment thực tế parser phát ra (đối soát code):**
> Hierarchy hiện được hiện thực bằng `CONTAINS` ở hai cấp:
> - `(:Project)-[:CONTAINS]->(:Package)` — nối ở tầng `AnalyzeServiceImpl`
>   (Project node có `fullName = projectId`).
> - `(:Package)-[:CONTAINS]->(:File)` — phát ra ở parser theo `package` declaration.
>   File không có package (default package) thì KHÔNG sinh Package node.
> - `(:File)-[:DEFINES]->(:Class|:Interface|:Enum|:Record|:Method|:Field|...)` giữ nguyên.
>
> `OWNS` và `(:Package)-[:CONTAINS]->(:Class...)` trong sơ đồ lý thuyết ở trên
> **chưa** được parser phát ra (canonical containment đi qua File như trên).

### 3.2 Kế thừa & Hiện thực

```cypher
(:Class)-[:EXTENDS]->(:Class)
(:Class)-[:IMPLEMENTS]->(:Interface)
(:Interface)-[:EXTENDS]->(:Interface)
(:Method)-[:OVERRIDES]->(:Method)        // method override (resolved tĩnh)
```

> **OVERRIDES — best-effort, conservative (Phase 2):** chỉ phát ra khi method bị
> override resolve được tới một method **trong project** (JavaParser-backed) ở
> ancestor type, khớp tên + kiểu tham số. Nếu cây kế thừa không resolve được
> (supertype external/unsolved) thì **không** phát ra edge nào — KHÔNG suy ra
> OVERRIDES chỉ từ annotation `@Override`. Hệ quả: override các method từ thư viện
> ngoài (vd `Object.toString`, JDK, Spring) hiện chưa được ghi nhận.

### 3.3 Phụ thuộc kiểu & import

```cypher
(:File)-[:IMPORTS]->(:Class|:Interface|:Enum|:Annotation)
(:Field)-[:TYPE_OF]->(:Class|:Interface|:Enum)
(:Method)-[:RETURNS]->(:Class|:Interface|:Enum)
(:Method)-[:PARAMETER_TYPE {position: INTEGER}]->(:Class|:Interface|:Enum)
(:Method)-[:THROWS]->(:Class)             // các kiểu exception
```

### 3.4 Đồ thị lỗi gọi (Quyết định #3 — Đa hình)

```cypher
(:Method)-[:CALLS {
  lineNumber: INTEGER,
  targetType: STRING,                     // "resolved" | "interface" | "unresolved"
  confidence: FLOAT,                      // 0.0-1.0
  rawTarget: STRING,                      // "userRepo.save" (cho debug khi unresolved)
  callKind: STRING                        // "method" | "constructor" | "static" | "lambda"
}]->(:Method)
```

**Quy ước `targetType`:**
- `"resolved"` (confidence=1.0): Symbol Solver resolve được tới class cụ thể
- `"interface"` (confidence=0.8): gọi qua interface ref, edge tới interface method
- `"unresolved"` (confidence=0.3): Symbol Solver thất bại, đoán khớp tốt nhất theo tên

```cypher
// Khởi tạo đối tượng (new X(...)) — Phase 2
(:Method|:Constructor)-[:INSTANTIATES {lineNumber: INTEGER}]->(:Class|:External)
```

> **INSTANTIATES — best-effort:** target lấy từ Symbol Solver khi resolve được
> (qualified name), ngược lại fallback theo import/cùng package. Type ngoài project
> trở thành node `External` lúc persist. Nguồn là method/constructor bao quanh biểu
> thức `new`.

### 3.5 Edge đặc thù Spring

```cypher
// Tiêm phụ thuộc (dependency injection)
(:Class)-[:INJECTS {
  via: STRING,                             // "constructor" | "field" | "setter"
  fieldName: STRING                        // tên field/param nhận inject
}]->(:Class|:Interface)

// Định tuyến HTTP — chiều canonical: METHOD -> ROUTE
// `(:Method)-[:HANDLES_ROUTE]->(:Route)`
// Ý nghĩa: method (handler) XỬ LÝ route được biểu diễn bởi node endpoint.
// Source = handler method, Target = endpoint node.
// LƯU Ý: parser phát ra node endpoint với label `APIEndpoint` (xem
// SpringAnnotationVisitor) còn schema/legend/frontend hiển thị là `Route`;
// hai tên này CHỈ là một loại node endpoint, không phải hai node khác nhau.
// Chiều `APIEndpoint -> Method` (route trỏ về method) là SAI — một số spec/audit
// cũ ghi ngược; backend (`EdgeData.of("HANDLES_ROUTE", methodFqcn, routeId)`) đã
// đúng canonical, KHÔNG đổi chiều backend.
(:Method)-[:HANDLES_ROUTE]->(:Route)

// Sử dụng annotation (mỗi lần annotation được dùng trên 1 element)
// Chiều canonical: ELEMENT -> ANNOTATION (element được annotate trỏ tới node
// annotation TYPE). Phase 2: phát ra cho Class/Interface/Enum/Record/DBModel,
// Method, Constructor, Field. Một node Annotation cho mỗi annotation TYPE (FQN),
// dedupe qua MERGE. Type ngoài project trở thành External rồi được enrich thành
// Annotation vì AnnotationVisitor cũng phát node Annotation cho nó.
// Resolve FQN best-effort: import → cùng package → java.lang cho built-in →
// simple name. `attributes` HIỆN CHƯA được populate (chỉ lưu `simpleName` trên node).
(:Class|:Interface|:Enum|:Record|:Method|:Constructor|:Field)-[:ANNOTATED_BY {
  lineNumber: INTEGER
}]->(:Annotation)
```

### 3.6 Body-level CPG — READS / WRITES / CATCHES (Phase 3, opt-in)

> **Bật/tắt:** mặc định TẮT qua `vibegraph.parser.deep-cpg-enabled` (env
> `VIBEGRAPH_PARSER_DEEP_CPG`). Khi tắt, KHÔNG có node `LocalVariable` và KHÔNG có
> edge READS/WRITES/CATCHES — graph y hệt Phase 2. Lý do: data-flow mức thân hàm
> có thể làm bùng nổ số node/edge, nên để opt-in. Khi bật, frontend vẫn mặc định
> ẩn các loại sâu này (node `LocalVariable` + READS/WRITES/CATCHES) để giữ graph dễ
> đọc, chỉ hiện qua "Show all".

```cypher
// Node biến cục bộ / tham số (chỉ khi deep CPG bật)
// id ổn định = "<methodId>#<varName>@<dòng>"; property kind = "local" | "parameter"
(:LocalVariable {kind: STRING, declaredType: STRING})

// Đọc giá trị: METHOD/CONSTRUCTOR -> Field | LocalVariable
(:Method|:Constructor)-[:READS]->(:Field|:LocalVariable)

// Ghi giá trị: METHOD/CONSTRUCTOR -> Field | LocalVariable
(:Method|:Constructor)-[:WRITES]->(:Field|:LocalVariable)

// Bắt exception: METHOD/CONSTRUCTOR -> kiểu exception (multi-catch => nhiều edge)
(:Method|:Constructor)-[:CATCHES {lineNumber: INTEGER}]->(:Class|:External)
```

**Direction (canonical):** nguồn luôn là method/constructor bao quanh; target là
biến/field được đọc-ghi hoặc kiểu exception được bắt.

**Ngữ nghĩa & giới hạn (conservative, intra-procedural):**
- READS phủ: đọc local/parameter; đọc field qua `field`, `this.field`, và field khai
  báo trong class hiện tại (so khớp tên, không cần symbol solver). Khai báo đơn thuần
  (không initializer) KHÔNG tính READS; initializer `int b = a + 1` => READS `a`,
  WRITES `b`.
- WRITES phủ: `=`, compound (`+=`, `-=`, …), `++`/`--`, `this.field = …`. Compound và
  inc/dec tính CẢ READS lẫn WRITES trên target.
- Mỗi method emit TỐI ĐA một READS và một WRITES cho mỗi target (dedupe) — giới hạn
  số edge ≈ 2 × số biến/field tham chiếu.
- CATCHES: mỗi loại trong multi-catch là một edge; type ngoài project => External stub.
- **KHÔNG hỗ trợ (tránh edge mơ hồ):** mutation collection (`list.add(...)`), gọi
  setter (`obj.setX(...)`), `obj.field`/`arr[i]` làm target (bỏ qua), data-flow
  liên-thủ-tục (cross-method), và field kế thừa truy cập bare (chỉ `this.field` mới
  chắc chắn). Lambda body quy về method bao quanh; thân method của anonymous class
  KHÔNG bị gộp nhầm vào method ngoài.
- Tham số được mô hình hoá như `LocalVariable {kind:"parameter"}` (không tạo node
  `Parameter` riêng); không tạo node `Exception` riêng (dùng Class/External).

> **STEP_IN_FLOW** (thứ tự thực thi) — xem §3.7 (đã hiện thực ở Phase 4 dưới dạng
> luồng SUY DIỄN từ CALLS, KHÔNG phải copy CALLS).

### 3.7 STEP_IN_FLOW — luồng thực thi suy diễn (Phase 4)

```cypher
// Bước luồng suy diễn: caller step -> callee next step
(:Method|:Constructor)-[:STEP_IN_FLOW {
  flowId: STRING,        // = entrypoint (handler method fullName)
  entrypoint: STRING,    // route handler method bắt đầu flow
  stepIndex: INTEGER,    // thứ tự bước trong flow (theo line của call)
  sourceKind: STRING,    // "ROUTE_FLOW"
  confidence: FLOAT,     // 0.9
  lineNumber: INTEGER
}]->(:Method|:Constructor)
```

**Ngữ nghĩa & khác biệt với CALLS:**
- `CALLS` = quan hệ gọi method TĨNH (static). KHÔNG đổi semantics, KHÔNG bị ảnh hưởng.
- `STEP_IN_FLOW` = view luồng nghiệp vụ/thực thi SUY DIỄN, bắt đầu từ route handler
  (`HANDLES_ROUTE` source) đi theo các CALLS in-project. Đây là **tập con đã lọc +
  dedupe**, KHÔNG phải bản sao của CALLS:
  - Chỉ gồm bước **đến được (reachable)** từ một entrypoint route.
  - Chỉ method/constructor **in-project** (call tới JDK/Spring/library vốn không nằm
    trong CALLS nên đương nhiên bị loại).
  - Tối đa MỘT edge cho mỗi cặp `(from,to)` (dedupe), khác với nhiều call-site.
  - Do đó `count(STEP_IN_FLOW)` thường < `count(CALLS)`.
- **Direction:** caller step → callee next step (Method/Constructor → Method/Constructor).
- **Ordering/metadata:** call trong một method được sắp theo `lineNumber` (rồi tên
  target) để xấp xỉ thứ tự nguồn; mỗi edge mang `flowId/entrypoint/stepIndex/
  sourceKind/confidence/lineNumber`.
- **Branches/loops/recursion:** mọi call in-project ở mọi nhánh đều được gồm vào
  (sắp xếp tất định theo line); mỗi method thăm tối đa MỘT lần/flow → chặn vòng lặp
  và đệ quy vô hạn.

**Giới hạn (documented):**
- Edge model MERGE theo `(from)-[type]->(to)` (không có khóa per-step), nên khi một
  call tham gia nhiều flow chỉ còn MỘT edge; metadata phản ánh flow ĐẦU TIÊN chạm
  tới (entrypoint xử lý theo thứ tự đã sort → tất định).
- `EdgeDto` của graph API hiện chỉ expose `confidence` + `lineNumber`; các property
  `flowId/entrypoint/stepIndex/sourceKind` được PERSIST trong Neo4j (truy vấn qua
  Cypher/MCP) nhưng KHÔNG nằm trong payload `/graph`.
- Entrypoint hiện chỉ là route handler (`HANDLES_ROUTE`). Public service method làm
  entrypoint là mở rộng tương lai, chưa bật.

---

## 4. Ràng buộc & Index

### 4.1 Ràng buộc tính duy nhất

```cypher
// Project
CREATE CONSTRAINT project_id_unique IF NOT EXISTS
FOR (p:Project) REQUIRE p.id IS UNIQUE;

// Package
CREATE CONSTRAINT package_unique IF NOT EXISTS
FOR (p:Package) REQUIRE (p.projectId, p.fullName) IS UNIQUE;

// File
CREATE CONSTRAINT file_unique IF NOT EXISTS
FOR (f:File) REQUIRE (f.projectId, f.filePath) IS UNIQUE;

// Class / Interface / Enum / Annotation
CREATE CONSTRAINT class_unique IF NOT EXISTS
FOR (c:Class) REQUIRE (c.projectId, c.fullName) IS UNIQUE;

CREATE CONSTRAINT interface_unique IF NOT EXISTS
FOR (i:Interface) REQUIRE (i.projectId, i.fullName) IS UNIQUE;

CREATE CONSTRAINT enum_unique IF NOT EXISTS
FOR (e:Enum) REQUIRE (e.projectId, e.fullName) IS UNIQUE;

CREATE CONSTRAINT annotation_unique IF NOT EXISTS
FOR (a:Annotation) REQUIRE (a.projectId, a.fullName) IS UNIQUE;

// Method (composite key vì overloading)
CREATE CONSTRAINT method_unique IF NOT EXISTS
FOR (m:Method) REQUIRE (m.projectId, m.fullName, m.paramTypes) IS UNIQUE;

// Field
CREATE CONSTRAINT field_unique IF NOT EXISTS
FOR (f:Field) REQUIRE (f.projectId, f.fullName) IS UNIQUE;

// Route
CREATE CONSTRAINT route_unique IF NOT EXISTS
FOR (r:Route) REQUIRE (r.projectId, r.httpMethod, r.routePath) IS UNIQUE;
```

### 4.2 Index tra cứu

```cypher
// Lọc theo project + tên (thanh tìm kiếm)
CREATE INDEX class_proj_name IF NOT EXISTS
FOR (c:Class) ON (c.projectId, c.name);

CREATE INDEX interface_proj_name IF NOT EXISTS
FOR (i:Interface) ON (i.projectId, i.name);

CREATE INDEX method_proj_name IF NOT EXISTS
FOR (m:Method) ON (m.projectId, m.name);

CREATE INDEX field_proj_name IF NOT EXISTS
FOR (f:Field) ON (f.projectId, f.name);

// Lọc theo Spring layer
CREATE INDEX class_spring_layer IF NOT EXISTS
FOR (c:Class) ON (c.projectId, c.springLayer);

// Tìm route theo path
CREATE INDEX route_path IF NOT EXISTS
FOR (r:Route) ON (r.projectId, r.routePath);

// Tra cứu File khi watcher kích hoạt
CREATE INDEX file_path IF NOT EXISTS
FOR (f:File) ON (f.projectId, f.filePath);

// Dọn dẹp stub method
CREATE INDEX method_stub IF NOT EXISTS
FOR (m:Method) ON (m.projectId, m.isStub);
```

### 4.3 Tìm kiếm fulltext

```cypher
// Thanh tìm kiếm: tìm node theo tên (case-insensitive, fuzzy)
CREATE FULLTEXT INDEX node_search IF NOT EXISTS
FOR (n:Class|Interface|Enum|Method|Field|Annotation)
ON EACH [n.name, n.fullName];
```

---

## 5. Cypher mẫu — các pattern CRUD

### 5.1 Chèn Class với pattern MERGE

> **Target pattern:** implementation hiện tại chưa tự tạo `Package`/`File` nodes và các edge `OWNS`/`CONTAINS`/`DEFINES` trong parser/repository generic path.

```cypher
// Upsert node Class + các edge
MERGE (proj:Project {id: $projectId})
MERGE (pkg:Package {projectId: $projectId, fullName: $packageName})
  ON CREATE SET pkg.name = $packageSimpleName
MERGE (file:File {projectId: $projectId, filePath: $filePath})
  ON CREATE SET file.name = $fileName
  SET file.lineCount = $lineCount,
      file.checksum = $checksum,
      file.lastModified = $lastModified
MERGE (cls:Class {projectId: $projectId, fullName: $classFullName})
  SET cls.name = $className,
      cls.filePath = $filePath,
      cls.lineNumber = $lineNumber,
      cls.endLine = $endLine,
      cls.visibility = $visibility,
      cls.isAbstract = $isAbstract,
      cls.springLayer = $springLayer,
      cls.springAnnotations = $springAnnotations,
      cls.signatureSnippet = $signatureSnippet
MERGE (proj)-[:OWNS]->(pkg)
MERGE (proj)-[:OWNS]->(file)
MERGE (pkg)-[:CONTAINS]->(cls)
MERGE (file)-[:DEFINES]->(cls)
RETURN cls
```

### 5.2 Chèn Method (xử lý trường hợp stub)

> **Target pattern:** method fullName hiện đã bao gồm chữ ký tham số trong parser; repository hiện MERGE generic theo `{projectId, fullName}` chứ không dùng đủ `{projectId, fullName, paramTypes}` trong tất cả nhánh upsert.

```cypher
// MERGE method, enrich nếu trước đây là stub
MERGE (m:Method {
  projectId: $projectId,
  fullName: $methodFullName,
  paramTypes: $paramTypes
})
ON CREATE SET m.isStub = false
SET m.name = $name,
    m.kind = $kind,
    m.filePath = $filePath,
    m.lineNumber = $lineNumber,
    m.endLine = $endLine,
    m.visibility = $visibility,
    m.returnType = $returnType,
    m.paramNames = $paramNames,
    m.throwsTypes = $throwsTypes,
    m.httpMethod = $httpMethod,
    m.routePath = $routePath,
    m.springAnnotations = $springAnnotations,
    m.snippet = $snippet,
    m.isStub = false                        // luôn unset stub khi parse class chứa nó
WITH m
MATCH (cls:Class {projectId: $projectId, fullName: $classFullName})
MERGE (cls)-[:HAS_METHOD]->(m)
RETURN m
```

### 5.3 Chèn edge CALLS (Quyết định #7 — stub fallback)

> **Target pattern:** code hiện tại chỉ emit CALLS cho resolved in-project calls. Khi upsert edge gặp endpoint chưa có node, repository tạo node `External` stub thay vì `Method {isStub:true}`.

```cypher
// MERGE callee — tạo stub nếu chưa parse class chứa nó
MERGE (callee:Method {
  projectId: $projectId,
  fullName: $calleeFullName,
  paramTypes: $calleeParamTypes
})
ON CREATE SET
  callee.isStub = true,
  callee.name = $calleeSimpleName
WITH callee
MATCH (caller:Method {
  projectId: $projectId,
  fullName: $callerFullName,
  paramTypes: $callerParamTypes
})
MERGE (caller)-[r:CALLS {lineNumber: $line}]->(callee)
SET r.targetType = $targetType,           // "resolved" | "interface" | "unresolved"
    r.confidence = $confidence,
    r.rawTarget = $rawTarget,
    r.callKind = $callKind
RETURN r
```

### 5.4 Xóa file (Quyết định #6 — đổi tên = xóa + tạo mới)

> **Implementation hiện tại:** `Neo4jGraphRepository.deleteFile(projectId, filePath)` xóa mỗi node có property `filePath` tương ứng bằng `DETACH DELETE`, sau đó prune các `External` stub cùng project không còn relationship nào. Code chưa đi theo `File-[:DEFINES]->typeNode` vì `File` node chưa được parser emit.

```cypher
// Khi watcher báo file bị xóa hoặc bị đổi tên
MATCH (f:File {projectId: $projectId, filePath: $filePath})
OPTIONAL MATCH (f)-[:DEFINES]->(typeNode)
OPTIONAL MATCH (typeNode)-[:HAS_METHOD]->(m:Method)
OPTIONAL MATCH (typeNode)-[:HAS_FIELD]->(fld:Field)
DETACH DELETE f, typeNode, m, fld
```

### 5.5 Dọn dẹp các stub method mồ côi

```cypher
// Chạy định kỳ hoặc sau khi xóa file
MATCH (m:Method {projectId: $projectId, isStub: true})
WHERE NOT (m)<-[:CALLS]-()
DETACH DELETE m
```

### 5.6 Lấy toàn bộ graph (cho frontend)

> **Implementation hiện tại:** `getFullGraph` đang dùng pattern tổng quát `MATCH (n {projectId}) OPTIONAL MATCH (n)-[r]->(m {projectId}) RETURN n,r,m`, chưa có phân trang/limit và chưa duyệt từ `Project-[:OWNS*]` vì structural edges chưa đủ.

```cypher
// Trả về tất cả nodes + edges cho 1 project
MATCH (p:Project {id: $projectId})-[:OWNS*1..3]->(n)
WHERE n:Class OR n:Interface OR n:Enum OR n:Method OR n:Field OR n:Route
WITH collect(DISTINCT n) AS nodes
UNWIND nodes AS src
MATCH (src)-[r]->(dst)
WHERE dst IN nodes
RETURN nodes, collect({
  source: id(src),
  target: id(dst),
  type: type(r),
  properties: properties(r)
}) AS edges
```

### 5.7 Vùng lân cận N-hop

```cypher
// Các node lân cận trong N hop của 1 node (cho Focus Mode)
MATCH (center:Method {projectId: $projectId, fullName: $fullName})
CALL apoc.path.subgraphAll(center, {
  maxLevel: $hops,
  relationshipFilter: "CALLS|HAS_METHOD|EXTENDS|IMPLEMENTS|INJECTS"
}) YIELD nodes, relationships
RETURN nodes, relationships
```

### 5.8 Phân tích tác động (blast radius)

```cypher
// Tìm tất cả method bị ảnh hưởng nếu sửa target
MATCH (target:Method {projectId: $projectId, fullName: $fullName})
MATCH path = (caller:Method)-[:CALLS*1..5]->(target)
RETURN DISTINCT caller, length(path) AS distance
ORDER BY distance ASC
```

### 5.9 Phát hiện Spring layer

```cypher
// Đếm nodes mỗi layer
MATCH (c:Class {projectId: $projectId})
WHERE c.springLayer IS NOT NULL
RETURN c.springLayer AS layer, count(c) AS count
ORDER BY count DESC
```

### 5.10 Tìm kiếm node (fulltext)

```cypher
// Thanh tìm kiếm: tìm theo tên
CALL db.index.fulltext.queryNodes("node_search", $query)
YIELD node, score
WHERE node.projectId = $projectId
RETURN node, labels(node) AS labels, score
ORDER BY score DESC
LIMIT 20
```

---

## 6. Quy tắc visibility (an toàn tenant)

**Mọi Cypher query PHẢI lọc theo `projectId`** để tránh rò rỉ dữ liệu giữa các project.

Tầng persistence dùng **raw Neo4j Java Driver** (`org.neo4j.driver.Driver`) với
Cypher có tham số (parameterized), KHÔNG dùng Spring Data Neo4j OGM / `@Node` entities. Mọi
truy cập đều đi qua interface `GraphRepository`; impl duy nhất là
`graph/repository/impl/neo4j/Neo4jGraphRepository.java`, nơi `projectId` luôn được
truyền vào dưới dạng tham số truy vấn:

```java
try (Session session = neo4jDriver.session()) {
    session.run(
        "MATCH (c:Class {projectId: $projectId, fullName: $fullName}) RETURN c",
        Map.of("projectId", projectId, "fullName", fullName)
    );
}
```

**Các pattern bị cấm** (sẽ bị từ chối khi review code):
```cypher
MATCH (c:Class) RETURN c                  // SAI thiếu bộ lọc projectId
MATCH (c:Class {fullName: $fn}) RETURN c  // SAI trùng fullName giữa các project
```

---

## 7. Mục tiêu hiệu năng

| Truy vấn | Mục tiêu | Index hỗ trợ |
|-------|--------|-------------|
| Lấy toàn bộ graph (1 project, 5000 node) | < 800ms | Duyệt `OWNS` + composite key |
| Vùng lân cận N-hop (3 hop) | < 500ms | APOC subgraphAll + lọc relationship |
| Phân tích tác động (5 hop) | < 1s | Index relationship `CALLS` |
| Tìm node theo tên | < 100ms | Fulltext `node_search` |
| Chèn 1 file (10 class, 50 method, 100 lỗi gọi) | < 200ms | Batched MERGE trong 1 transaction |

---

## 8. Ghi chú migration

### v1.0 → v1.1 (dự kiến)
- Thêm label `:TestClass` (multi-label `:Class:TestClass`) cho việc phát hiện test
- Edge `:CALLS_IN_TEST` để tách các lỗi gọi trong test khỏi các lỗi gọi production

### v1.x → v2.0 (Giai đoạn 2)
- Đa ngôn ngữ: thêm property `language` → `:Class {language: "java"|"kotlin"|"typescript"}`
- Lịch sử Git: thêm node `:Commit` + edge `(:File)-[:CHANGED_IN]->(:Commit)`

---

## 9. Script khởi tạo

File: `src/main/resources/db/migration/V1__init_schema.cypher`

Lưu toàn bộ các câu lệnh CREATE CONSTRAINT + CREATE INDEX ở §4. Schema được áp dụng
lúc khởi động bởi `common/config/Neo4jMigrationRunner.java` — một `ApplicationRunner`
đọc file `.cypher` từ classpath, tách theo dấu `;`, và chạy từng câu lệnh qua raw
Driver session. Tất cả câu lệnh đều dùng `IF NOT EXISTS` nên việc chạy lại là
idempotent.

---

## 10. Câu hỏi mở (để lại cho khâu hiện thực)

1. **Yêu cầu APOC plugin**: truy vấn `apoc.path.subgraphAll` cần APOC. Phải thêm `NEO4J_PLUGINS=["apoc"]` vào docker-compose.
2. **Kích thước batch**: với project 2000 file, batch MERGE bao nhiêu file/transaction để tránh tràn memory? → benchmark ở Sprint 2.
3. **Giới hạn kích thước snippet**: `Method.snippet` có giới hạn (cap) không? Method 200 dòng vẫn lưu hết? → đề xuất giới hạn 500 ký tự hoặc 20 dòng.
4. **Tần suất dọn dẹp stub**: chạy lúc nào (scheduled @Every 5min, hay sau mỗi lần DELETE file)? → đo mức tăng memory.

---

## 11. Graph Schema Roadmap (Architecture Graph → CPG-lite → CPG)

> Lộ trình tiến hoá schema. **Task hiện tại chỉ là tài liệu** — KHÔNG implement
> hàng loạt node/edge mới vào parser, KHÔNG đổi parser nếu không cần.

### 11.1 Current status (đang đúng end-to-end)

- Schema hiện tại **chính xác end-to-end cho Architecture Graph**: parser → Neo4j →
  GraphService → frontend.
- Backend **đã phát ra một số edge CPG-lite** (`TYPE_OF`, `PARAMETER_TYPE`,
  `RETURNS`, `INJECTS`, `HAS_FIELD`, …). Ví dụ trên project thật `Lab7_Java6`:
  `PARAMETER_TYPE = 66`, `RETURNS = 44`, `HAS_FIELD = 35`, `INJECTS = 7`,
  `TYPE_OF = 15`.
- `HANDLES_ROUTE` canonical là `(:Method)-[:HANDLES_ROUTE]->(:Route)` (xem §3.5).

#### Edge types thực sự được parser phát ra (emitted)

Nguồn: `EdgeData.of(...)` trong các visitor + `ParserServiceImpl` + `AnalyzeServiceImpl`:

`CONTAINS` (Project→Package, Package→File), `DEFINES`, `HAS_METHOD`, `HAS_FIELD`,
`HAS_INNER`, `EXTENDS`, `IMPLEMENTS`, `OVERRIDES`, `IMPORTS`, `TYPE_OF`, `RETURNS`,
`PARAMETER_TYPE`, `THROWS`, `CALLS`, `INSTANTIATES`, `INJECTS`, `HANDLES_ROUTE`,
`ANNOTATED_BY` (18 loại luôn bật). Khi `deep-cpg-enabled=true` bổ sung: `READS`,
`WRITES`, `CATCHES`. Phase 4 bổ sung `STEP_IN_FLOW` (luôn bật, suy diễn từ luồng
route → CALLS in-project; xem §3.7).

Node types emitted: `Project` (tầng repository), `Package`, `File`, `Class`,
`Interface`, `Enum`, `Record`, `DBModel`, `Method`, `Constructor`, `Field`,
`Annotation`, `APIEndpoint` (+ `External` stub). Khi deep CPG bật bổ sung
`LocalVariable` (local/parameter).

#### Edge types chỉ tồn tại trong contract (CHƯA emit)

`OWNS` — có trong `EdgeTypeEnum` / `GraphSchema` allow-list nhưng **không có visitor
nào phát ra**. Là chỗ dành sẵn; frontend KHÔNG được fake/hiển thị count cho nó.

#### Frontend exposure policy (Phase 1 — visible structural vs optional CPG-lite)

Frontend giữ **toàn bộ** edge backend phát ra trong store (không drop), và phân
loại HIỂN THỊ qua filter state thay vì bỏ dữ liệu:

- **Visible structural (mặc định hiện):** `CONTAINS`, `DEFINES`, `HAS_METHOD`,
  `HAS_INNER`, `EXTENDS`, `IMPLEMENTS`, `OVERRIDES`, `IMPORTS`, `CALLS`,
  `HANDLES_ROUTE`.
- **Optional CPG-lite (mặc định ẩn, mở qua "Show all"):** `HAS_FIELD`, `TYPE_OF`,
  `RETURNS`, `PARAMETER_TYPE`, `THROWS`, `INSTANTIATES`, `INJECTS`, `ANNOTATED_BY`,
  (deep CPG) `READS`, `WRITES`, `CATCHES`, và (Phase 4) `STEP_IN_FLOW`.
- **Node mặc định ẩn:** `LocalVariable` (deep CPG) — hiện qua Node Types "Show all".

Counts trong legend được tính từ graph thật (đầy đủ), nên một loại CPG-lite đang
ẩn vẫn hiện count và luôn có thể bật lên — **không loại nào có count > 0 mà không
thể reveal**. (Trước đây `sanitizeAllowedEdgeTypes` hard-drop CPG-lite ở ingestion
nên không thể bật lại — Phase 1 đã thay bằng default-hidden filter.)

- **Quan trọng:** edge type bị frontend ẩn KHÔNG có nghĩa là backend thiếu hỗ trợ.
  Đừng coi "hidden ở frontend" là "missing ở backend".

### 11.2 Phase A — Architecture Graph Java/Spring (nên làm sớm)

Củng cố và chuẩn hoá graph kiến trúc hiện có.

**Node types:** `Project`, `Package`, `File`, `External`, `Class`, `Interface`,
`Enum`, `Record`, `Annotation`, `Method`, `Constructor`, `Field`, `APIEndpoint`,
`DBModel`.

> Ghi chú label: endpoint node hiện được parser tạo với label `APIEndpoint`, còn
> schema/frontend hiển thị `Route` — cùng một loại node endpoint. Phase A nên
> **chuẩn hoá tên** (chọn một, map nhất quán) thay vì thêm node mới.

**Edge types:** `DEFINES`, `HAS_METHOD`, `HAS_FIELD`,
`HAS_ANNOTATION`/`ANNOTATED_BY` (chuẩn hoá MỘT tên canonical), `EXTENDS`,
`IMPLEMENTS`, `OVERRIDES`, `TYPE_OF`, `PARAMETER_TYPE`, `RETURNS`, `IMPORTS`,
`CALLS`, `INSTANTIATES`, `INJECTS`, `THROWS`, `HANDLES_ROUTE`.

**Phase A analysis goals:**
- Class nào thuộc file/package nào.
- Phân bố Controller / Service / Repository / Entity.
- Quan hệ handler method ↔ endpoint (tức "method nào xử lý endpoint nào"; cạnh canonical là `Method -> APIEndpoint`, xem §3.5).
- Method call graph.
- Field / parameter / return types.
- Quan hệ bean injection.
- Inheritance / interface / override.

### 11.3 Phase B — Spring architecture classification

- Thêm phân loại layer/domain: `Controller`, `Service`, `Repository`, `DTO`,
  `Config`.
- **Ưu tiên dùng property trước** (vd `:Class {springLayer: "SERVICE"}`), chỉ tạo
  node type riêng khi query/UI thực sự cần.

### 11.4 Phase C — CPG / data-flow sâu hơn (chỉ làm sau, opt-in)

**Node types:** `Variable`/`LocalVariable`, `Parameter` (nếu cần query chữ ký
method), `Exception` (nếu cần phân tích exception riêng).

**Edge types:** `READS`, `WRITES`, `CATCHES`, `STEP_IN_FLOW`.

**⚠️ Warnings (bắt buộc tuân thủ):**
- KHÔNG fake `STEP_IN_FLOW` từ `CALLS` thô — `STEP_IN_FLOW` phải đến từ một flow
  analyzer chuyên dụng.
- `READS` / `WRITES` cần phân tích biểu thức (expression-level) chính xác.
- Local variable có thể làm graph phình to → dùng deep analysis/filter dạng
  opt-in, không bật mặc định.

### 11.5 Java version target

Parser nên hướng tới hỗ trợ Java hiện đại: **Java 17**, **Java 21**, **Java 25
LTS**, và **nhận biết (awareness) Java 26** nếu thư viện parser hỗ trợ.

External facts (theo task, tính đến **2026-06-16**): JDK 26 là bản GA mới nhất và
JDK 25 là bản LTS mới nhất. Nguồn:
[Oracle Java Downloads](https://www.oracle.com/java/technologies/downloads/),
[OpenJDK JDK 26](https://openjdk.org/projects/jdk/26/).
*(Content was rephrased for compliance with licensing restrictions.)*

### 11.6 Modern Java syntax — parser goals

- `record`
- `sealed class` / `permits`
- `var`
- switch expression
- pattern matching `instanceof`
- record patterns / deconstruction (nếu parser hỗ trợ)
- text blocks
- lambda / method reference
- annotation trên type/use
- nested / inner classes
- generics phức tạp

**Spring annotations cần nhận diện:** `@RestController`, `@GetMapping`,
`@PostMapping`, `@RequestMapping`, `@Service`, `@Repository`, `@Component`,
`@Configuration`, `@Bean`, `@Entity`, `@Table`, `@Autowired`, và constructor
injection.
