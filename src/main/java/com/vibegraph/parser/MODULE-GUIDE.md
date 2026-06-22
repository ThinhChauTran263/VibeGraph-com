# Module: parser

## Mục đích
Parser engine sử dụng JavaParser để đọc Java source code, extract AST nodes và relationships, tạo dữ liệu cho knowledge graph.

## Cấu trúc

```
parser/
├── service/
│   ├── ParserService.java              — Interface: orchestrate parsing pipeline (+ ParseProgressListener overload)
│   ├── ParseProgressListener.java      — Per-file progress callback (filesParsed/total)
│   ├── CacheService.java               — Parse-result cache (checksum-keyed)
│   └── impl/
│       └── ParserServiceImpl.java      — Main parser orchestrator (resolve + call graph inline)
├── visitor/
│   ├── ClassVisitor.java               — Extract Class/Interface/Enum nodes
│   ├── MethodVisitor.java              — Extract Method nodes (params, return type)
│   ├── FieldVisitor.java               — Extract Field nodes (type, visibility)
│   ├── AnnotationVisitor.java          — Extract annotation metadata
│   ├── SpringAnnotationVisitor.java    — Detect @Controller, @Service, @Repository, @RequestMapping
│   └── ImportVisitor.java              — Extract IMPORTS edges
├── flow/
│   └── FlowAnalyzer.java               — Infer STEP_IN_FLOW edges from route handlers + CALLS graph
├── node/
│   ├── NodeData.java                   — Extracted node model
│   ├── EdgeData.java                   — Extracted edge model
│   └── ParseResult.java               — Internal result model (nodes + edges + warnings)
├── util/
│   └── TypeNames.java                  — Type-name normalization helpers
├── Signatures.java                     — Method/field signature formatting
└── dto/
    ├── request/
    │   └── ParseFileRequest.java       — {filePath, projectId}
    └── response/
        └── ParseResultResponse.java    — {nodesCount, edgesCount, warnings[]}
```

## Yêu cầu chức năng

### ParserService
- [ ] `parseProject(Path projectDir)`: Scan tất cả .java files, parse từng file, trả về aggregated ParseResult
- [ ] `parseFile(Path file)`: Parse single file → ParseResult (nodes + edges)
- [ ] `parseIncremental(Path file, String previousChecksum)`: Chỉ parse nếu checksum thay đổi
- [ ] Parallel parsing sử dụng virtual threads (Java 21)
- [ ] Parse time < 30 seconds cho 500 files
- [ ] Graceful error handling: skip unparseable files, log warnings, continue

### Visitors
- [ ] `ClassVisitor`: Extract Class, Interface, Enum nodes với properties:
  - name, fullName (package.ClassName), filePath, lineNumber
  - visibility (public/private/protected/package-private)
  - isAbstract, isFinal, isStatic
  - springLayer (CONTROLLER/SERVICE/REPOSITORY/COMPONENT/NONE)
  - springAnnotations[] (list of annotation names)
- [ ] `MethodVisitor`: Extract Method nodes với:
  - name, fullName, filePath, lineNumber
  - visibility, isAbstract, isStatic, isFinal
  - returnType, parameters[] (name:type pairs), throwsTypes[]
  - httpMethod (GET/POST/PUT/DELETE nếu có @RequestMapping)
  - routePath (URL path nếu có mapping annotation)
- [ ] `FieldVisitor`: Extract Field nodes với:
  - name, fullName, filePath, lineNumber
  - visibility, isStatic, isFinal
  - declaredType, isInjected (true nếu có @Autowired/@Inject)
- [ ] `SpringAnnotationVisitor`: Detect và enrich nodes:
  - @RestController, @Controller → springLayer = CONTROLLER
  - @Service → springLayer = SERVICE
  - @Repository → springLayer = REPOSITORY
  - @Component → springLayer = COMPONENT
  - @RequestMapping, @GetMapping, @PostMapping → tạo Route node
  - @Autowired → mark field isInjected = true
  - @Scheduled, @KafkaListener → enrich method metadata
- [ ] `ImportVisitor`: Extract IMPORTS edges (Class A imports Class B)

### Symbol resolution & call graph (trong ParserServiceImpl)
Việc resolve type/method-call và dựng CALLS edges được làm **trực tiếp trong** `ParserServiceImpl`
(qua JavaParser Symbol Solver) thay vì tách thành service riêng. Phạm vi:
- [x] Resolve method call targets + type references (field/return/param types) trong khả năng Symbol Solver
- [x] CALLS edges từ method invocation; symbol chưa resolve → gắn confidence thấp / External stub
- [x] STEP_IN_FLOW (call chain từ route handler) do `flow/FlowAnalyzer` suy luận

### Relationship Extraction
- [ ] EXTENDS: Class A extends Class B
- [ ] IMPLEMENTS: Class A implements Interface B
- [ ] HAS_METHOD: Class → Method
- [ ] HAS_FIELD: Class → Field
- [ ] CALLS: Method A calls Method B (with lineNumber, confidence)
- [ ] IMPORTS: Class A imports Class B
- [ ] INJECTS: Class A injects Class B (via @Autowired)
- [ ] HANDLES_ROUTE: Method → Route (HTTP endpoint)
- [ ] CONTAINS: Package → Class/Interface/Enum
- [ ] DEFINES: File → Class/Interface/Enum

## Quy tắc code

1. **Visitor Pattern**: Mỗi visitor chỉ extract 1 loại thông tin, không mix concerns
2. **Immutable results**: ParseResult là immutable, tạo mới mỗi lần parse
3. **No Neo4j dependency**: Module này KHÔNG phụ thuộc Neo4j, chỉ trả về plain objects
4. **Error isolation**: Parse error ở 1 file không ảnh hưởng files khác
5. **Logging**: Log mỗi file đang parse (DEBUG level), log warnings cho unresolved symbols

## Dependencies

- JavaParser Core 3.28+
- JavaParser Symbol Solver 3.28+
- Spring Context (for @Service)
- Lombok

## Performance Targets

| Metric | Target |
|--------|--------|
| Parse 500 files | < 30 seconds |
| Parse 1 file (incremental) | < 500ms |
| Symbol resolution accuracy | > 90% |
| Memory usage (500 files) | < 512MB |

## Acceptance Criteria

- [ ] Parse sample Spring Boot project → extract all node types correctly
- [ ] CALLS edges resolve > 90% method invocations
- [ ] Spring annotations detected correctly (layer assignment)
- [ ] Route nodes created for all @RequestMapping methods
- [ ] Incremental parse skips unchanged files (checksum match)
- [ ] No crash on invalid/incomplete Java files
- [ ] Unit tests cover all visitors with sample Java code
- [ ] Integration test: parse real Spring Boot project end-to-end
