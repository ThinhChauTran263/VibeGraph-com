# VibeGraph — Báo cáo Audit (Vòng 1)

> **Nguyên tắc:** bằng chứng trước, kết luận sau. Mỗi finding trích dẫn file + đoạn code thật. Giải pháp luôn nêu *hơn hiện trạng ở điểm nào* và *cách kiểm chứng*. **Chưa sửa code.**

**Phạm vi vòng 1:** hình dạng graph khi zoom, hình dạng graph khi generate (layout), tốc độ import (3 phương thức), và audit rộng (search, an toàn dữ liệu Neo4j, bảo mật đường dẫn, hiệu năng truy vấn, config chết).

**Nguồn tài liệu ngoài:** ForceAtlas2 — Jacomy et al., PLOS ONE 2014; docs `graphology-layout-forceatlas2`.

---

## Bảng ưu tiên tổng hợp

| # | Lỗi / Rủi ro | Mức độ | Vùng | Bằng chứng chính |
|---|-------------|--------|------|------------------|
| 1 | Search crash với ký tự đặc biệt (Lucene) | 🔴 Cao | Backend/UX | `Neo4jGraphRepository.searchNodes` |
| 2 | Upsert Neo4j không nguyên tử → graph dở dang khi FAILED | 🔴 Cao | Dữ liệu | `Neo4jGraphRepository` + `analyzeInBackground` |
| 3 | Layout dừng theo wall-clock + node mới `Math.random` → hình không tái lập | 🟠 TB-cao | Frontend | `useSigma.startLayout`, `GraphCanvas.vue` |
| 4 | Bỏ node Package khi render → mất lực gom cụm | 🟠 TB-cao | Frontend | `graphAdapter.apiToGraphology` |
| 5 | Overlap "hồi sinh" khi zoom (node screen-sized) | 🟠 TB | Frontend | `useSigma` + `settleScreenOverlaps` |
| 6 | Import/Browse không giới hạn khi bật cờ → đọc filesystem server | 🟠 TB | Bảo mật | `LocalProjectPathValidator`, `LocalImportServiceImpl.browse` |
| 7 | `getFullGraph` trả toàn bộ trong 1 query, nhân bản node theo cạnh | 🟠 TB | Hiệu năng | `Neo4jGraphRepository.getFullGraph` |
| 8 | Parse tuần tự + walk cây thừa | 🟡 TB | Hiệu năng import | `ParserServiceImpl`, `measureExtractedSize` |
| 9 | Đo dung lượng local chặn request thread | 🟡 TB | UX import | `LocalImportServiceImpl.importLocal` |
| 10 | Config chết / doc không khớp code | 🟡 Thấp | Bảo trì | `runtimeConfig.ts` |

---

# PHẦN A — Hình dạng graph khi ZOOM

Stack: Sigma.js (WebGL) + Graphology + ForceAtlas2 worker. Nhiều phần đã tối ưu tốt (label scale live, cull edge-label theo viewport, budget vẽ/frame).

### A1. Node kích thước theo màn hình → overlap "hồi sinh" khi zoom out 🟠

**Bằng chứng** — [useSigma.ts](../vibegraph-web/src/composables/useSigma.ts), cấu hình Sigma:
```
itemSizesReference: 'screen',
minEdgeThickness: SIGMA_MIN_EDGE_THICKNESS,
```
Và pass giải chồng lấn quy đổi px→graph-units **theo viewport tại thời điểm settle** (`settleScreenOverlaps`):
```
const unitsPerPixel = Math.max(width / viewportWidth, height / viewportHeight)
const gap = LAYOUT_SCREEN_OVERLAP_GAP_PX * unitsPerPixel
```
**Vấn đề:** node giữ kích thước pixel cố định bất kể zoom, còn khoảng cách graph co giãn theo zoom. De-overlap chỉ đúng tại mức zoom lúc settle → zoom out thì node lại chồng, zoom in thì rời rạc. Đây chính là cảm giác "hình dạng đổi theo zoom".

**Giải pháp / hơn hiện tại:**
- Phương án 1: `itemSizesReference: 'positions'` → node scale theo graph, hình dạng tương đối **ổn định** khi zoom (đánh đổi: node nhỏ đi khi zoom out).
- Phương án 2: giữ 'screen' nhưng tính de-overlap ở **mức zoom fit chuẩn**, không phải viewport hiện tại.
- **Kiểm chứng:** đo `overlap ratio` ở 3 mức zoom (0.5×, 1×, 2×); hiện tại chỉ tối ưu ở 1 mức, sau khi sửa phải ổn định qua các mức.

### A2. `ZOOM_SIZE_POWER = 0.75` hardcode 🟡

**Bằng chứng** — [useSigma.ts](../vibegraph-web/src/composables/useSigma.ts):
```
const ZOOM_SIZE_POWER = 0.75
const zoomToSizeRatio = (ratio: number): number =>
  Math.max(0.001, Math.pow(ratio, ZOOM_SIZE_POWER))
```
Đây là yếu tố quyết định node "phình" theo zoom thế nào nhưng **không** là env-knob như triết lý của `runtimeConfig`.

**Giải pháp / hơn hiện tại:** đưa ra `VITE_SIGMA_ZOOM_SIZE_POWER`. Hiện tại muốn tune phải sửa code + rebuild; sau khi sửa chỉ cần đổi `.env`.

### Điểm TỐT về zoom (giữ nguyên)
- Label density chỉ đổi khi **vượt ngưỡng** (`if (nextDensity === labelDensity.value) return`) — không swap reducer mỗi frame.
- `measureText` cache tuyến tính theo font-size (`edgeLabelWidthPerPx`) — tránh "measureText storm".
- Edge-label cull theo viewport + budget `SIGMA_MAX_EDGE_LABELS_PER_FRAME = 48`.

---

# PHẦN B — Hình dạng graph khi GENERATE (layout)

### B1. Hình dạng KHÔNG tất định, mâu thuẫn mục tiêu code tự nêu 🟠

**Bằng chứng — mục tiêu** ([graphAdapter.ts](../vibegraph-web/src/lib/graphAdapter.ts)):
```
// ... so the layout is REPRODUCIBLE: the same project always converges to the
// same picture instead of a different random hairball on every load
function seededPosition(id: string): { x: number; y: number } { ... }
```
**Bằng chứng — thực tế** ([useSigma.ts](../vibegraph-web/src/composables/useSigma.ts) `startLayout`):
```
fa2.start()
layout.value = fa2
layoutStopTimer.value = setTimeout(() => {
  if (layout.value === fa2) stopLayout(true)
}, LAYOUT_AUTO_STOP_MS)   // = 8000ms
```
Import worker: `import FA2Layout from 'graphology-layout-forceatlas2/worker'`.

**Bằng chứng — config chết:** `FA2_ITERATIONS = 700`, `FA2_ITERATIONS_LARGE = 1000` chỉ xuất hiện tại định nghĩa trong `runtimeConfig.ts`, **không dùng ở đâu** (grep toàn frontend).

**Tài liệu:**
- Docs graphology: API **worker** dừng bằng `stop()`, **không** nhận `iterations`; chỉ API đồng bộ mới có `iterations`.
- Bài báo FA2: hội tụ có thể chậm (>1000 bước), một số layout **dao động** → hình tại một mốc thời gian ≠ hình đã settle.

**Kết luận:** dừng theo 8 giây ⇒ số vòng lặp phụ thuộc CPU/tải máy ⇒ **cùng project ra hình khác nhau giữa các máy**. Seed tất định chỉ cố định điểm xuất phát, không cố định điểm dừng.

**Giải pháp / hơn hiện tại:** dừng theo **số vòng lặp cố định** (kích hoạt `FA2_ITERATIONS`), hoặc đếm tick worker rồi `stop()`. Hiện tại: không tái lập; sau: snapshot tọa độ regression được. **Kiểm chứng:** chạy cùng project 5 lần / 2 máy → sai lệch tọa độ < epsilon.

### B2. Node mới (expand/realtime) dùng `Math.random()` 🟠

**Bằng chứng** — [GraphCanvas.vue](../vibegraph-web/src/components/graph/GraphCanvas.vue):
```
return { x: ox + (Math.random() * 40 - 20), y: oy + (Math.random() * 40 - 20) }
return { x: Math.random() * 200 - 100, y: Math.random() * 200 - 100 }
```
**Vấn đề:** đường dẫn expand/realtime ngẫu nhiên hoá vị trí → mâu thuẫn với seed tất định của `graphAdapter`. Cùng thao tác expand ra hình khác nhau.

**Giải pháp / hơn hiện tại:** seed tất định theo id hoặc đặt cạnh centroid neighbor. **Kiểm chứng:** expand cùng node 3 lần → vị trí trùng.

### B3. Bỏ node Package khi render → mất xương sống gom cụm 🟠

**Bằng chứng — frontend** ([graphAdapter.ts](../vibegraph-web/src/lib/graphAdapter.ts)):
```
for (const node of data.nodes) {
  if (node.type === 'Package') continue
  ...
}
...
for (const edge of data.edges) {
  if (!graph.hasNode(edge.source) || !graph.hasNode(edge.target)) continue
  ...
}
```
**Bằng chứng — backend cố công dựng phân cấp** ([AnalyzeServiceImpl.java](../src/main/java/com/vibegraph/graph/service/impl/AnalyzeServiceImpl.java)):
```
// Project -[:CONTAINS]-> Package ... so the hierarchy is Project -> Package -> File.
allEdges.addAll(projectContainsPackageEdges(projectId, allNodes));
```
**Kết luận:** mọi cạnh `Project→Package` và `Package→File` bị **âm thầm loại** khỏi đồ thị render (do check `hasNode`). Đây là lực hút cấu trúc mạnh nhất để FA2 gom File cùng package. Mất nó ⇒ dễ thành hairball / rời rạc.

**Giải pháp / hơn hiện tại:** giữ containment làm lực layout (có thể ẩn hiển thị Package). **Kiểm chứng:** đo **modularity theo package** trước/sau.

### B4. Tuning chỉ dựa trên MỘT comment, không có harness đo 🟡

**Bằng chứng** — [runtimeConfig.ts](../vibegraph-web/src/lib/runtimeConfig.ts):
```
// LinLog is intentionally OFF — it lengthens edges
// and pulls the body toward the center (measured edgeToRadius 0.48 vs 0.29).
```
Grep toàn repo: **không có** script/test/tài liệu tạo ra con số "0.48 vs 0.29".

**Đối chiếu tài liệu (giá trị hiện tại):**
- `FA2_SCALING_RATIO = 1500`: bài báo minh hoạ dùng bậc ~2.0; scaling càng cao càng giãn rộng → 1500 là **cực cao**, chi phối độ trải.
- `FA2_GRAVITY = 0.001`: gravity giữ component không trôi; **quá thấp** → đảo cô lập trôi xa, buộc zoom-to-fit thu nhỏ cả graph.
- `outboundAttractionDistribution = true` ("Dissuade Hubs"): đẩy hub ra biên.
- `adjustSizes = false`: FA2 bỏ qua bán kính node → xử lý bù bằng pass hậu-layout.

**Giải pháp / hơn hiện tại:** xây harness đo (mục cuối) rồi mới tinh chỉnh; mọi con số biện minh phải tái lập được.

### B5. `FA2_OUTLIER_CLAMP_PERCENTILE` mô tả kỹ nhưng CHƯA hiện thực 🟡

**Bằng chứng** — [runtimeConfig.ts](../vibegraph-web/src/lib/runtimeConfig.ts) mô tả chi tiết cơ chế "kéo outlier vào vành bao":
```
export const FA2_OUTLIER_CLAMP_PERCENTILE = envFloat('VITE_FA2_OUTLIER_CLAMP_PERCENTILE', 0.9, {min:0, max:1})
```
Grep: hằng số này **không được dùng** ở đâu. Trong khi framing thực tế do chuỗi pass thủ công `normalizeLayout → spreadLayoutClusters → centerLayout → Noverlap → settleScreenOverlaps` đảm nhận, với nhiều hằng số phép thuật (`0.32`, `0.14`, `Math.log(n)*24`…).

**Giải pháp / hơn hiện tại:** gỡ hoặc hiện thực; đưa hằng số magic ra config. Hiện tại: knob đánh lừa người tune (chỉnh env vô tác dụng).

### Điểm TỐT về generate (giữ nguyên)
- Seed tất định (FNV-1a) cho lần load đầu — đúng khuyến nghị "phải seed vị trí, tránh (0,0)".
- De-overlap chạy **sau** khi FA2 settle (`stopLayout(true) → runPostLayoutPass`) — đúng khuyến nghị bài báo.
- Cache vị trí qua rebuild (`positionCache`) giữ ổn định khi filter/expand.

---

# PHẦN C — Tốc độ Import (3 phương thức)

3 phương thức đều hội tụ về `AnalyzeService.analyzeProject → ParserService.parseProject → Neo4jGraphRepository`.
- Local: [LocalImportServiceImpl](../src/main/java/com/vibegraph/graph/service/impl/LocalImportServiceImpl.java)
- Archive/ZIP: [ArchiveImportServiceImpl](../src/main/java/com/vibegraph/graph/service/impl/ArchiveImportServiceImpl.java)
- GitHub tarball: [TarballImportServiceImpl](../src/main/java/com/vibegraph/graph/service/impl/TarballImportServiceImpl.java)

### C1. Parse TUẦN TỰ — bottleneck lớn nhất (chung cả 3) 🟡→🟠

**Bằng chứng** — [ParserServiceImpl.java](../src/main/java/com/vibegraph/parser/service/impl/ParserServiceImpl.java) `parseProject`:
```
JavaParser parser = createProjectParser(projectRoot, javaFiles);
int parsed = 0;
for (Path javaFile : javaFiles) {
    ParseResult result = parseFileInternal(javaFile, parser, projectSymbols);
    results.add(result);
    ...
}
```
Một `JavaParser` + symbol solver dùng chung, vòng `for` tuần tự. Phase parse chiếm khoảng `PARSE_START_PCT=5 → PARSE_END_PCT=70` (~65% tiến trình, xem `AnalyzeServiceImpl`).

**Giải pháp / hơn hiện tại:** parse song song trên bounded pool (mỗi thread một `JavaParser`, dùng chung type-solver read-only). **Điều kiện tiên quyết:** xác minh thread-safety của `ProjectSymbolRegistry` (đang dùng scope `open(...)` — nghi ThreadLocal). **Kiểm chứng:** đo thời gian phase parse trên repo mẫu trước/sau.

### C2. Walk cây THỪA sau extract (Archive + GitHub) 🟡

**Bằng chứng** — cả `ArchiveImportServiceImpl.prepare` và `TarballImportServiceImpl.prepareWorkspace` gọi:
```
long totalSize = measureExtractedSize(source);   // Files.walk toàn bộ cây LẦN NỮA
```
Trong khi extractor đã đi qua từng file khi ghi ra đĩa.

**Giải pháp / hơn hiện tại:** cho `ArchiveExtractor` trả kèm tổng bytes trong `ArchiveExtractionResult` → bỏ hẳn lượt walk thứ hai (logic quota giữ nguyên). Hiện tại: 2 lượt I/O toàn cây; sau: 1 lượt.

### C3. Đo dung lượng local CHẶN request thread 🟡

**Bằng chứng** — [LocalImportServiceImpl.importLocal](../src/main/java/com/vibegraph/graph/service/impl/LocalImportServiceImpl.java):
```
Path validatedRoot = pathValidator.validateImportRoot(request.path());
long totalSize = directorySizeMeasurer.measureBytes(validatedRoot);  // đồng bộ, trước khi trả ANALYZING
```
2 phương thức kia đẩy phần nặng sang background; local thì đo dung lượng đồng bộ → thư mục lớn làm chậm phản hồi.

**Giải pháp / hơn hiện tại:** đưa đo dung lượng vào background (vẫn assert quota trước khi persist). Đánh đổi: quota check trễ hơn.

### C4. Persist Neo4j: transaction lớn, tuần tự 🟠 (xem thêm D2)

**Bằng chứng** — `upsertNodes`/`upsertEdges` gom **toàn bộ** vào một `UNWIND $batch` mỗi label/rel-type, một `session` tuần tự, không chunk.

**Giải pháp / hơn hiện tại:** chunk batch (5k–10k/UNWIND) + `executeWrite`; verify có index `(:Symbol {projectId, fullName})`. Hiện tại: 1 transaction khổng lồ, áp lực heap; sau: ổn định bộ nhớ, tránh timeout.

### Ưu tiên tối ưu import
| Ưu tiên | Hạng mục | Ảnh hưởng | Phương thức |
|---|---|---|---|
| 1 | Parse song song (C1) | Rất lớn | Cả 3 |
| 2 | Bỏ walk thừa (C2) | Trung bình | Archive, GitHub |
| 3 | Chunk batch + index (C4) | TB-lớn | Cả 3 |
| 4 | Đo dung lượng local → background (C3) | Nhỏ-TB (cảm nhận) | Local |

---

# PHẦN D — Audit rộng (bảo mật / dữ liệu / hiệu năng)

### D1. 🔴 Search crash với ký tự đặc biệt (Lucene)

**Bằng chứng** — [Neo4jGraphRepository.searchNodes](../src/main/java/com/vibegraph/graph/repository/impl/neo4j/Neo4jGraphRepository.java):
```
"CALL db.index.fulltext.queryNodes('node_search', $query) YIELD node, score " +
"WHERE node.projectId = $projectId " +
"RETURN node ORDER BY score DESC LIMIT 50"
```
[GraphServiceImpl.searchNodes](../src/main/java/com/vibegraph/graph/service/impl/GraphServiceImpl.java) truyền query **nguyên văn**: `return graphRepository.searchNodes(projectId, query);`. Grep toàn backend: **không có escaping Lucene** cho luồng này.

**Vấn đề:** `$query` là Lucene query string. Ký tự `+ - && || ! ( ) { } [ ] ^ " ~ * ? : \ /` và từ khoá `AND/OR/NOT` bị parse. Gõ `User(`, `foo:`, `a && b`, `*` lẻ → `ParseException` → lỗi 500 (không phải "0 kết quả"). Search bar gọi mỗi lần gõ ⇒ dễ dính.

> Lưu ý: đây **không** phải Cypher injection (query đã parameter hoá `$query`); rủi ro là **crash/ổn định**, không phải bảo mật.

**Giải pháp / hơn hiện tại:** escape ký tự đặc biệt Lucene (hoặc bọc term + `*` prefix có kiểm soát) trước khi truyền. Hiện tại: input hợp lệ về mặt người dùng làm sập request; sau: luôn trả kết quả hoặc rỗng. **Kiểm chứng:** test tham số hoá `User(`, `*`, `a:b`, `""` → tất cả 200.

### D2. 🔴 Upsert Neo4j không nguyên tử → graph dở dang khi FAILED

**Bằng chứng — autocommit từng batch** ([Neo4jGraphRepository](../src/main/java/com/vibegraph/graph/repository/impl/neo4j/Neo4jGraphRepository.java)):
```
try (Session session = neo4jDriver.session()) {
    for (Map.Entry<...> group : byLabel.entrySet()) {
        session.run(cypher, Map.of("projectId", projectId, "batch", group.getValue()));
    }
}
```
Không dùng `session.executeWrite(...)` bao trọn. Thứ tự persist ([AnalyzeServiceImpl](../src/main/java/com/vibegraph/graph/service/impl/AnalyzeServiceImpl.java)): `upsertProject (80%) → upsertNodes → upsertEdges (94%)`.

**Bằng chứng — FAILED không dọn Neo4j** (`analyzeInBackground`, cả 3 service): khi lỗi chỉ `markFailed` + xoá **workspace**, không `deleteProject` trên Neo4j.

**Vấn đề:** lỗi ở `upsertEdges` sau khi `upsertNodes` xong ⇒ project `FAILED` **nhưng vẫn còn node/một phần edge** trong Neo4j → dữ liệu rác/nhất quán kém, retry dính node cũ.

**Giải pháp / hơn hiện tại:** gói mỗi phase persist trong `executeWrite`; khi FAILED gọi `deleteProject(projectId)` (đã có `DETACH DELETE`). Hiện tại: FAILED không đảm bảo dữ liệu sạch; sau: FAILED ⇒ không dấu vết. **Kiểm chứng:** inject lỗi ở `upsertEdges`, assert `getFullGraph` rỗng.

### D3. 🟠 Import/Browse không giới hạn khi bật cờ

**Bằng chứng** — [LocalProjectPathValidator](../src/main/java/com/vibegraph/graph/service/LocalProjectPathValidator.java):
```
if (allowedRoot == null && !properties.isAllowUnconfinedImport()) {
    throw new IllegalStateException("Local import is disabled until an allowed root is configured");
}
```
Khi `allowedRoot == null` **và** `allowUnconfinedImport == true` ⇒ import **bất kỳ thư mục nào** trên host. `browse()` unconfined liệt kê cả ổ đĩa (`listRoots`).

**Vấn đề:** với deploy server (không phải desktop) đây là đường đọc filesystem tùy ý. Có cờ bảo vệ nhưng dễ bật nhầm khi lên server.

**Giải pháp / hơn hiện tại:** tách profile desktop vs server; profile `prod` **luôn** yêu cầu `allowedRoot`, tắt unconfined. **Kiểm chứng:** test profile prod từ chối import ngoài root.

### D4. 🟠 `getFullGraph` — một query, nhân bản node theo cạnh

**Bằng chứng** — [Neo4jGraphRepository.getFullGraph](../src/main/java/com/vibegraph/graph/repository/impl/neo4j/Neo4jGraphRepository.java):
```
"MATCH (n:Symbol {projectId: $projectId}) " +
"OPTIONAL MATCH (n)-[r]->(m:Symbol {projectId: $projectId}) " +
"RETURN n, r, m"
```
Trả **O(số cạnh)** dòng, node bậc cao lặp nhiều; gom hết vào RAM rồi trả một lần. `GRAPH_SAFE_NODE_LIMIT` mặc định `0` (tắt cap).

**Giải pháp / hơn hiện tại:** tách 2 truy vấn (nodes / edges) hoặc phân trang/streaming; bật cap mặc định. Hiện tại: payload/bộ nhớ phình với project nhiều hub. **Kiểm chứng:** đo kích thước payload trước/sau.

---

# ĐIỂM TỐT cần ghi nhận (đừng sửa nhầm)
- ✅ Cypher label/rel-type **có whitelist** ([GraphSchema](../src/main/java/com/vibegraph/graph/repository/impl/neo4j/GraphSchema.java) → `GraphVocabulary`) + property-key regex `[A-Za-z_][A-Za-z0-9_]*` → chặn injection.
- ✅ Path traversal & symlink xử lý bằng `toRealPath` + `startsWith` (validator, LocalPatch, MCP SourceFile, ArchiveExtractor `UNSAFE_ENTRY`).
- ✅ Thread pool phân tích **bounded** + `AbortPolicy` ([AsyncConfig](../src/main/java/com/vibegraph/common/config/AsyncConfig.java)) → không tràn Tomcat thread; import có `ConcurrentImportGuard` per-user.
- ✅ De-overlap chạy **sau** khi FA2 settle — đúng khuyến nghị ForceAtlas2.
- ✅ Zoom: label density đổi theo ngưỡng (không mỗi frame), cache measureText, cull theo viewport.

---

# HARNESS ĐO LƯỜNG (để mọi tinh chỉnh về sau có bằng chứng tái lập)

Hiện "bằng chứng" tuning layout chỉ là 1 comment (`edgeToRadius 0.48 vs 0.29`) không tái lập. Đề xuất script đo trên bộ project mẫu (`src/test/resources/sample-project`, `projects/use-case-lab`):

| Metric | Đo cái gì | Phục vụ finding |
|--------|-----------|-----------------|
| `edgeToRadius` | độ dài cạnh TB / bán kính body | B4 (tuning FA2) |
| `overlap ratio` @ {0.5×,1×,2×} | % node chồng theo mức zoom | A1 |
| `modularity` theo package | mức gom cụm | B3 |
| `reproducibility drift` | sai lệch tọa độ giữa 2 lần / 2 máy | B1, B2 |
| `import phase timing` | scan/parse/infer/upsert | C1–C4 |

Quy trình: đo **baseline** trước → thay đổi → so sánh. Đây là cách "cái gì cũng có bằng chứng".

---

# GHI CHÚ QUY TRÌNH
Theo `AGENTS.md`: trước khi hiện thực bất kỳ đề xuất nào đụng symbol nhiều caller (`parseProject`, `apiToGraphology`, `upsertNodes/Edges`, `startLayout`, `searchNodes`, `getFullGraph`) → chạy `gitnexus_impact` để đánh giá blast radius; cảnh báo nếu HIGH/CRITICAL.

**Trạng thái:** Vòng 1, tập trung vùng rủi ro cao. Chưa bao phủ toàn bộ (chưa audit sâu: auth/JWT, OAuth, credit/billing, MCP, patch applier, WebSocket/STOMP, file watcher). Có thể mở rộng theo yêu cầu.
