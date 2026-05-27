# Module: `parser/` — Java Parser Engine

> **Vai trò:** Trái tim của VibeGraph. Đọc Java source code, dùng JavaParser + Symbol Solver để extract nodes (Class/Method/Field) và edges (CALLS/EXTENDS/IMPLEMENTS/IMPORTS). Output chuyển vào `graph/` module để lưu Neo4j.

> **Dev phụ trách:** Dev 1 (Backend Lead).

> **Sprint:** Sprint 1 (basic visitors), Sprint 2 (call graph + Spring annotations), Sprint 3 (robustness + parallel).

> **Phụ thuộc:** `common/` (FileUtils, HashUtils, exception). KHÔNG được phụ thuộc Neo4j hay Spring Web — module này phải pure Java logic, dễ unit test.

---

## Mục tiêu module

1. Parse 1 file Java → trả về `ParseResult` (nodes + edges)
2. Symbol Solver resolve method calls đúng > 90% (FR-01)
3. Detect Spring annotations: `@Controller`, `@Service`, `@Repository`, `@Autowired`, `@RequestMapping`, `@Scheduled`, `@KafkaListener`
4. Handle inner class, anonymous class, lambda, method reference
5. Parse 500 files < 30 giây (NFR-01)
6. Graceful: skip file lỗi, log warning, không crash

---

## Cấu trúc thư mục

```
parser/
├── service/              # Interface
│   └── impl/             # JavaParser implementation
├── visitor/              # JavaParser AST visitors (ClassVisitor, MethodVisitor...)
├── node/                 # Internal models (ParseResult)
└── dto/
    ├── request/
    └── response/
```

---

## Files & Specs

### `service/ParserService.java` (interface)
**Mục tiêu:** Public API của parser.

**Phải có method:**
- `ParseResult parseFile(Path filePath, ParseContext ctx)` — parse 1 file
- `List<ParseResult> parseProject(Path projectRoot)` — parse cả project (parallel)
- `void configureSymbolSolver(Path projectRoot, List<Path> jarDependencies)` — setup symbol solver

**Đạt được khi:**
- [ ] Có thể mock trong test
- [ ] AnalyzeService (graph module) gọi qua interface, không phụ thuộc impl

---

### `service/impl/ParserServiceImpl.java`
**Mục tiêu:** Orchestrator — phối hợp các visitor để parse 1 file.

**Phải làm:**
- `@Service`, inject `SymbolResolverService`, `CallGraphBuilderService`
- Inject các visitor: `ClassVisitor`, `MethodVisitor`, `FieldVisitor`, `SpringAnnotationVisitor`, `ImportVisitor`
- Method `parseFile`:
  1. `JavaParser javaParser = new JavaParser(parserConfig)` (configure với SymbolSolver)
  2. `ParseResult<CompilationUnit> result = javaParser.parse(content)`
  3. Nếu parse fail → log WARN, return empty `ParseResult` với field `errors`
  4. Visit `CompilationUnit` qua từng visitor, accumulate nodes/edges
  5. Trả về `ParseResult` chứa nodes + edges + errors
- Method `parseProject`: dùng `parallelStream` + virtual thread executor (từ `AsyncConfig`)
- Compute checksum trước khi parse (skip nếu trùng `FileNode.checksum` hiện tại)

**Đạt được khi:**
- [ ] Parse `Main.java` đơn giản → trả về 1 ClassNode + N MethodNode + edges HAS_METHOD
- [ ] Parse file có syntax error → return empty result, log warning, KHÔNG throw
- [ ] Parse 500 files (test fixture) < 30 giây
- [ ] Coverage > 70%

**Tham chiếu:** `requirements.md` FR-01, NFR-01; `task-breakdown.md` 1.1-1.8, 1.17

---

### `service/SymbolResolverService.java` (interface)
**Mục tiêu:** Wrap JavaParser Symbol Solver — resolve type và method từ AST node.

**Phải có method:**
- `Optional<String> resolveType(Type astType)` — trả về fullyQualifiedName
- `Optional<ResolvedMethod> resolveMethodCall(MethodCallExpr expr)` — trả về method ref + confidence
- `void initialize(Path projectRoot, List<Path> jarPaths)` — setup CombinedTypeSolver

**Đạt được khi:**
- [ ] Resolve `UserService.findById(...)` trả về fullName chính xác
- [ ] Generic type (`List<User>`) trả về `java.util.List` với type args

---

### `service/impl/SymbolResolverServiceImpl.java`
**Mục tiêu:** Implementation dùng `CombinedTypeSolver` của JavaParser.

**Phải làm:**
- `@Service`, lazy init (gọi `initialize` từ AnalyzeService trước khi parseProject)
- `CombinedTypeSolver` gồm:
  - `ReflectionTypeSolver` (JDK classes)
  - `JavaParserTypeSolver(srcRoot)` cho project source
  - `JarTypeSolver` cho mỗi `.jar` trong dependencies (nếu có Maven `target/dependency/`)
- Cache `Map<String, Optional<ResolvedType>>` để tránh resolve lại
- Khi resolve fail → trả `Optional.empty`, set confidence = 0.5 (architecture.md §3)
- KHÔNG throw exception khi resolve fail — đó là chuyện thường xảy ra

**Đạt được khi:**
- [ ] Resolve > 90% method calls trong test fixture (FR-01)
- [ ] Cache hit > 50% sau parse 100 files
- [ ] Memory không leak khi parse project lớn

**Tham chiếu:** `requirements.md` FR-01 (>90% accuracy), `architecture.md` §3 (confidence property)

---

### `service/CallGraphBuilderService.java` (interface)
**Mục tiêu:** Build CALLS edges từ MethodCallExpr nodes.

**Phải có method:**
- `List<Edge> buildCallEdges(MethodDeclaration callerMethod, ResolvedMethodContext ctx)`

---

### `service/impl/CallGraphBuilderServiceImpl.java`
**Mục tiêu:** Tìm tất cả `MethodCallExpr` trong body method, resolve target, tạo CALLS edge.

**Phải làm:**
- `@Service`, inject `SymbolResolverService`
- Walk AST của method body bằng `methodDecl.findAll(MethodCallExpr.class)`
- Với mỗi call expr:
  1. `resolveMethodCall(expr)` → optional ResolvedMethod
  2. Nếu resolve OK → tạo Edge `{type=CALLS, source=callerFullName, target=resolvedFullName, lineNumber, confidence=1.0}`
  3. Nếu fail → tạo Edge với `confidence=0.5`, `target=expr.getNameAsString()` (best effort)
- Handle `ObjectCreationExpr` (constructor calls): tạo CALLS edge tới constructor
- Handle `MethodReferenceExpr` (`User::getName`): tạo CALLS edge

**Đạt được khi:**
- [ ] Sample fixture: `Controller.create() calls service.save()` → CALLS edge xuất hiện
- [ ] Lambda body: `users.stream().map(u -> service.process(u))` → CALLS edge tới `service.process`
- [ ] Constructor call: `new UserDto(user)` → CALLS edge tới `UserDto.<init>`
- [ ] Static method: `Math.max(a, b)` → CALLS edge tới `java.lang.Math.max`

**Tham chiếu:** `task-breakdown.md` 1.9, 1.10, 1.15

---

### `visitor/ClassVisitor.java`
**Mục tiêu:** Extract Class, Interface, Enum, Record nodes + EXTENDS/IMPLEMENTS edges.

**Phải làm:**
- Extends `VoidVisitorAdapter<ParseContext>` của JavaParser
- Override `visit(ClassOrInterfaceDeclaration n, ParseContext ctx)`:
  - Tạo `ClassNode` hoặc `InterfaceNode` (theo `n.isInterface()`)
  - Set: name, fullName (qualifiedName), filePath, lineNumber, visibility, isAbstract, isFinal, isStatic
  - Với class: extract `extendedTypes` → EXTENDS edge; `implementedTypes` → IMPLEMENTS edge
  - Với interface: `extendedTypes` → EXTENDS edge (interface extends interface)
  - Inner class: visit recursive, set `enclosingClass` field
  - Anonymous class: tạo class node với name `EnclosingClass$N`
- Override `visit(EnumDeclaration n, ParseContext ctx)`: tạo EnumNode
- Override `visit(RecordDeclaration n, ParseContext ctx)`: tạo ClassNode với flag `isRecord=true`

**Đạt được khi:**
- [ ] Sample: `class Foo extends Bar implements Baz` → ClassNode + EXTENDS(Foo, Bar) + IMPLEMENTS(Foo, Baz)
- [ ] Inner class: `class Outer { class Inner {} }` → 2 ClassNode, fullName = `com.x.Outer.Inner`
- [ ] Generic class: `class List<T>` → giữ generic params trong property
- [ ] Coverage > 80% cho visitor logic

**Tham chiếu:** `requirements.md` FR-01, `task-breakdown.md` 1.2, 1.6

---

### `visitor/MethodVisitor.java`
**Mục tiêu:** Extract Method nodes + parameters/return types.

**Phải làm:**
- Override `visit(MethodDeclaration n, ParseContext ctx)`:
  - Tạo `MethodNode` với: name, fullName (className.methodName(params)), filePath, lineNumber
  - Visibility (public/private/protected/package)
  - Modifiers: isAbstract, isStatic, isFinal, isSynchronized
  - returnType: resolve qua SymbolResolverService → fullName
  - parameters: List `{name, type}` cho mỗi `Parameter`
  - throwsTypes: list từ `n.getThrownExceptions()`
  - Edge HAS_METHOD từ enclosing class
- Override `visit(ConstructorDeclaration n, ...)`: tạo MethodNode với name=`<init>`
- Lambda: KHÔNG tạo MethodNode riêng (đã handle trong CallGraphBuilder)

**Đạt được khi:**
- [ ] Method overload: `add(int)` và `add(String)` tạo 2 nodes khác fullName
- [ ] Constructor extracted với name `<init>`
- [ ] Throws clause được lưu

**Tham chiếu:** `requirements.md` FR-01, `task-breakdown.md` 1.3

---

### `visitor/FieldVisitor.java`
**Mục tiêu:** Extract Field nodes + TYPE_OF edges.

**Phải làm:**
- Override `visit(FieldDeclaration n, ParseContext ctx)`:
  - Mỗi `VariableDeclarator` trong field → 1 FieldNode
  - Set: name, fullName, declaredType (resolved fullName), visibility, isStatic, isFinal
  - Edge HAS_FIELD từ enclosing class
  - Edge TYPE_OF từ field tới class/interface của declaredType
  - Detect injection: nếu có annotation `@Autowired`, `@Inject`, `@Resource` → set `isInjected=true`

**Đạt được khi:**
- [ ] `private final UserService userService` → FieldNode + TYPE_OF tới UserService
- [ ] `@Autowired UserRepo repo` → FieldNode với `isInjected=true`
- [ ] Multi-decl: `int a, b, c` → 3 FieldNode

**Tham chiếu:** `task-breakdown.md` 1.4

---

### `visitor/SpringAnnotationVisitor.java`
**Mục tiêu:** Detect Spring annotations, enrich ClassNode/MethodNode + tạo Route node.

**Phải làm:**
- Override `visit(ClassOrInterfaceDeclaration n, ctx)`:
  - Check annotation: `@Controller`, `@RestController`, `@Service`, `@Repository`, `@Component`, `@Configuration`
  - Set `springLayer` của ClassNode = `CONTROLLER` | `SERVICE` | `REPOSITORY` | `COMPONENT` | `CONFIG`
  - Lưu list `springAnnotations[]` (raw annotation names)
  - Class-level `@RequestMapping("/api/users")` → lưu `basePath` để dùng cho method
- Override `visit(MethodDeclaration n, ctx)`:
  - Detect `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@RequestMapping`
  - Tạo `RouteNode` `{httpMethod, routePath: basePath + methodPath, handlerMethod: methodFullName}`
  - Edge HANDLES_ROUTE từ method tới route
  - Detect `@Scheduled` → set flag `isScheduled=true`
  - Detect `@KafkaListener`, `@RabbitListener`, `@EventListener` → set `eventSource`
- Override `visit(FieldDeclaration n, ctx)`:
  - `@Autowired` field → INJECTS edge từ enclosing class tới field type (architecture.md §3)
- Constructor injection: visit constructor params (final fields được set trong constructor) → INJECTS edge

**Đạt được khi:**
- [ ] `@RestController` class có `springLayer=CONTROLLER`
- [ ] `@GetMapping("/users")` → RouteNode `{GET, /api/users}` (basePath + path)
- [ ] `@Scheduled(cron=...)` method có `isScheduled=true`
- [ ] Constructor injection được detect (modern Spring style không dùng `@Autowired`)

**Tham chiếu:** `requirements.md` FR-01, FR-04 (actor detection), `architecture.md` §3, `task-breakdown.md` 1.11-1.14

---

### `visitor/ImportVisitor.java`
**Mục tiêu:** Extract IMPORTS edges từ `import` statements.

**Phải làm:**
- Override `visit(ImportDeclaration n, ctx)`:
  - Skip `java.lang.*` (auto-imported)
  - Tạo IMPORTS edge từ current class tới imported class
  - Wildcard import (`import com.foo.*`): tạo edge tới package (DEPENDS_ON Package)
  - Static import: tạo edge tới class chứa member

**Đạt được khi:**
- [ ] `import com.example.UserService` → IMPORTS edge
- [ ] `import java.util.*` → DEPENDS_ON edge tới `java.util` package
- [ ] `import static org.junit.Assert.*` → IMPORTS edge tới `org.junit.Assert`

**Tham chiếu:** `task-breakdown.md` 1.8

---

### `node/ParseResult.java`
**Mục tiêu:** Internal model — kết quả parse 1 file.

**Phải làm:**
- Record `ParseResult(Path filePath, String checksum, List<NodeData> nodes, List<EdgeData> edges, List<ParseError> errors)`
- `NodeData` record: `{String type, String fullName, Map<String, Object> properties}`
- `EdgeData` record: `{String type, String sourceFullName, String targetFullName, Map<String, Object> properties}`
- `ParseError` record: `{String filePath, Integer lineNumber, String message}`

**Đạt được khi:**
- [ ] Là Java record (immutable)
- [ ] Serializable to JSON cho debug

**Tham chiếu:** `architecture.md` §3 (node/edge schema)

---

### `dto/request/ParseFileRequest.java`
**Mục tiêu:** Request DTO nếu expose parser qua REST (debug endpoint).

**Phải làm:**
- Record `ParseFileRequest(String filePath, String projectId)`
- `@NotBlank` validation

**Đạt được khi:**
- [ ] (Optional) Endpoint `POST /api/debug/parse` dùng được cho dev

---

### `dto/response/ParseResultResponse.java`
**Mục tiêu:** Public-facing version của ParseResult.

**Phải làm:**
- Record với cùng field như `ParseResult` nhưng bỏ checksum, làm thân thiện cho UI/debug
- Convert từ `ParseResult.toResponse()`

---

## Definition of Done cho module parser/

- [ ] Unit tests parse fixture project: 5 files, 10 classes, 30 methods → đúng số node/edge expected
- [ ] Symbol Solver resolve > 90% method calls trên fixture (đo bằng `confidence=1.0` ratio)
- [ ] Parse 500 files < 30 giây trên máy dev (NFR-01)
- [ ] Skip file syntax-error mà không crash app
- [ ] Coverage > 70% (testing.md)
- [ ] Tích hợp được với `AnalyzeService` (graph module): graph module gọi `parser.parseProject(...)` → nhận `List<ParseResult>` → save Neo4j

---

## Lưu ý cross-module

- Output `ParseResult` của module này là **input của graph module**. Tuyệt đối không reference Neo4j entity trong parser/ — giữ thuần Java.
- Field `confidence` trong CALLS edge (1.0 / 0.5) phải được map sang property của Neo4j edge ở graph module.
- Khi parser không resolve được type → vẫn lưu edge với `confidence=0.5` thay vì bỏ qua, để frontend có thể hiển thị "approximate" call.
- Performance critical: dùng `parallelStream` + virtual thread, KHÔNG dùng synchronized block trong visitor (mỗi file 1 visitor instance riêng).
