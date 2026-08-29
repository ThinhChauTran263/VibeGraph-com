# Báo cáo kiểm thử Bảo mật & Hiệu năng — VibeGraph API

**Vai trò:** Kỹ sư kiểm thử bảo mật & hiệu năng
**Phạm vi:** Toàn bộ bề mặt API trọng yếu (filter chain, xác thực, import/đọc/ghi file, ownership). Phân tích tĩnh (code review).
**Ngày:** 2026-08-12 · **Bản:** đã đồng bộ đính chính (F7 rút lại, F8 xác nhận, F11 bổ sung)

## Các file đã soi (28)
Filter/security: `SecurityConfig`, `JwtAuthFilter`, `ApiKeyAuthFilter`, `CookieCsrfFilter`, `IpBlockFilter`, `IpBlockService`, `StatelessSessionCookieFilter`, `RateLimitFilter`, `ClientAddressResolver`, `RequestEventService`.
Auth core: `JwtService`, `AccountAccessGuard`.
Import: `ImportController`, `ArchiveImportService`, `TarballImportService(Impl)`, `ArchiveExtractor`, `GitHubUrlParser`, `GitHubTarballClient`, `GitHubPreFlightService`.
File I/O: `SourceController`, `SourceFileService(Impl)`, `LocalPatchController`, `LocalPatchService(Impl)`, `AtomicPatchApplier`.
Ownership/CRUD: `ProjectOwnershipGuard`, `ProjectTrashService`, `ApiKeyRequestContextAccessor`, `GraphController`, `ProjectController`, `DiagramController`.

---

## Tóm tắt mức độ
| # | Mức | Vấn đề | Loại |
|---|-----|--------|------|
| F1 | 🔴 Cao | Rate-limit chạy SAU xác thực bcrypt | DoS CPU |
| F2 | 🔴 Cao | `readRange` không chốt size trước `readAllLines` | DoS bộ nhớ (OOM) |
| F3 | 🟠 TB-Cao | Chỉ redact dòng header private key | Rò rỉ dữ liệu |
| F4 | 🟠 TB | XFF lấy IP trái nhất → giả mạo IP | Vượt rate-limit/IP-block |
| F5 | 🟠 TB | `ACTIVE_USERS` static dọn lười | Rò rỉ bộ nhớ |
| F6 | 🟠 TB | Ghi DB mỗi request dùng API key | Nghẽn DB |
| ~~F7~~ | ⚪ Rút lại | ~~Telemetry đồng bộ trong hot path~~ — **SAI**, xem đính chính | — |
| F8 | 🟠 TB | `IpBlockService.findActive` truy vấn DB **mỗi request** (không cache) | Nghẽn DB |
| F9 | 🟡 Thấp | Zip-bomb qua entry non-`.java` | DoS CPU |
| F10| 🟡 Thấp | Redirect NORMAL + owner/repo cho phép `.`/`..` | Làm chặt SSRF |
| F11| 🟡 Thấp | Queue telemetry đầy → drop cả security event (RATE_LIMIT) | Mù giám sát |

**Kết quả kiểm tra IDOR/traversal/alg-confusion:** không tìm thấy lỗ hổng khai thác được (xem mục "Đã kiểm chứng an toàn").

---

## Chi tiết (bằng chứng + giải pháp)

### F1 🔴 Rate limiting chạy SAU khi đã tốn bcrypt
**Bằng chứng** — `SecurityConfig.securityFilterChain`:
```java
.addFilterAt(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterAfter(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(rateLimitFilter(...), AuthorizationFilter.class);
```
`AuthorizationFilter` nằm gần cuối chain ⇒ `rateLimitFilter` chạy **sau** `jwtAuthFilter`/`apiKeyAuthFilter`. Trong `ApiKeyAuthFilter.findMatch()` mỗi request đúng định dạng chạy tới **5 lần** `passwordEncoder.matches()` (BCrypt — cố ý chậm):
```java
List<ApiKey> candidates = apiKeyRepository.findTop6ByKeyPrefix...(prefix);
...
.filter(key -> passwordEncoder.matches(presented, key.getKeyHash()))
```
Kẻ tấn công dội request tới `/mcp/**` hoặc `/api/projects/*/patch` với `X-API-Key: vbg_<đúng prefix>` → đốt CPU bằng bcrypt **trước khi** rate-limit chặn. `IpBlockFilter` chỉ chặn IP đã bị block sẵn.
**Giải pháp:** đặt `rateLimitFilter` trước `jwtAuthFilter`/`apiKeyAuthFilter` (ngay sau `IpBlockFilter`); thêm giới hạn riêng số lần thử API-key sai theo IP.

### F2 🔴 `readRange` nạp cả file vào RAM → OOM
**Bằng chứng** — `SourceFileServiceImpl.readRange`:
```java
List<String> lines = readAllLines(candidate); // Files.readAllLines — nạp TOÀN BỘ file
```
Không kiểm tra `Files.size()` trước đó (chỉ `isLikelyBinary` đọc 8KB đầu). Nhánh `search` thì có chốt: `if (Files.size(path) > MAX_FILE_BYTES_TO_SCAN ...) return;`. Trần `MAX_LINES`/`MAX_BYTES` chỉ áp dụng **sau khi** đã load hết. Một file đuôi hợp lệ nhưng khổng lồ (`.sql`/`.txt`/`.md`, vài trăm MB) → nạp hết vào heap → OOM/GC DoS.
**Giải pháp:** thêm chốt `Files.size(candidate) > MAX_FILE_BYTES` trong `readRange`; hoặc đọc theo dòng, dừng sớm tại `endLine` + trần byte.

### F3 🟠 Rò rỉ private key — chỉ redact 1 dòng header
**Bằng chứng** — `SourceFileServiceImpl.redact` chạy per-line; `readRange` lặp từng dòng:
```java
if (PRIVATE_KEY_HEADER.matcher(result).find()) { result = REDACTED; }
```
`PRIVATE_KEY_HEADER = "-----BEGIN [A-Z ]*PRIVATE KEY-----"` chỉ khớp **dòng header**. Các dòng base64 thân khóa phía sau không khớp pattern nào ⇒ trả về **nguyên văn**. `GET /api/projects/{id}/source` trên file chứa khóa (đuôi được phép) sẽ lộ toàn bộ thân khóa.
**Giải pháp:** redact theo khối đa dòng có trạng thái (thấy `BEGIN` → che tới `END`), hoặc từ chối hẳn file chứa header private key.

### F4 🟠 XFF lấy địa chỉ trái nhất → giả mạo IP
**Bằng chứng** — `ClientAddressResolver.resolve` (khi `trustProxy` và remote là proxy tin cậy):
```java
return Arrays.stream(forwarded.split(","))...
    .filter(ClientAddressResolver::isPublicClientAddress)
    .findFirst()   // <-- token TRÁI NHẤT (client tự đặt)
    .orElse(remote);
```
Token XFF trái nhất do client kiểm soát. Attacker gửi `X-Forwarded-For: <IP công cộng ngẫu nhiên>` → khóa rate-limit `ip:<giả>` xoay vòng liên tục và né `IpBlockService`.
**Giải pháp:** lấy IP từ hop **phải nhất** sau khi bóc N proxy tin cậy (đếm hop), không lấy trái nhất.

### F5 🟠 `ACTIVE_USERS` rò rỉ bộ nhớ
**Bằng chứng** — `JwtAuthFilter`:
```java
private static final Map<UUID, Long> ACTIVE_USERS = new ConcurrentHashMap<>();
...
ACTIVE_USERS.put(decision.principal().id(), System.currentTimeMillis()); // mỗi request auth
```
Chỉ dọn bên trong `getActiveUsersCount()` (`removeIf`). Nếu endpoint metric admin không được gọi, entry tồn tại suốt vòng đời JVM.
**Giải pháp:** dùng Caffeine `expireAfterWrite(5m)` (như `RateLimitFilter`), hoặc quét dọn theo lịch.

### F6 🟠 Ghi DB mỗi request dùng API key
**Bằng chứng** — `ApiKeyAuthFilter.authenticate`:
```java
key.setLastUsedAt(Instant.now());
apiKeyRepository.save(key);   // UPDATE trên MỖI request dùng API key
```
Gây write-amplification + tranh chấp khóa dòng `api_keys` khi tải cao.
**Giải pháp:** throttle `lastUsedAt` (vd tối đa 1 lần/phút/khóa) hoặc ghi async/batch.

### ~~F7~~ ⚪ ĐÍNH CHÍNH — Telemetry KHÔNG đồng bộ (phát hiện sai, đã rút lại)
Sau khi đọc `RequestEventService`, xác nhận `record()` chỉ **normalize + `offer()`** vào `ArrayBlockingQueue` bounded; việc ghi Supabase do `@Scheduled flush()` gom batch chạy nền (có retry backoff, bisect batch lỗi, drain khi shutdown). Đây là thiết kế **tốt**, không phải hot-path DB write. Nhận định F7 ở bản trước là **sai** và được rút lại. Chi phí mỗi request chỉ là normalize + enqueue → không đáng kể.

### F8 🟠 `IpBlockService.findActive` truy vấn DB mỗi request (xác nhận)
**Bằng chứng** — `IpBlockFilter.doFilterInternal` chạy sớm cho **mọi** request, gọi:
```java
@Transactional(readOnly = true)
public Optional<IpBlock> findActive(String ipAddress) {
    return repository.findActive(ClientAddressResolver.canonicalize(ipAddress), clock.instant());
}
```
Không có cache ⇒ **1 truy vấn DB + 1 giao dịch readOnly mỗi request** trên toàn bộ API (kể cả khi không có block nào). Ngoài ra `canonicalize` bị gọi lại lần 2 (filter đã resolve rồi).
**Giải pháp:** cache set IP đang bị block trong bộ nhớ (nạp toàn bộ active + refresh định kỳ, hoặc TTL ngắn), tra cứu O(1) trước khi chạm DB.

### F9 🟡 Zip/tar-bomb qua entry KHÔNG `.java`
**Bằng chứng** — `ArchiveExtractor.materializeIfJava` bỏ qua entry không phải `.java` **trước khi** đọc; trần byte (`copyCapped` so với `maxBytes`) chỉ áp cho entry `.java` được ghi. Entry non-`.java` vẫn bị bung nén (inflate) khi stream nhảy qua → tốn CPU/thời gian. Bị chặn phần nào bởi `Files.size(archivePath) > maxBytes` (giới hạn kích thước nén).
**Giải pháp:** cộng dồn tổng byte **giải nén** trên MỌI entry (kể cả entry bỏ qua) và abort khi vượt trần.

### F10 🟡 Redirect NORMAL + owner/repo lỏng
**Bằng chứng** — `GitHubTarballClient`/`GitHubPreFlightService` dùng `HttpClient.Redirect.NORMAL`; `GitHubUrlParser.SEGMENT = [A-Za-z0-9_.-]+` cho phép owner/repo là `.` hoặc `..` → sinh `repos/../x`. Host cố định `api.github.com` nên không SSRF nội bộ được, nhưng nên siết.
**Giải pháp:** giữ allow-list host (đang tốt), thêm từ chối owner/repo `.`/`..`, và kiểm tra host đích sau redirect vẫn thuộc github.com/codeload.github.com.

### F11 🟡 Queue telemetry đầy có thể drop security event
**Bằng chứng** — `RequestEventService.offer()` khi `freshQueue` đầy sẽ **shed oldest** (`freshQueue.poll()` rồi `countDrop`); `countDrop` tăng cả `securityDropped` nếu event có `securityEvent` (RATE_LIMIT). Vậy một đợt flood vượt `drainCeilingPerSecond` có thể đẩy chính các sự kiện RATE_LIMIT ra khỏi hàng đợi → admin mất tín hiệu cảnh báo tấn công. Là "best-effort" theo thiết kế, nhưng đáng lưu ý.
**Giải pháp:** ưu tiên security event (hàng đợi riêng không bị shed, hoặc ghi thẳng security event vào kênh audit bền vững tách khỏi telemetry request thường).

---

## Đã kiểm chứng an toàn (không phải lỗ hổng)
- **IDOR:** `GraphController`, `DiagramController`, `ProjectController` — mọi endpoint theo project gọi `ProjectOwnershipGuard.assertOwner(projectId)`. `restore`/`purge` kiểm tra quyền tại tầng service qua `ProjectTrashService.requireOwnedTrashedProject` (lọc theo `currentUser.id()`). *Ghi chú maintainability: `purge` không có assert ở controller như các endpoint khác — an toàn nhờ service, nhưng nên nhất quán.*
- **Path/write traversal (patch):** `LocalPatchServiceImpl` validate-all-before-write, chặn `..`/absolute/drive/backslash/control-char, chống symlink-escape bằng `toRealPath`, chặn thư mục/tên/đuôi/archive nhạy cảm, reject binary, giới hạn count/size/quota. `AtomicPatchApplier` dùng journal backup + atomic move + rollback → không để lại trạng thái dở.
- **JWT:** `JwtService` HS512, key ≥64 byte fail-fast, kiểm tra `alg` header == HS512 (chặn alg-confusion/`none`), role fail-closed về `USER`.
- **API-key scope:** `ApiKeyRequestContextAccessor.assertProjectMatches` chặn khóa bound-project patch sang project khác.
- **Archive:** `ArchiveExtractor` từ chối `..`/absolute/drive/symlink/hardlink, `copyCapped` trần byte cho file ghi ra.
- **Source read:** `SourceFileServiceImpl` resolve path = normalize + `startsWith(root)` + `toRealPath` chống symlink; allow-list đuôi; chặn thư mục/tên nhạy cảm.
- **CSRF/CORS:** `CookieCsrfFilter` custom-header cho request dùng cookie; CORS origin tường minh, không wildcard kèm credentials.

## Ưu tiên khắc phục đề xuất
1. **F1, F2** (🔴) — vá ngay: đổi thứ tự filter; chốt size trước `readAllLines`.
2. **F3, F4, F8** (🟠) — rò rỉ dữ liệu & nghẽn DB: redact khối private key; sửa cách chọn IP từ XFF; cache IP-block.
3. **F5, F6** (🟠) — leak bộ nhớ & write amplification.
4. **F9, F10, F11** (🟡) — làm chặt phòng thủ chiều sâu.
