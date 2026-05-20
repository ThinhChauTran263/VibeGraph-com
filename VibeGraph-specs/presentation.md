# VibeGraph

## Hệ thống phân tích kiến trúc mã nguồn Java thời gian thực

---

## 1. Vấn đề cần giải quyết

### Thực trạng phát triển phần mềm hiện nay

Năm 2026, hơn 40% mã nguồn trên toàn cầu được tạo ra bởi AI (vibe coding). Tuy nhiên, AI thường sinh code **sai kiến trúc** vì thiếu hiểu biết về cấu trúc tổng thể của dự án.

| Vấn đề | Hậu quả | Tần suất |
|--------|---------|----------|
| AI không biết project đang dùng pattern gì | Code mới không nhất quán với code cũ | Mỗi lần AI generate code |
| Developer mới mất thời gian hiểu codebase | Onboarding chậm, dễ phá vỡ architecture | Mỗi lần có người mới |
| Không có sơ đồ kiến trúc cập nhật | Tài liệu lỗi thời, không ai tin tưởng | Liên tục |
| Thay đổi code không biết ảnh hưởng gì | Bug lan truyền, regression | Hàng tuần |

### Chi phí thực tế

- Developer trung bình dành **60-70% thời gian đọc code**, chỉ 30-40% viết code mới
- Bug do sai architecture tốn **10-50x** chi phí sửa so với bug logic đơn giản
- AI sinh code sai pattern → phải refactor → lãng phí thời gian và tiền

---

## 2. Giải pháp: VibeGraph

VibeGraph là nền tảng phân tích mã nguồn Java **thời gian thực**, tự động:

1. **Đọc** toàn bộ source code Java
2. **Hiểu** cấu trúc: class nào gọi class nào, kế thừa gì, phụ thuộc gì
3. **Vẽ** sơ đồ kiến trúc tự động (Force Graph, Use Case, Class Diagram, Sequence Diagram)
4. **Cập nhật** ngay khi code thay đổi
5. **Cung cấp context** cho AI coding tools để sinh code đúng architecture

### Một câu mô tả

> "VibeGraph biến mã nguồn thành bản đồ sống — developer nhìn thấy kiến trúc, AI hiểu được kiến trúc, cả hai cùng code đúng."

---

## 3. Tính năng chính

### 3.1 Force Graph — Bản đồ mã nguồn tương tác

Toàn bộ dự án Java được hiển thị dưới dạng đồ thị tương tác:

- Mỗi **class, method, interface** là một node (chấm tròn)
- Mỗi **quan hệ** (gọi hàm, kế thừa, phụ thuộc) là một edge (đường nối)
- Màu sắc phân biệt loại: Class (vàng), Interface (xanh lá), Method (xanh dương), File (đỏ)
- **Zoom, kéo thả, click** để khám phá
- **Tìm kiếm** theo tên, lọc theo package hoặc layer
- **Highlight đường đi** khi click vào một method → thấy ngay call chain

**Công nghệ:** Sigma.js (WebGL) — xử lý mượt 10,000+ nodes ở 60fps.

### 3.2 Use Case Diagram — Sơ đồ chức năng tự động

Tự động phát hiện:
- **Actors**: HTTP Client (từ @RestController), System Scheduler (từ @Scheduled), Message Queue (từ @KafkaListener)
- **Use Cases**: Mỗi endpoint API = một use case
- **Relationships**: <<include>> (service dùng chung), <<extend>> (validation, notification)

Không cần vẽ tay — VibeGraph đọc code và sinh sơ đồ.

### 3.3 Class Diagram — Sơ đồ lớp

- Hiển thị classes với fields, methods, visibility (+, -, #)
- Quan hệ kế thừa (extends), triển khai (implements)
- Quan hệ phụ thuộc (@Autowired injection)
- Lọc theo package để xem từng module

### 3.4 Sequence Diagram — Sơ đồ tuần tự

- Chọn một entry point (ví dụ: `POST /api/users`)
- VibeGraph trace toàn bộ call chain: Controller → Service → Repository
- Sinh sơ đồ sequence tự động

### 3.5 Realtime Update — Cập nhật thời gian thực

- Khi developer lưu file → VibeGraph phân tích lại file đó trong **< 3 giây**
- Sơ đồ trên dashboard tự động cập nhật
- Không cần refresh, không cần chạy lại lệnh

### 3.6 AI Context Provider — Cung cấp context cho AI

Đây là tính năng **khác biệt cốt lõi** của VibeGraph:

- **MCP Server**: AI tools (Cursor, Kiro, Claude Code) tự động gọi VibeGraph để lấy context trước khi sinh code
- **Steering Files**: Tự động generate file rules cho AI, bao gồm: patterns, naming conventions, DO/DON'T
- **Pre-code Hook**: Bắt buộc AI đọc architecture context trước khi viết code mới

**Kết quả:** AI sinh code đúng architecture ngay từ lần đầu, giảm 80% thời gian review và refactor.

---

## 4. Kiến trúc hệ thống

```
┌────────────────────┐         ┌─────────────────────────────────┐
│  Developer IDE     │         │        Spring Boot Backend       │
│  (bất kỳ IDE nào) │         │                                   │
│  save file (Ctrl+S)│         │  ┌─────────────┐ ┌────────────┐ │
└────────────────────┘         │  │ Java Parser │ │ MCP Server │ │
         │                     │  │ (đọc code)  │ │ (cho AI)   │ │
         │  file thay đổi     │  └──────┬──────┘ └────────────┘ │
         ▼                     │         │                        │
┌────────────────────┐         │  ┌──────▼──────┐                 │
│  File Watcher      │────────▶│  │   Neo4j     │                 │
│  (tự động detect)  │         │  │ (Graph DB)  │                 │
└────────────────────┘         │  └─────────────┘                 │
                               └─────────────────────────────────┘
┌────────────────────┐                    │
│   AI Coding Tools  │────────────────────┘ MCP
│   (Cursor, Kiro)   │
└────────────────────┘
         │
┌────────▼───────────┐
│   Vue.js Frontend  │◀──── WebSocket (realtime update)
│   (Dashboard)      │
└────────────────────┘
```

| Thành phần | Công nghệ | Vai trò |
|------------|-----------|---------|
| Backend | Spring Boot 3.3, Java 21 | API, parser, MCP server, file watcher |
| Parser | JavaParser | Đọc và phân tích mã nguồn Java |
| Database | Neo4j 5.x | Lưu trữ đồ thị quan hệ code |
| Frontend | Vue 3, Sigma.js, Mermaid.js | Dashboard visualization |
| File Watcher | Java WatchService | Tự động detect file thay đổi (mọi IDE) |
| AI Bridge | Spring AI MCP Starter | Cung cấp context cho AI tools |

---

## 5. Luồng hoạt động

### Luồng 1: Developer mở dự án lần đầu

```
1. Cài IntelliJ Plugin → Click "Analyze Project"
2. VibeGraph quét toàn bộ .java files (< 30 giây cho 500 files)
3. Xây dựng knowledge graph trong Neo4j
4. Dashboard hiển thị Force Graph + UML diagrams
5. Developer hiểu architecture trong 5 phút thay vì 5 ngày
```

### Luồng 2: Developer code hàng ngày (TỰ ĐỘNG 100%)

```
1. Developer sửa UserService.java → Save (Ctrl+S) trong bất kỳ IDE nào
2. File Watcher (backend) tự detect thay đổi → Re-parse file đó (< 3 giây)
3. Graph update realtime trên dashboard (WebSocket push)
4. Diagrams tự động cập nhật
5. Developer không cần click gì, không cần cài plugin
```

### Luồng 3: AI vibe coding

```
1. Developer yêu cầu AI: "Tạo endpoint mới cho payment"
2. AI gọi VibeGraph MCP: "Cho tôi architecture context"
3. VibeGraph trả về: layers, patterns, naming, related classes
4. AI sinh code ĐÚNG pattern: PaymentController → PaymentService → PaymentRepository
5. Code review nhanh hơn, ít lỗi architecture hơn
```

---

## 6. Môi trường sử dụng (Where)

| Nơi sử dụng | Mô tả |
|--------------|--------|
| **Máy cá nhân (Local)** | Developer cài Docker, chạy `docker compose up`, mở browser xem dashboard |
| **Server nội bộ (Team)** | Deploy trên server công ty, cả team truy cập chung 1 dashboard |
| **Trong AI tools (Cursor, Kiro, Claude Code)** | MCP Server cung cấp context — AI tự động gọi khi cần |
| **Mọi IDE** | Không cần plugin — backend tự watch folder, hoạt động với IntelliJ, VS Code, Eclipse, Vim... |

**Platform hỗ trợ:**
- Backend: Chạy trên mọi OS có Docker (Windows, macOS, Linux)
- Frontend: Mọi browser hiện đại (Chrome, Firefox, Safari, Edge)
- IDE: Không giới hạn — tự động detect file thay đổi bất kể IDE nào
- AI Integration: Mọi tool hỗ trợ MCP protocol

---

## 7. Đối tượng sử dụng (Who)

| Đối tượng | Lợi ích | Cách dùng |
|-----------|---------|-----------|
| **Java Developer** | Hiểu codebase nhanh, navigate relationships | Mở dashboard, click explore |
| **Tech Lead** | Review architecture, phát hiện violations | Xem class diagram, check layers |
| **New Member** | Onboarding nhanh | Xem force graph + use case diagram |
| **AI Assistant** | Sinh code đúng architecture | Tự động qua MCP protocol |
| **Project Manager** | Hiểu scope dự án | Xem use case diagram |

---

## 8. Điểm khác biệt so với công cụ hiện có

| Tiêu chí | VibeGraph | SonarQube | IntelliJ Diagrams | GitNexus |
|----------|-----------|-----------|-------------------|----------|
| Realtime update | ✅ < 3 giây | ❌ Batch scan | ❌ Manual generate | ✅ |
| Force Graph interactive | ✅ WebGL 10k+ nodes | ❌ | ❌ | ✅ |
| Auto UML diagrams | ✅ Use Case + Class + Sequence | ❌ | ⚠️ Class only | ✅ Use Case |
| AI context provider (MCP) | ✅ | ❌ | ❌ | ❌ |
| Steering file generation | ✅ Auto-generate rules cho AI | ❌ | ❌ | ❌ |
| Spring Boot aware | ✅ Detect layers, annotations | ⚠️ Generic | ⚠️ Generic | ⚠️ Generic |
| Ngôn ngữ | Java (Phase 1) | Multi | Java | Multi |
| Giá | Free / Self-hosted | Paid (enterprise) | Bundled with IDE | Free |

**Unique selling point:** VibeGraph là công cụ duy nhất kết hợp **visualization realtime** + **AI context provider** — không chỉ cho người nhìn, mà còn cho AI hiểu.

---

## 9. Demo Scenarios

### Demo 1: "5 phút hiểu dự án mới"
- Input: Spring Boot project 200 files
- Action: Click Analyze
- Output: Force graph hiển thị toàn bộ architecture, click vào module → zoom in

### Demo 2: "AI code đúng architecture"
- Input: Yêu cầu AI tạo feature mới
- Without VibeGraph: AI tạo code sai layer, sai naming
- With VibeGraph: AI đọc context → code đúng pattern ngay lần đầu

### Demo 3: "Thay đổi code, sơ đồ tự cập nhật"
- Action: Thêm method mới vào Service
- Result: Force graph thêm node mới + edges trong 3 giây

---

## 10. Kế hoạch triển khai (When)

### Timeline: 6 tuần

| Tuần | Milestone | Deliverable |
|------|-----------|-------------|
| 1-2 | Foundation | Parse Java → Force Graph hiển thị trên browser |
| 3-4 | Core Features | Realtime + Use Case + Class diagrams + WebSocket |
| 5-6 | Ship MVP | MCP Server + Docker deploy + AI integration |

### Team: 5 developers

| Role | Phụ trách |
|------|-----------|
| Backend Lead | Parser engine (JavaParser) |
| Backend Dev | API + Neo4j + WebSocket + MCP |
| Frontend Lead | Sigma.js Force Graph |
| Frontend Dev | Mermaid diagrams + UI |
| Integration Dev | File Watcher + DevOps + Testing |

### Deployment

```bash
# Một lệnh duy nhất để chạy toàn bộ hệ thống
docker compose up -d
```

Bao gồm: Backend + Neo4j + Frontend — sẵn sàng dùng.

---

## 11. Roadmap

### Phase 1 (6 tuần) — MVP
- ✅ Parse Java source code
- ✅ Force Graph visualization
- ✅ Use Case + Class + Sequence diagrams
- ✅ Realtime update (tự động, không cần click)
- ✅ MCP Server cho AI tools
- ✅ Steering file auto-generation
- ✅ Hoạt động với mọi IDE (không cần plugin)

### Phase 2 (tháng 3-4) — Mở rộng
- Multi-language: TypeScript, Python, Kotlin
- IntelliJ Plugin (status bar, nút bấm trong IDE)
- Multi-user với authentication
- Git history analysis (code evolution)
- Cloud deployment (SaaS)

### Phase 3 (tháng 5-6) — AI-powered
- AI gợi ý refactoring dựa trên graph
- Tự động phát hiện architecture violations
- So sánh architecture giữa branches
- Export tài liệu tự động (Confluence, Notion)

---

## 12. Tổng kết

VibeGraph giải quyết 3 vấn đề cốt lõi:

| # | Vấn đề | Giải pháp VibeGraph |
|---|--------|---------------------|
| 1 | Developer không hiểu codebase | Force Graph + UML diagrams tự động |
| 2 | Tài liệu kiến trúc lỗi thời | Realtime update — luôn chính xác |
| 3 | AI sinh code sai architecture | MCP Server + Steering files — AI bắt buộc đọc context |

**Kết quả kỳ vọng:**
- Giảm 70% thời gian onboarding developer mới
- Giảm 80% lỗi architecture khi AI vibe coding
- Tiết kiệm 2-3 giờ/tuần/developer cho việc đọc hiểu code
- Tài liệu kiến trúc luôn chính xác 100% (vì generate từ code thật)

---

## Liên hệ

**Dự án:** VibeGraph  
**Tech Stack:** Spring Boot + Neo4j + Vue.js + Sigma.js  
**License:** [TBD]  
**Repository:** [TBD]
