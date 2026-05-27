# VibeGraph — Test Guide

## Tổng quan

Project sử dụng **JUnit 5** + **AssertJ** + **Mockito**. Tất cả test được tổ chức theo module và đánh dấu `@Disabled` cho đến khi source code được implement.

**Trạng thái hiện tại:** 103 tests (3 pass, 100 skipped via `@Disabled`)

---

## Cách chạy tests

### Chạy tất cả tests
```bash
./mvnw test
```

### Chạy test cho 1 module cụ thể
```bash
# Common module
./mvnw test -Dtest="com.vibegraph.common.**"

# Parser module
./mvnw test -Dtest="com.vibegraph.parser.**"

# Graph module
./mvnw test -Dtest="com.vibegraph.graph.**"

# Diagram module
./mvnw test -Dtest="com.vibegraph.diagram.**"

# MCP module
./mvnw test -Dtest="com.vibegraph.mcp.**"

# Steering module
./mvnw test -Dtest="com.vibegraph.steering.**"

# Watcher module
./mvnw test -Dtest="com.vibegraph.watcher.**"
```

### Chạy 1 test class cụ thể
```bash
./mvnw test -Dtest=ClassVisitorTest
./mvnw test -Dtest=FileUtilsTest
./mvnw test -Dtest=HashUtilsTest
```

### Chạy với coverage report
```bash
./mvnw verify
# Report tại: target/site/jacoco/index.html
# Build FAIL nếu coverage < 70%
```

---

## Quy trình cho Developer

### Khi implement xong 1 feature:

1. Mở file test tương ứng
2. Bỏ `@Disabled` annotation ở method/class đã implement
3. Chạy test:
   ```bash
   ./mvnw test -Dtest="TênTestClass"
   ```
4. **PASS** → feature hoàn thành
5. **FAIL** → fix code cho đến khi pass

### Ví dụ: Implement FileUtils

```java
// Trước: Test bị disabled
@Disabled("Chờ FileUtils implement scanJavaFiles() và isJavaFile()")
class FileUtilsTest { ... }

// Sau khi implement xong FileUtils.java → bỏ @Disabled:
class FileUtilsTest { ... }

// Chạy verify
./mvnw test -Dtest="FileUtilsTest"
```

---

## Test status per module

| Module | Test File | Status | Điều kiện bỏ @Disabled |
|--------|-----------|--------|------------------------|
| common | ExceptionsTest | ✅ PASS | Đã implement |
| common | FileUtilsTest | @Disabled | Implement `scanJavaFiles()`, `isJavaFile()` |
| common | HashUtilsTest | @Disabled | Implement `sha256(String)`, `sha256(Path)` |
| common | JsonUtilsTest | @Disabled | Implement `toJson()`, `fromJson()` |
| parser | ClassVisitorTest | @Disabled | ClassVisitor trả về `ExtractedClassNode` |
| parser | MethodVisitorTest | @Disabled | MethodVisitor trả về `ExtractedMethodNode` |
| parser | FieldVisitorTest | @Disabled | FieldVisitor trả về `ExtractedFieldNode` |
| parser | ParserServiceTest | @Disabled | ParserServiceImpl ready |
| graph | ProjectControllerTest | @Disabled | REST controller + MockMvc |
| graph | GraphServiceTest | @Disabled | Neo4j queries |
| graph | ImpactServiceTest | @Disabled | Blast radius logic |
| diagram | DiagramServiceTest | @Disabled | Mermaid generation |
| mcp | McpToolsTest | @Disabled | MCP tools wired up |
| steering | SteeringWriterTest | @Disabled | Writer implementations |
| watcher | FileWatcherServiceTest | @Disabled | WatchService impl |
| root | VibeGraphApplicationTests | @Disabled | Neo4j + MCP Server running |

---

## Cấu trúc test files

```
src/test/java/com/vibegraph/
├── VibeGraphApplicationTests.java        — Spring Boot context test
├── common/
│   ├── exception/
│   │   └── ExceptionsTest.java           — ✅ Custom exception classes
│   └── util/
│       ├── FileUtilsTest.java            — File scanning, filtering
│       ├── HashUtilsTest.java            — SHA-256 checksum
│       └── JsonUtilsTest.java            — JSON serialization
├── parser/
│   ├── visitor/
│   │   ├── ClassVisitorTest.java         — Class/Interface/Enum extraction
│   │   ├── MethodVisitorTest.java        — Method extraction + Spring endpoints
│   │   └── FieldVisitorTest.java         — Field extraction + @Autowired
│   └── service/
│       └── ParserServiceTest.java        — Parsing orchestrator
├── graph/
│   ├── controller/
│   │   └── ProjectControllerTest.java    — REST API endpoints
│   └── service/
│       ├── GraphServiceTest.java         — Graph queries
│       └── ImpactServiceTest.java        — Blast radius analysis
├── diagram/
│   └── service/
│       └── DiagramServiceTest.java       — UML diagram generation
├── mcp/
│   └── tool/
│       └── McpToolsTest.java             — MCP tool responses
├── steering/
│   └── writer/
│       └── SteeringWriterTest.java       — Steering file generation
└── watcher/
    └── service/
        └── FileWatcherServiceTest.java   — File change detection
```

---

## Quy tắc viết test

### 1. AAA Pattern
```java
@Test
void parseFile_validJavaFile_returnsClassNode() {
    // Arrange
    Path file = tempDir.resolve("Test.java");
    Files.writeString(file, "public class Test {}");

    // Act
    var result = parserService.parseFile(file);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("Test");
}
```

### 2. Naming convention
- Method: `methodName_scenario_expectedBehavior()`
- `@DisplayName`: Mô tả behavior bằng tiếng Anh
- `@Nested`: Group tests theo feature/scenario

### 3. Assertions (AssertJ)
```java
// Dùng AssertJ thay vì JUnit assertions
assertThat(actual).isEqualTo(expected);
assertThat(list).hasSize(3).contains("a", "b");
assertThat(path).exists().isRegularFile();
assertThatThrownBy(() -> method())
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("invalid");
```

### 4. Annotations
- `@TempDir` — cho file I/O tests (auto cleanup)
- `@Nested` — group tests theo scenario
- `@DisplayName` — mô tả readable
- `@Disabled("Chờ XxxImpl implement")` — skip test chưa có source

### 5. Sample data
Dùng files trong `src/test/resources/sample-project/` cho parser tests.

---

## Khi nào module DONE

Mỗi module được coi là hoàn thành khi:

- [ ] Tất cả `@Disabled` đã được bỏ
- [ ] Tất cả tests PASS (green)
- [ ] `./mvnw verify` pass → Coverage >= 70%
- [ ] Không còn `UnsupportedOperationException` trong source

---

## Thứ tự implement khuyến nghị

```
1. common/util     → FileUtils, HashUtils, JsonUtils
2. parser/visitor  → ClassVisitor, MethodVisitor, FieldVisitor
3. parser/service  → ParserServiceImpl
4. graph/service   → GraphServiceImpl, ImpactServiceImpl
5. graph/controller → ProjectController
6. diagram         → DiagramServiceImpl
7. mcp             → MCP Tools
8. steering        → SteeringWriters
9. watcher         → FileWatcherServiceImpl
```

Lý do: mỗi tầng phụ thuộc tầng trước. Parser cần utils, graph cần parser, diagram cần graph.

---

## Troubleshooting

### Test không compile
Kiểm tra source code đã có method/class mà test gọi chưa. Stub methods (throw `UnsupportedOperationException`) đã có sẵn để đảm bảo compile.

### Spring context test fail
`VibeGraphApplicationTests` cần Neo4j running:
```bash
docker run -d -p 7687:7687 -e NEO4J_AUTH=neo4j/vibegraph neo4j:5
```

### Coverage thấp
- Test cả happy path và error cases
- Đừng chỉ test getter/setter
- Focus vào business logic

### Test bị skip nhưng không muốn
Tìm và bỏ `@Disabled` annotation trong file test tương ứng.
