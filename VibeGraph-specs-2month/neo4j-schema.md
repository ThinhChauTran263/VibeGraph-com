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

### 3.2 Kế thừa & Hiện thực

```cypher
(:Class)-[:EXTENDS]->(:Class)
(:Class)-[:IMPLEMENTS]->(:Interface)
(:Interface)-[:EXTENDS]->(:Interface)
(:Method)-[:OVERRIDES]->(:Method)        // method override (resolved tĩnh)
```

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

### 3.5 Edge đặc thù Spring

```cypher
// Tiêm phụ thuộc (dependency injection)
(:Class)-[:INJECTS {
  via: STRING,                             // "constructor" | "field" | "setter"
  fieldName: STRING                        // tên field/param nhận inject
}]->(:Class|:Interface)

// Định tuyến HTTP
(:Method)-[:HANDLES_ROUTE]->(:Route)

// Sử dụng annotation (mỗi lần annotation được dùng trên 1 element)
(:Class|:Interface|:Method|:Field)-[:ANNOTATED_BY {
  attributes: STRING                       // giá trị attribute được serialize dạng JSON
}]->(:Annotation)
```

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

> **Implementation hiện tại:** `Neo4jGraphRepository.deleteFile(projectId, filePath)` xóa mỗi node có property `filePath` tương ứng bằng `DETACH DELETE`, không đi theo `File-[:DEFINES]->typeNode` vì `File` node chưa được parser emit.

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
