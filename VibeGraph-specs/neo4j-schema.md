# VibeGraph — Neo4j Schema v1.0

**Version:** 1.0.0
**Date:** 2026-05-20
**Status:** Approved (6 design decisions confirmed)

---

## 1. Design Decisions (Locked)

| # | Decision | Choice |
|---|----------|--------|
| 1 | Multi-tenancy | `projectId` property trên mọi node |
| 2 | Node identity | Composite key `(projectId, fullName, paramTypes)` |
| 3 | Polymorphism | 1 edge tới target tĩnh + `targetType` property |
| 4 | Constructor | `:Method` label + `kind="constructor"` property |
| 5 | Source code | Snippet trên node + filesystem cho full file |
| 6 | Rename | Xóa và tạo mới (không track rename) |
| 7 | CALLS integrity | `MERGE` theo composite key + stub fallback |

---

## 2. Node Labels

### 2.1 `:Project`
Project gốc, là entry point cho mọi query.

```cypher
(:Project {
  id: STRING,                  // UUID, primary key
  name: STRING,                // tên hiển thị
  rootPath: STRING,            // absolute path tới project folder
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
  filePath: STRING,            // relative path từ rootPath, ví dụ "src/main/java/User.java"
  name: STRING,                // "User.java"
  lineCount: INTEGER,
  checksum: STRING,            // SHA-256, dùng cho incremental skip
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
  springLayer: STRING,         // "controller" | "service" | "repository" | "component" | "config" | null
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
  kind: STRING,                // "method" | "constructor" | "static_init"
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
  snippet: STRING              // 5-20 lines source code
})
```

### 2.8 `:Field`

```cypher
(:Field {
  projectId: STRING,
  fullName: STRING,            // "com.example.UserService.userRepository"
  name: STRING,
  paramTypes: LIST<STRING>,    // [] (cho composite key consistency với Method)
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
Định nghĩa custom annotation (không phải usage).

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
HTTP endpoint detected từ Spring annotations.

```cypher
(:Route {
  projectId: STRING,
  fullName: STRING,            // "GET /api/users/{id}"
  paramTypes: LIST<STRING>,    // [] (consistency)
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

## 3. Edge Types

### 3.1 Structural Edges

```cypher
// Project ownership (root traversal)
(:Project)-[:OWNS]->(:Package|:File)

// Package containment
(:Package)-[:CONTAINS]->(:Class|:Interface|:Enum|:Annotation)

// File definition
(:File)-[:DEFINES]->(:Class|:Interface|:Enum|:Annotation)

// Class members
(:Class|:Interface|:Enum)-[:HAS_METHOD]->(:Method)
(:Class|:Enum)-[:HAS_FIELD]->(:Field)
(:Class|:Interface)-[:HAS_INNER]->(:Class|:Interface|:Enum)
```

### 3.2 Inheritance & Implementation

```cypher
(:Class)-[:EXTENDS]->(:Class)
(:Class)-[:IMPLEMENTS]->(:Interface)
(:Interface)-[:EXTENDS]->(:Interface)
(:Method)-[:OVERRIDES]->(:Method)        // method override (resolved tĩnh)
```

### 3.3 Type & Import Dependencies

```cypher
(:File)-[:IMPORTS]->(:Class|:Interface|:Enum|:Annotation)
(:Field)-[:TYPE_OF]->(:Class|:Interface|:Enum)
(:Method)-[:RETURNS]->(:Class|:Interface|:Enum)
(:Method)-[:PARAMETER_TYPE {position: INTEGER}]->(:Class|:Interface|:Enum)
(:Method)-[:THROWS]->(:Class)             // exception types
```

### 3.4 Call Graph (Decision #3 — Polymorphism)

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
- `"unresolved"` (confidence=0.3): Symbol Solver fail, best-guess match theo name

### 3.5 Spring-Specific Edges

```cypher
// Dependency injection
(:Class)-[:INJECTS {
  via: STRING,                             // "constructor" | "field" | "setter"
  fieldName: STRING                        // tên field/param nhận inject
}]->(:Class|:Interface)

// HTTP routing
(:Method)-[:HANDLES_ROUTE]->(:Route)

// Annotation usage (mỗi lần annotation được dùng trên 1 element)
(:Class|:Interface|:Method|:Field)-[:ANNOTATED_BY {
  attributes: STRING                       // JSON-serialized attribute values
}]->(:Annotation)
```

---

## 4. Constraints & Indexes

### 4.1 Uniqueness Constraints

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

### 4.2 Lookup Indexes

```cypher
// Filter theo project + tên (search bar)
CREATE INDEX class_proj_name IF NOT EXISTS
FOR (c:Class) ON (c.projectId, c.name);

CREATE INDEX interface_proj_name IF NOT EXISTS
FOR (i:Interface) ON (i.projectId, i.name);

CREATE INDEX method_proj_name IF NOT EXISTS
FOR (m:Method) ON (m.projectId, m.name);

CREATE INDEX field_proj_name IF NOT EXISTS
FOR (f:Field) ON (f.projectId, f.name);

// Filter theo Spring layer
CREATE INDEX class_spring_layer IF NOT EXISTS
FOR (c:Class) ON (c.projectId, c.springLayer);

// Tìm route theo path
CREATE INDEX route_path IF NOT EXISTS
FOR (r:Route) ON (r.projectId, r.routePath);

// File lookup khi watcher trigger
CREATE INDEX file_path IF NOT EXISTS
FOR (f:File) ON (f.projectId, f.filePath);

// Stub method cleanup
CREATE INDEX method_stub IF NOT EXISTS
FOR (m:Method) ON (m.projectId, m.isStub);
```

### 4.3 Fulltext Search

```cypher
// Search bar: tìm node theo tên (case-insensitive, fuzzy)
CREATE FULLTEXT INDEX node_search IF NOT EXISTS
FOR (n:Class|Interface|Enum|Method|Field|Annotation)
ON EACH [n.name, n.fullName];
```

---

## 5. Sample Cypher — CRUD Patterns

### 5.1 Insert Class với MERGE pattern

```cypher
// Upsert Class node + edges
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

### 5.2 Insert Method (handle stub case)

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

### 5.3 Insert CALLS edge (Decision #7 — stub fallback)

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

### 5.4 Delete file (Decision #6 — rename = delete + create)

```cypher
// Khi watcher báo file deleted hoặc renamed
MATCH (f:File {projectId: $projectId, filePath: $filePath})
OPTIONAL MATCH (f)-[:DEFINES]->(typeNode)
OPTIONAL MATCH (typeNode)-[:HAS_METHOD]->(m:Method)
OPTIONAL MATCH (typeNode)-[:HAS_FIELD]->(fld:Field)
DETACH DELETE f, typeNode, m, fld
```

### 5.5 Cleanup orphan stub methods

```cypher
// Chạy định kỳ hoặc sau khi delete file
MATCH (m:Method {projectId: $projectId, isStub: true})
WHERE NOT (m)<-[:CALLS]-()
DETACH DELETE m
```

### 5.6 Get full graph (cho frontend)

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

### 5.7 N-hop neighborhood

```cypher
// Neighbors trong N hops của 1 node (cho Focus Mode)
MATCH (center:Method {projectId: $projectId, fullName: $fullName})
CALL apoc.path.subgraphAll(center, {
  maxLevel: $hops,
  relationshipFilter: "CALLS|HAS_METHOD|EXTENDS|IMPLEMENTS|INJECTS"
}) YIELD nodes, relationships
RETURN nodes, relationships
```

### 5.8 Impact analysis (blast radius)

```cypher
// Tìm tất cả method bị ảnh hưởng nếu sửa target
MATCH (target:Method {projectId: $projectId, fullName: $fullName})
MATCH path = (caller:Method)-[:CALLS*1..5]->(target)
RETURN DISTINCT caller, length(path) AS distance
ORDER BY distance ASC
```

### 5.9 Spring layer detection

```cypher
// Đếm nodes mỗi layer
MATCH (c:Class {projectId: $projectId})
WHERE c.springLayer IS NOT NULL
RETURN c.springLayer AS layer, count(c) AS count
ORDER BY count DESC
```

### 5.10 Search nodes (fulltext)

```cypher
// Search bar: tìm theo tên
CALL db.index.fulltext.queryNodes("node_search", $query)
YIELD node, score
WHERE node.projectId = $projectId
RETURN node, labels(node) AS labels, score
ORDER BY score DESC
LIMIT 20
```

---

## 6. Visibility Rule (Tenant Safety)

**Mọi Cypher query MUST filter `projectId`** để tránh leak data giữa projects.

Để giảm risk, Spring Data Neo4j repository methods được generate kèm `projectId` parameter:

```java
@Query("""
    MATCH (c:Class {projectId: $projectId, fullName: $fullName})
    RETURN c
    """)
Optional<ClassNode> findClassByFullName(String projectId, String fullName);
```

**Forbidden patterns** (sẽ bị reject ở code review):
```cypher
MATCH (c:Class) RETURN c                  // ❌ thiếu projectId filter
MATCH (c:Class {fullName: $fn}) RETURN c  // ❌ trùng fullName cross-project
```

---

## 7. Performance Targets

| Query | Target | Index hỗ trợ |
|-------|--------|-------------|
| Get full graph (1 project, 5000 nodes) | < 800ms | `OWNS` traversal + composite keys |
| N-hop neighborhood (3 hops) | < 500ms | APOC subgraphAll + relationship filter |
| Impact analysis (5 hops) | < 1s | `CALLS` relationship index |
| Search node by name | < 100ms | Fulltext `node_search` |
| Insert 1 file (10 classes, 50 methods, 100 calls) | < 200ms | Batched MERGE trong 1 transaction |

---

## 8. Migration Notes

### v1.0 → v1.1 (planned)
- Thêm label `:TestClass` (multi-label `:Class:TestClass`) cho test detection
- Edge `:CALLS_IN_TEST` để separate test calls khỏi production calls

### v1.x → v2.0 (Phase 2)
- Multi-language: thêm `language` property → `:Class {language: "java"|"kotlin"|"typescript"}`
- Git history: thêm `:Commit` node + `(:File)-[:CHANGED_IN]->(:Commit)` edge

---

## 9. Initialization Script

File: `vibegraph-server/src/main/resources/db/migration/V1__init_schema.cypher`

Lưu toàn bộ CREATE CONSTRAINT + CREATE INDEX statements ở §4. Spring Boot chạy script này khi startup qua `Neo4jMigrationsAutoConfiguration` (hoặc custom `@PostConstruct` runner).

---

## 10. Open Questions (Defer to implementation)

1. **APOC plugin requirement**: query `apoc.path.subgraphAll` cần APOC. Phải thêm `NEO4J_PLUGINS=["apoc"]` vào docker-compose.
2. **Batch size**: với project 2000 files, batch MERGE bao nhiêu file/transaction để tránh memory? → benchmark Sprint 2.
3. **Snippet size limit**: `Method.snippet` có cap không? Method 200 dòng vẫn lưu hết? → propose cap 500 chars hoặc 20 lines.
4. **Stub cleanup frequency**: chạy lúc nào (scheduled @Every 5min, hay sau mỗi DELETE file)? → đo memory growth.
