# 🔍 BÁO CÁO AUDIT TOÀN DIỆN — VibeGraph

> **Ngày:** 2026-08-12
> **Người thực hiện:** Nhóm Senior Software Architects (Frontend Vue.js · Backend Java/Spring Boot · Database · Security · DevOps)
> **Loại:** Phân tích tĩnh (static code review) + kiểm chứng độc lập trên source thật

---

## Phạm vi & phương pháp (minh bạch)

- Codebase rất lớn: **703 file mã nguồn** (`src/main` + `vibegraph-web/src`) + cấu hình. Không đọc tuyến tính từng dòng của 703 file — thay vào đó dùng **phân tích tĩnh có mục tiêu** (grep pattern nguy hiểm, secrets, exception, injection) kết hợp **đọc sâu & kiểm chứng** các file trọng yếu nhất theo rủi ro/tác động.
- Các file đã đọc/kiểm chứng trực tiếp: `SecurityConfig`, `Neo4jGraphRepository`, `ApiKeyAuthFilter`, `JwtAuthFilter`, `SourceFileServiceImpl`, `ClientAddressResolver`, `stores/auth.ts`, `application.yaml`/`application-prod.yaml`, `Dockerfile` (BE), `docker-compose.yml`, `V1__init_auth_schema.sql`, các migration `db/migration/*`.
- Repo **đã có sẵn** báo cáo `update/security-perf-audit.md` (F1–F11). Các finding đó đã được **verify lại trên code thật** (không copy) và bổ sung các trục còn thiếu: **DevOps/Docker, DB indexing/concurrency, Clean Code/Kiến trúc, Frontend**.

> ✅ **Tin tốt:** quản lý secret **đúng chuẩn** — `.env` và `projects/cli-demo/secrets/prod.pem` đều **đã bị `.gitignore`, không hề commit** (xác nhận qua `git ls-files`/`git check-ignore`). Không tìm thấy secret hardcode trong mã nguồn.

---

## 📊 BẢNG PHÂN LOẠI & ƯU TIÊN

| # | Mức | Vấn đề | Trục | File |
|---|-----|--------|------|------|
| C1 | 🔴 Nghiêm trọng | Rate-limit chạy **sau** bcrypt → DoS CPU | Bảo mật/Perf | `SecurityConfig.java` |
| C2 | 🔴 Nghiêm trọng | `readRange` nạp **toàn bộ** file vào RAM → OOM | Bug/Perf | `SourceFileServiceImpl.java` |
| C3 | 🔴 Nghiêm trọng | Container backend chạy **quyền root**, không giới hạn heap | DevOps | `Dockerfile` |
| H1 | 🟠 Cao | Redact private key **chỉ 1 dòng header** → lộ khóa | Bảo mật | `SourceFileServiceImpl.java` |
| H2 | 🟠 Cao | XFF lấy IP **trái nhất** → giả mạo IP, né rate-limit | Bảo mật | `ClientAddressResolver.java` |
| H3 | 🟠 Cao | `compose` mount `.env` vào container + Neo4j APOC `unrestricted` + thiếu `mem_limit` BE/PG | DevOps | `docker-compose.yml` |
| H4 | 🟠 Cao | Ghi DB (`api_keys.save`) **mỗi request** → write amplification | Perf/DB | `ApiKeyAuthFilter.java` |
| M1 | 🟡 TB | `ACTIVE_USERS` static map dọn lười → memory leak | Bug/Perf | `JwtAuthFilter.java` |
| M2 | 🟡 TB | `IpBlockService.findActive` truy vấn DB mỗi request (không cache) | Perf/DB | `IpBlockFilter/Service` |
| M3 | 🟡 TB | Nguy cơ **race condition (TOCTOU)** khi cộng dồn quota `used_bytes` | Bug/DB | schema + service |
| M4 | 🟡 TB | **God components/classes** vi phạm SRP (2821, 2681, 1283 dòng) | Clean Code | `UserDetailDrawer.vue`,… |
| L1 | ⚪ Nhỏ | **2 nguồn sự thật** cho migration (`database/schema` vs `db/migration`) | Kiến trúc | thư mục |
| L2 | ⚪ Nhỏ | Dead code / scaffold rỗng (`getNeighborhood` ném exception, `ImpactController`) | Clean Code | repo/controller |
| L3 | ⚪ Nhỏ | Zip-bomb qua entry non-`.java`; redirect GitHub NORMAL; drop security-event khi queue đầy | Bảo mật (defense-in-depth) | importer/telemetry |

---

## 🔴 MỨC NGHIÊM TRỌNG (sửa ngay)

### C1 — Rate-limit chạy SAU khi đã tốn bcrypt (DoS CPU)
**File:** `src/main/java/com/vibegraph/auth/config/SecurityConfig.java` — cuối method `securityFilterChain` (khối `addFilter*`).

**Bằng chứng (đã đọc trực tiếp):**
```java
.addFilterAt(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterAfter(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(rateLimitFilter(...), AuthorizationFilter.class); // ← chạy gần cuối chain
```

**Tại sao:** `AuthorizationFilter` nằm cuối chain ⇒ `rateLimitFilter` chạy **sau** `apiKeyAuthFilter`. Mỗi request `X-API-Key: vbg_<prefix hợp lệ>` khiến `ApiKeyAuthFilter` chạy tới ~5 lần `passwordEncoder.matches()` (BCrypt cố ý chậm) **trước khi** rate-limit kịp chặn → kẻ tấn công đốt cạn CPU. Đã xác nhận `apiKeyRepository.save(...)` + BCrypt trong `ApiKeyAuthFilter`.

**Sửa:** đẩy `rateLimitFilter` lên **ngay sau `IpBlockFilter`**, trước các filter xác thực:
```java
.addFilterBefore(ipBlockFilter(clientAddressResolver()), UsernamePasswordAuthenticationFilter.class)
.addFilterAfter(rateLimitFilter(clientAddressResolver(), meterRegistry), IpBlockFilter.class) // trước JWT/API-key
.addFilterBefore(cookieCsrfFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterAt(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterAfter(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);
```
Bổ sung counter “API-key sai theo IP” để chặn brute-force prefix sớm.

### C2 — `readRange` nạp toàn bộ file vào RAM → OOM
**File:** `src/main/java/com/vibegraph/mcp/source/impl/SourceFileServiceImpl.java` — `readRange()` gọi `readAllLines(candidate)`.

**Bằng chứng:**
```java
List<String> lines = readAllLines(candidate); // Files.readAllLines → nạp CẢ file
// trần MAX_LINES/MAX_BYTES chỉ áp dụng SAU khi đã load hết
```
Nhánh `search` có chốt `Files.size(...) > MAX_FILE_BYTES_TO_SCAN` nhưng `readRange` **không có**. File đuôi hợp lệ nhưng vài trăm MB (`.sql/.md/.txt`) → OOM/GC DoS.

**Sửa:** chốt size trước khi đọc, và đọc theo dòng dừng sớm:
```java
if (Files.size(candidate) > MAX_FILE_BYTES) {
    throw new SourceTooLargeException(candidate.toString(), MAX_FILE_BYTES);
}
try (Stream<String> s = Files.lines(candidate, StandardCharsets.UTF_8)) {
    lines = s.skip(startLine - 1).limit(endLine - startLine + 1L).toList();
}
```

### C3 — Container backend chạy quyền root + không giới hạn heap JVM
**File:** `Dockerfile` (root).

**Bằng chứng:**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]   # ← chạy root, không có -XX:MaxRAMPercentage
```

**Tại sao:** (1) Process chạy **root** trong container → nếu có RCE/traversal (app còn ghi file vào `/projects`), thiệt hại leo thang. (2) Không đặt `MaxRAMPercentage` ⇒ JVM có thể vượt `mem_limit` container → OOM-kill đột ngột. (3) Không có `.dockerignore` rõ ràng cho context.

**Sửa:**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
RUN chown -R app:app /app
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
```

---

## 🟠 MỨC CAO

### H1 — Redact private key chỉ khớp dòng header → lộ thân khóa
**File:** `SourceFileServiceImpl.java` — `redact()` chạy per-line.
```java
private static final Pattern PRIVATE_KEY_HEADER = Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----");
...
if (PRIVATE_KEY_HEADER.matcher(result).find()) { result = REDACTED; } // chỉ che DÒNG header
```
**Tại sao:** Các dòng base64 thân khóa **không khớp** pattern ⇒ trả nguyên văn. `GET /api/projects/{id}/source` trên file chứa khóa (đuôi được allow) sẽ lộ toàn bộ khóa.

**Sửa:** redact theo **khối có trạng thái** (thấy `BEGIN` → che đến `END`), hoặc từ chối hẳn file có header private key:
```java
boolean inKey = false;
for (String line : lines) {
    if (BEGIN.matcher(line).find()) inKey = true;
    out.add(inKey ? REDACTED : redactInline(line));
    if (END.matcher(line).find()) inKey = false;
}
```

### H2 — XFF lấy token trái nhất → giả mạo IP
**File:** `abuse/ClientAddressResolver.java` — `resolve()` dùng `.findFirst()` trên `X-Forwarded-For`.
```java
return Arrays.stream(forwarded.split(","))...
    .filter(ClientAddressResolver::isPublicClientAddress)
    .findFirst()      // ← token TRÁI NHẤT do client kiểm soát
    .orElse(remote);
```
**Tại sao:** Attacker gửi `X-Forwarded-For: <IP giả>` → khóa rate-limit `ip:<giả>` xoay vòng liên tục, né luôn `IpBlockService`.

**Sửa:** đếm & bóc N proxy tin cậy từ **phải sang**, lấy hop **phải nhất** không tin cậy:
```java
String[] hops = forwarded.split(",");
for (int i = hops.length - 1; i >= 0; i--) {
    String ip = hops[i].trim();
    if (!isTrustedProxy(ip)) return ip; // client thật là hop ngoài cùng phía sau proxy
}
```

### H3 — docker-compose: rò rỉ/thừa secret + APOC unrestricted + thiếu resource limit
**File:** `docker-compose.yml`.

- **Mount `.env` vào container dù đã inject env:**
```yaml
volumes:
  - ./.env:/app/.env:ro   # ← trùng lặp, làm lộ TẤT CẢ secret vào FS container (kể cả secret không cần)
```
→ Bỏ mount này (biến môi trường đã được truyền tường minh ở block `environment`).

- **Neo4j mở toàn bộ APOC:**
```yaml
NEO4J_dbms_security_procedures_unrestricted: apoc.*
```
→ Thu hẹp còn đúng procedure cần (`apoc.coll.*,apoc.map.*` …) hoặc bỏ nếu app không gọi APOC.

- **Chỉ `neo4j` có `mem_limit`; `backend` và `postgres` không có** → một analyze nặng có thể ăn hết RAM host. Thêm:
```yaml
backend:
  mem_limit: 2g
  cpus: "2.0"
postgres:
  mem_limit: 512m
```

- Cân nhắc **không map cổng Postgres/Neo4j ra host** ở môi trường prod (chỉ để trong network nội bộ).

### H4 — Ghi `api_keys` mỗi request dùng API key (write amplification)
**File:** `auth/web/ApiKeyAuthFilter.java`.
```java
key.setLastUsedAt(Instant.now());
apiKeyRepository.save(key);   // UPDATE trên MỖI request dùng API key
```
**Tại sao:** MCP/CLI gọi liên tục ⇒ UPDATE + tranh chấp row-lock trên `api_keys` mỗi request. (Lưu ý: lookup theo `key_prefix` **đã có index** ở `V13__api_key_lifecycle.sql` — chỗ này ổn; vấn đề là **ghi**.)

**Sửa:** throttle `lastUsedAt` (tối đa 1 lần/phút/khóa) hoặc gom async:
```java
if (key.getLastUsedAt() == null ||
    Duration.between(key.getLastUsedAt(), Instant.now()).toMinutes() >= 1) {
    key.setLastUsedAt(Instant.now());
    apiKeyRepository.save(key);
}
```

---

## 🟡 MỨC TRUNG BÌNH

### M1 — `ACTIVE_USERS` static map rò rỉ bộ nhớ
**File:** `auth/web/JwtAuthFilter.java`.
```java
private static final Map<UUID, Long> ACTIVE_USERS = new ConcurrentHashMap<>();
...
ACTIVE_USERS.put(decision.principal().id(), System.currentTimeMillis()); // mỗi request
// chỉ dọn khi getActiveUsersCount() được gọi: ACTIVE_USERS.values().removeIf(...)
```
**Tại sao:** Nếu endpoint metric admin ít/không được gọi, entry sống suốt vòng đời JVM → phình heap theo số user.

**Sửa:** dùng cache có TTL:
```java
private static final Cache<UUID, Long> ACTIVE_USERS =
    Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).build();
```

### M2 — `IpBlockService.findActive` query DB mỗi request
**File:** `abuse/IpBlockFilter`/`IpBlockService.findActive` (chạy sớm cho **mọi** request).

**Tại sao:** 1 query + 1 transaction readOnly/request trên toàn API kể cả khi không có block nào; `canonicalize` còn bị gọi lặp.

**Sửa:** cache in-memory tập IP đang block (nạp active + refresh định kỳ / TTL ngắn), tra O(1) trước khi chạm DB.

### M3 — Nguy cơ race condition khi cộng dồn quota `used_bytes`
**File:** `database/schema/V1__init_auth_schema.sql` (`users.used_bytes/quota_bytes`) + service import.

**Tại sao:** Với `concurrent-imports-per-user` mặc định 1 thì rủi ro thấp, nhưng nếu tăng giá trị này hoặc có nhiều luồng, mẫu **đọc used_bytes → kiểm tra quota → +size** dễ bị TOCTOU nếu không khoá dòng.

**Sửa:** cập nhật nguyên tử có điều kiện trong 1 câu lệnh:
```sql
UPDATE users SET used_bytes = used_bytes + :size
WHERE id = :id AND used_bytes + :size <= quota_bytes;
-- rowCount = 0 ⇒ vượt quota, reject
```

### M4 — God components/classes vi phạm SRP (SOLID)
**Bằng chứng (số dòng thực đo):**
- `vibegraph-web/src/views/admin/UserDetailDrawer.vue` — **2821 dòng**
- `vibegraph-web/src/views/LandingView.vue` — **2681 dòng**
- `vibegraph-web/src/components/graph/GraphCanvas.vue` — **1325 dòng**
- `src/main/java/com/vibegraph/diagram/service/impl/UseCaseInferenceEngine.java` — **1283 dòng**

**Tại sao:** File 2000+ dòng gộp nhiều trách nhiệm (state, fetch, render, business rule) → khó test, khó tái sử dụng, dễ merge-conflict, vi phạm Single Responsibility.

**Sửa (Vue):** tách theo composables + component con:
```
UserDetailDrawer.vue        (chỉ layout + orchestration)
├─ composables/useUserDetail.ts   (fetch/state)
├─ UserQuotaSection.vue
├─ UserSecuritySection.vue
└─ UserActivityTable.vue
```
**Sửa (Java `UseCaseInferenceEngine`):** áp dụng **Strategy pattern** cho từng heuristic suy luận (naming-strip, actor-mapping, LLM-relabel) thành các class `*InferenceStep` implement chung interface, engine chỉ điều phối pipeline.

---

## ⚪ MỨC NHỎ / CẢI THIỆN

### L1 — Hai nguồn sự thật cho migration
`database/schema/V1,V2*.sql` **trùng** với `src/main/resources/db/migration/V1,V2*.sql`. Dễ lệch phiên bản. → Giữ **một** nguồn (khuyến nghị `db/migration` do Flyway đọc), thư mục kia chỉ symlink/generated hoặc xóa.

### L2 — Dead code / scaffold rỗng
- README tự nhận: `Neo4jGraphRepository.getNeighborhood` **ném `UnsupportedOperationException`** (chưa làm) và `ImpactController` là **scaffold rỗng** (impact thật đang chạy ở `GraphController /graph/impact`). → Hoặc hoàn thiện, hoặc xóa để tránh gây hiểu nhầm/endpoint chết.
- Dò `import`/biến không dùng nên chạy bằng công cụ (xem “Khuyến nghị công cụ”).

### L3 — Phòng thủ chiều sâu (từ security-perf-audit, đã đối chiếu code)
- **Zip/tar-bomb** qua entry non-`.java`: cộng dồn tổng byte **giải nén trên MỌI entry** và abort khi vượt trần, không chỉ entry `.java`.
- **GitHub redirect `NORMAL`** + owner/repo cho phép `.`/`..`: chặn `.`/`..`, kiểm tra host sau redirect vẫn thuộc `github.com/codeload.github.com`.
- **Telemetry shed oldest** có thể drop cả security-event (RATE_LIMIT): tách hàng đợi riêng cho security-event, không bị shed.

---

## ✅ NHỮNG ĐIỂM LÀM TỐT (không phải lỗi — ghi nhận)

- **Không có Cypher injection:** `Neo4jGraphRepository` dùng **tham số hóa** (`$projectId`, `$batch`…); chỉ label/relationship-type mới nội suy qua `String.format`, và được **validate bằng enum** `GraphSchema`/`ImpactProfile` (không nhận input tự do) → an toàn.
- **Quản lý secret đúng:** `.env`, `prod.pem` **không commit** (đã kiểm chứng `git`), config toàn bộ dùng `${ENV}`, JWT secret bắt buộc ≥64 byte fail-fast.
- **Auth FE đúng chuẩn:** JWT nằm trong **HttpOnly cookie**; `localStorage` chỉ lưu user JSON không nhạy cảm (`stores/auth.ts`).
- **Fail-closed:** `/mcp/**` yêu cầu `API_KEY` trừ khi bật `demo-permit` (có log WARN). `application-prod.yaml` hạ log về `WARN/INFO`, bật compression + prometheus, CORS chỉ `${FRONTEND_URL}`.
- **Path-traversal/patch:** validate-all-before-write, chống symlink bằng `toRealPath`, journal backup + atomic move.

---

## 🧭 LỘ TRÌNH KHẮC PHỤC ĐỀ XUẤT

1. **Ngay (🔴):** C1 (đổi thứ tự filter), C2 (chốt size trước readAllLines), C3 (USER non-root + MaxRAMPercentage).
2. **Tuần này (🟠):** H1 (redact khối khóa), H2 (XFF phải-nhất), H3 (siết compose), H4 (throttle lastUsedAt).
3. **Sprint sau (🟡):** M1/M2 (cache TTL), M3 (quota atomic), M4 (tách god components).
4. **Backlog (⚪):** hợp nhất migration, dọn dead code, defense-in-depth L3.

### Khuyến nghị công cụ (tự động hoá phần “dead code / import không dùng”)
Không thể liệt kê chính xác 703 file thủ công — nên bật:
- **Frontend:** `vue-tsc --noEmit` + ESLint `@typescript-eslint/no-unused-vars`.
- **Backend:** `mvn com.github.spotbugs:spotbugs`, IntelliJ “unused declaration”, Checkstyle `UnusedImports`.
