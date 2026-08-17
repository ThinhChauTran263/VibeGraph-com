# KIỂM CHỨNG CHÉO 4 BÁO CÁO AUDIT — VibeGraph

- **Ngày:** 12/08/2026
- **Loại tài liệu:** không phải audit thứ 5. Đây là **kiểm chứng bằng lệnh** các claim của 4 báo cáo đã có, tập trung vào những chỗ chúng **mâu thuẫn nhau**.
- **Nguyên tắc:** mỗi kết luận dưới đây kèm lệnh đã chạy. Không có kết luận nào dựa trên "đọc thấy hợp lý" hay "2/3 báo cáo đồng thuận".
- **Chưa sửa code.** Tài liệu phân tích.

Script chạy lại toàn bộ: [`verify-claims.sh`](./verify-claims.sh) (read-only, không xoá/ghi gì).

---

## 0. Phát hiện về chính bộ tài liệu

**`update/docs/codex/VibeGraph-audit-report-2026-08-12.md` ≡ `docs/audit-report-v2-2026-08-12.md`.** Không phải 2 báo cáo. `diff` ra đúng 5 hunk: bản trong `docs/` thêm H8 (npm audit) và §10 (đối chiếu `security-perf-audit.md`). File snippets thì giống hệt byte-for-byte.

Nên số nguồn thực tế là **3**: Codex (= audit-report-v2), Qwen, ClaudePostman — cộng 2 vòng trước (`update/AUDIT-REPORT.md`, `update/security-perf-audit.md`).

**Nhãn phiên bản của audit-report-v2 không nhất quán:** header ghi `v2.2` (3 lần) nhưng thân báo cáo tự gọi là `v2.1` (11 lần), kể cả câu phân loại chính ở §1. H8 là mục mới của v2.2 nhưng không được đánh dấu `(bổ sung bản v2.2)` như §10. Bản Codex gốc còn ghi "**7** vấn đề High" rồi liệt kê **8** mã H1–H8 trong cùng một câu.

```bash
diff docs/audit-report-v2-2026-08-12.md update/docs/codex/VibeGraph-audit-report-2026-08-12.md
grep -oE 'v2\.[0-9]' docs/audit-report-v2-2026-08-12.md | sort | uniq -c
```

---

## 1. Bảng verdict — các claim mâu thuẫn, đã kiểm bằng lệnh

| Claim | Nguồn | Kết quả kiểm chứng | Verdict |
|---|---|---|---|
| Secret production thật nằm trong **git object database** | **chỉ Qwen** (S2) | `stash@{0}` = `31ebcda`, có **3 parent**; parent thứ 3 = `388632b`, chứa `.env.codex-backup-before-905919f-20260725-140030` (159 dòng) với 7 biến nhạy cảm: `JWT_SECRET`, `GOOGLE_CLIENT_SECRET`, `GITHUB_CLIENT_SECRET`, `GEMINI_API_KEY(S)`, `NEO4J_PASSWORD`, `POSTGRES_PASSWORD`. `git log --all -S'GOCSPX-'` → đúng 1 commit, chính là `388632b` | ✅ **ĐÚNG** |
| "Quản lý secret **đúng chuẩn** — `.env` không hề commit (xác nhận qua `git ls-files`/`check-ignore`)" | ClaudePostman | `git ls-files` chỉ đọc **index**, không thấy được object DB. Kết luận sai vì phương pháp kiểm sai | ❌ **SAI** |
| "Secrets là **action item vận hành**, không phải finding codebase; gitignore ĐÃ HOÀN THÀNH" | Codex/v2 §4 | Cùng lỗi phương pháp. Việc hạ secrets khỏi bảng Critical là quyết định sai | ❌ **SAI phân loại** |
| Rate-limit chạy **sau** BCrypt → DoS CPU | ClaudePostman C1, Qwen H13 | `SecurityConfig.java:184` `addFilterBefore(rateLimitFilter, AuthorizationFilter.class)` — `AuthorizationFilter` ở cuối chain, nên chạy sau `jwtAuthFilter:182` và `apiKeyAuthFilter:183` | ✅ ĐÚNG |
| `readRange` nạp cả file trước khi chốt trần | ClaudePostman C2, Qwen H14 | `SourceFileServiceImpl.java:110` `readAllLines` không chốt; trần áp sau ở `:122–136`. Nhánh search **có** chốt `Files.size()` ở `:196` | ✅ ĐÚNG |
| Redact private key chỉ che dòng header | ClaudePostman H1, Qwen H15 | `:68` pattern chỉ khớp `-----BEGIN…`; áp per-line ở `:316–317` → thân base64 trả nguyên văn | ✅ ĐÚNG |
| `VIBEGRAPH_TRUST_PROXY=true` trong `.env` **thật** | **chỉ Qwen** (S-M2) | `.env:104` = `true`; `:105` trusted = `172.18.0.0/16,127.0.0.1` | ✅ ĐÚNG |
| "trust-proxy mặc định false **là điểm mạnh**" | Codex/v2 §5 | Đúng với yaml default, **sai với triển khai thực**. v2 §10.3 còn đặt câu hỏi mở *"kế hoạch có bật trustProxy không?"* — đáp án nằm ở `.env` dòng 104 | ⚠️ **Sai bối cảnh** |
| 9 file dead code frontend | **chỉ Qwen** (F-M1) | 0 import ngoài. Siết thêm: 4 tham chiếu còn lại (`HeaderBar`, `SidePanel` ×2, `CodeInspector`) đều là **comment/TODO**, không phải import. Tổng **1.319 dòng** | ✅ ĐÚNG |
| Project ID = `UUID…substring(0,8)` | **chỉ Qwen** (H7) | `ProjectServiceImpl.java` dòng **62, 91, 101** (3 chỗ) | ✅ ĐÚNG (số dòng sai, xem §3) |
| Actuator `metrics/prometheus` mọi USER đọc được | **chỉ Qwen** (S-M3) | `application-prod.yaml:111` include `health,info,metrics,prometheus`; `SecurityConfig:161` chỉ permitAll `/actuator/health`, còn lại `authenticated()` | ✅ ĐÚNG |
| `.dockerignore` frontend không tồn tại | **chỉ Qwen** (H5) | `vibegraph-web/.dockerignore` không có | ✅ ĐÚNG |
| "Không có `.dockerignore` rõ ràng" (root) | ClaudePostman C3 | Root `.dockerignore` **có tồn tại**, 112 bytes | ❌ SAI |
| npm audit 8 lỗ hổng | Codex/v2 H8, Qwen H16 | `npm audit --json` → `{moderate:1, high:6, critical:1, total:8}`. critical = `websocket-driver`; high = `axios`, `brace-expansion`, `nanoid`, `postcss`, `shell-quote`, `undici`; moderate = `jsdom`. **v2 H8 chỉ kể 7 package (thiếu `jsdom`); Qwen H16 kể đủ 8** | ✅ ĐÚNG (Qwen chính xác hơn) |
| N+1 trong `AdminService` | **chỉ Qwen** (H9) | `toAdminUserResponse` mỗi user gọi `settingsRepository.findById` + `projectUsageRepository.sumStorageBytesByOwnerId` → 2 query/user | ✅ ĐÚNG |
| `UsersTableView` 5 điểm gọi API không try/catch | **chỉ Qwen** (H11) | 8 lời gọi `adminStore.*`, 3 `try {`, 3 `catch` → **5 điểm không bọc**. Khớp con số | ✅ ĐÚNG |
| `IpBlockService.findActive` query DB mỗi request | ClaudePostman M2, v2 §10 F8, Qwen B-M9 | `IpBlockService.java:32–35` `@Transactional(readOnly)`, không `@Cacheable` | ✅ ĐÚNG |
| Telemetry shed-oldest drop cả security event | v2 §10 F11, Qwen B-L8 | `RequestEventService.java:359` `freshQueue.poll()`; counter `securityDropped` `:343`; metric `security_events.dropped.total` `:56` | ✅ ĐÚNG |
| `Boolean.getBoolean` đọc system property JVM | **chỉ Qwen** (B-M3) | `MethodVisitor.java:68` đúng. Nhưng key `vibegraph.parser.emit-unresolved-call-stubs` **không hề có** trong `src/main/resources/` → không phải "yaml bị vô hiệu", mà là toggle chỉ-JVM không có đường cấu hình | ⚠️ Cơ chế đúng, diễn giải lệch |
| `task/` và `task-final/` trùng, cùng track | **chỉ Qwen** (D-M5) | 8 file mỗi bên | ✅ ĐÚNG |

---

## 2. Số dòng: bằng chứng cho thấy con số bị thừa kế mà không đo lại

| File | **Thật (`wc -l`)** | Qwen | ClaudePostman | Codex/v2 |
|---|---|---|---|---|
| `UserDetailDrawer.vue` | **3201** | 3202 ✅ | 2821 ❌ | ghi KB, né số dòng |
| `LandingView.vue` | **2958** | 2959 ✅ | 2681 ❌ | ghi KB |
| `GraphCanvas.vue` | **1468** | 1469 ✅ | 1325 ❌ | 1464 ~ |
| `useSigma.ts` | **1037** | 1038 ✅ | — | 1038 ✅ |
| `lib/api.ts` | **989** | 990 ✅ | — | 990 ✅ |
| `stores/admin.ts` | **782** | 783 ✅ | — | 783 ✅ |
| `UseCaseInferenceEngine.java` | **1398** | 1283 ❌ | 1283 ❌ | 1283 ❌ |

Qwen đo thật (lệch đúng 1 do quy ước đếm dòng cuối). ClaudePostman lệch 12–20% ở 3 file — không phải đo.

Con số **1283** xuất hiện y nguyên trong **cả ba** báo cáo trong khi thật là **1398**. Đây là bằng chứng trực tiếp: một số liệu cũ được chép vòng qua vòng, không ai đo lại. Yêu cầu "BẮT BUỘC cung cấp vị trí dòng" trong prompt tạo ra số liệu, nhưng không tạo ra việc kiểm số liệu.

---

## 3. Những gì Qwen bản 15:12 (69 finding) đã cải thiện — và chưa

### Làm tốt hơn cả 3 bản gốc

1. **H13 là tổng hợp, không phải copy.** Ghép rate-limit-sau-BCrypt (từ ClaudePostman) với `TRUST_PROXY=true` (tự tìm) thành **một chuỗi tấn công sống**: gửi API key sai + `X-Forwarded-For` giả → bào CPU BCrypt mà rate-limit không chạm tới vì khóa rate-limit xoay theo IP giả. Không báo cáo gốc nào nhìn ra chuỗi này.
2. **Gắn nhãn xuất xứ.** B-M11 (Neo4j upsert non-atomic) ghi rõ *"xác nhận chéo từ 2 báo cáo độc lập, **chưa tự kiểm chứng sâu**"*.
3. **Verify âm — loại bỏ finding sai của người khác.** §8: phát hiện *"serve `projects/` qua HTTP port 8080"* nhận từ đối chiếu đã được xác minh là **không tồn tại** trong code nên **không đưa vào báo cáo**. Trong cả 4 tài liệu, đây là lần duy nhất có ai bác bỏ một finding bằng bằng chứng thay vì đẩy vào "cần thảo luận".
4. **Chạy lệnh thật.** H16 khớp `npm audit` từng package, kể cả `jsdom` mà v2 thiếu.

### Chưa sửa

| Mục | Qwen viết | Thật |
|---|---|---|
| B-M2 | `UseCaseInferenceEngine.java : 1–1283` | **1398** |
| H7 | "dòng 62, **72**" | 62, **91**, **101** |
| F-M1 | "~1.328 dòng" | 1319 |
| S2 | file `.env.codex_backup-before-**9e1dfed**-…**140618**` | Trong object `388632b` là `.env.codex-backup-before-**905919f**-…**140030**`. Bản `9e1dfed` là file ở **working tree**, không phải bản trong git → lẫn 2 file khác nhau |

Nó thêm 9 finding có kiểm chứng nhưng không rà lại 60 finding cũ.

---

## 4. Hai khuyến nghị của các báo cáo cần sửa trước khi thực thi

### 4.1. Remediation cho secret (S2) hiện **không đủ**

Qwen đề xuất `git stash drop stash@{0}` + `git filter-repo --replace-text`.

- **`git stash drop` không xoá object.** Sau khi drop, `388632b` vẫn sống trong reflog đến khi hết hạn + gc. Lệnh đủ:
  ```bash
  git stash drop stash@{0}
  git reflog expire --expire=now --all
  git gc --prune=now
  ```
  Lưu ý còn `stash@{1..3}` — kiểm từng cái trước khi drop.
- **`git filter-repo` là sai công cụ ở đây.** `git log --all -- .env '.env.*backup*'` trả rỗng: `.env` **chưa bao giờ** commit lên branch nào. Object này chỉ tồn tại qua parent thứ 3 của stash. filter-repo dùng để viết lại history đã push — không áp dụng.
- Việc **rotate toàn bộ secret** vẫn là bắt buộc và độc lập với mọi thao tác git ở trên, vì file đã tồn tại trên disk và đã xuất hiện trong backup/chat.

### 4.2. `git clean -fdX` (Qwen D-L3, §7) sẽ xoá 985MB, không phải 106MB

| Đo thật | |
|---|---|
| Tổng entry ignored | **76** |
| Tổng dung lượng | **985M** |
| `vibegraph-web/node_modules` | 321M |
| `.gitnexus` | **223M** ← index GitNexus, phải `npx gitnexus analyze` lại |
| `target` | 167M |
| `.vibegraph` | 125M ← uploads/workspaces runtime |
| Nhóm log/dump/json ở root | **106M / 28 file** ← *phần thực sự là rác* |

Con số "105MB rác" của Qwen **đúng** (106M). Nhưng lệnh nó đề xuất xoá gấp 9 lần thứ nó mô tả. Lệnh an toàn:

```bash
rm -f backend_run.log backend.out.log backend-run.log backend-dev.out.log graph_check.json
```

Đây là khuyến nghị duy nhất trong cả 4 báo cáo có thể gây thiệt hại thật khi chạy nguyên văn.

---

## 5. Backlog hợp nhất, đã khử trùng lặp, xếp theo bằng chứng

Chỉ gồm mục **đã kiểm chứng bằng lệnh**. Mục dựa trên đồng thuận nhưng chưa tự kiểm được tách xuống §6.

| # | Việc | Mã nguồn gốc | Cơ sở |
|---|---|---|---|
| 1 | **Rotate toàn bộ secret** + xoá object git theo §4.1 + xoá `.env.codex-backup-*` ở working tree | Qwen S1/S2 | Secret thật trong object DB, đi theo `clone --mirror`/bundle |
| 2 | `readRange`: chốt `Files.size()` **trước** `readAllLines`; redact private key theo **khối** BEGIN→END | CP C2/H1, Qwen H14/H15 | Reachable qua MCP source tool; OOM + lộ khóa |
| 3 | `VIBEGRAPH_TRUST_PROXY=false`, **hoặc** sửa `ClientAddressResolver` lấy hop phải-nhất-ngoài-trusted | Qwen S-M2, CP H2 | `.env:104` đang bật thật → bypass rate-limit + IP-block |
| 4 | Đưa `rateLimitFilter` lên trước `jwtAuthFilter`/`apiKeyAuthFilter` | Qwen H13, CP C1 | **Chạy `gitnexus_impact` trước** — đổi thứ tự chain là blast radius rộng |
| 5 | `/actuator/**` → `hasRole('ADMIN')` trừ `health` | Qwen S-M3 | USER thường đọc được metrics/prometheus |
| 6 | Dockerfile non-root + `MaxRAMPercentage`; bỏ mount `./.env:/app/.env`; thêm `vibegraph-web/.dockerignore`; bind DB port về `127.0.0.1`; `AUTH_COOKIE_SECURE` default true; bỏ APOC unrestricted | đồng thuận 3/3 | Cấu hình, rủi ro sửa thấp |
| 7 | `npm audit fix` có review — ưu tiên `websocket-driver`, `undici`, `axios` | Qwen H16 | Đã chạy `npm audit`: 1 critical / 6 high / 1 moderate |
| 8 | Đặt cap dương cho `VIBEGRAPH_GRAPH_NODE_LIMIT` (compose `environment:`) và `VITE_GRAPH_SAFE_NODE_LIMIT` | Qwen B-M10, v2 M1/L2 | `.env:116` = 0, biến backend không đặt |
| 9 | Cache `IpBlockService.findActive` (TTL ngắn) | Qwen B-M9, CP M2, v2 F8 | 1 round-trip DB/request toàn API |
| 10 | Batch 2 query trong `AdminService.toAdminUserResponse` | Qwen H9 | N+1 xác nhận |
| 11 | Xoá 9 file dead code frontend (**1.319 dòng**) + test kèm theo | Qwen F-M1 | 0 import; ref còn lại chỉ là comment |
| 12 | Bọc try/catch 5 điểm còn lại trong `UsersTableView` | Qwen H11 | 8 gọi / 3 try |
| 13 | Dọn 106M log ở root theo §4.2 (**không** `git clean -fdX`) | Qwen D-L3 | Đã đo |
| 14 | Gộp `task/` + `task-final/` | Qwen D-M5 | 8 file trùng mỗi bên |
| 15 | Full UUID cho project ID (bỏ `substring(0,8)`) — 3 chỗ: dòng 62, 91, 101 | Qwen H7 | Đúng cơ chế; xác suất trùng cần ~77k project nên **không phải High** |

---

## 6. Chưa kiểm chứng — đừng thực thi dựa trên tài liệu này

Các mục dưới đây được 2–3 báo cáo nêu nhưng **tôi không tự mở file kiểm tra**. Đồng thuận giữa các báo cáo **không phải bằng chứng** — xem §2, cả 3 cùng sai về `1283`.

- Neo4j `upsertNodes`/`upsertEdges` non-atomic, async FAILED để lại graph dở *(v2 H1, Qwen B-M11 — Qwen tự ghi chưa kiểm sâu)*
- Parse CPG tuần tự là bottleneck *(v2 H3)*
- `getFullGraph` nhân bản node theo cạnh *(v2 M1)*
- `POST /{id}/analyze` đồng bộ *(v2 H2, Qwen H8)*
- Registry project in-memory / hard-code `ANALYZED` 100% *(v2 C1, Qwen H6)*
- TOCTOU khi cộng dồn `used_bytes` *(CP M3)*
- `searchNodes` là code chết *(v2 L7)* — tôi **khen** phương pháp reachability của §9 dựa trên đọc lập luận, không tự truy caller
- Không có chiến lược backup/restore *(v2 H4)*
- `FileChangeBroadcaster` gọi `getFullGraph` 2 lần mỗi lần đổi file *(Qwen B-M5)*
- Polling import GitHub không hủy được *(Qwen H10)*

---

## 7. Sai sót của chính tài liệu này (đã tự đính chính)

Ghi lại để đối xử với tài liệu này bằng cùng tiêu chuẩn nó áp cho 4 báo cáo kia.

1. **Phát biểu "gần 40% audit-report-v2 là nội dung meta" là phóng đại.** Đo thật: §3+§9+§10 = 81/431 dòng = **18,8%**; theo ký tự = **25,7%**. Con số đúng là ~26%.
2. **Từng khuyến nghị `git clean -fdX` theo Qwen mà chưa kiểm nó xoá gì.** Đúng cái lỗi đang phê. Đã sửa ở §4.2.
3. **Từng phê D-L3 "105MB" là chưa kiểm, trong khi tôi mới chỉ đo 3 file lớn nhất (87M).** Đo đủ nhóm: 106M/28 file — **Qwen đúng**, tôi đo thiếu rồi kết luận sớm.
4. **Lần đầu kiểm S2 tôi dùng `git stash show --name-only stash@{0}`, không thấy `.env`, và gần như kết luận Qwen bịa.** Sai: `git stash -u` tạo commit **3 parent**, file untracked nằm ở parent thứ 3 mà `stash show` không hiển thị. Đây **cùng loại lỗi** với `git ls-files` của ClaudePostman và Codex/v2 — dùng lệnh không nhìn được nơi cần nhìn rồi tuyên bố sạch.

---

## 8. Bài học cho vòng audit sau

Ba báo cáo cùng dùng một prompt, và mỗi cái đều có lỗ chết người riêng: bản tự tin nhất về **phương pháp** (Codex/v2) bỏ lọt vấn đề nghiêm trọng nhất; bản rộng nhất về **nội dung** (Qwen) xếp hạng lệch và thừa kế số liệu cũ; bản gọn nhất (ClaudePostman) bịa số dòng.

Điểm chung: **không ai chạy lệnh.** `git rev-list --parents`, `wc -l`, `npm audit`, `grep -L` — mỗi cái một dòng, và chính chúng phân định đúng/sai ở **mọi** tranh chấp trong §1.

Ba dòng nên thêm vào prompt vòng sau:

> 1. Với mỗi finding, ghi kèm **lệnh** đã dùng để xác minh. Finding không có lệnh xác minh phải đánh dấu `[chưa kiểm chứng]`.
> 2. Khi cập nhật báo cáo cũ, mọi **số dòng / số file / dung lượng / tên file** trong finding cũ phải **đo lại bằng lệnh** trước khi giữ; không đo lại được thì đánh dấu `[số liệu chưa xác minh lại]`.
> 3. Trước khi tuyên bố một hạng mục là "sạch", nêu rõ **lệnh nào** chứng minh điều đó và **lệnh đó không nhìn thấy được cái gì**.

Điều 3 là điều đắt nhất: cả `git ls-files` (ClaudePostman, Codex/v2) và `git stash show` (tài liệu này, lần đầu) đều trả kết quả "sạch" cho một repo đang chứa secret trong object database.

---

*Mọi lệnh trong tài liệu này có trong [`verify-claims.sh`](./verify-claims.sh) và có thể chạy lại độc lập. Không có giá trị secret nào được in ra — chỉ tên biến.*
