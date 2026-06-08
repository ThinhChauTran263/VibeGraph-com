# Hướng Dẫn Cập Nhật File VibeGraph_WS3_Sprint-Trello-BBCH-ERD.md

## 📝 SUMMARY

File `SPRINT_TASK_UPDATE_SUMMARY.md` đã được tạo với TẤT CẢ thông tin cập nhật chi tiết về:
- ✅ Team members (5 người với vai trò đúng)
- ✅ Task status (Done/In Progress/TODO)
- ✅ File names cho mỗi task
- ✅ Assignee theo vai trò thực tế
- ✅ Progress thực tế sau khi đọc source code

## 🔄 CÁC THAY ĐỔI CHÍNH CẦN ÁP DỤNG VÀO FILE GỐC

### 1. Header Section
```markdown
> **Cập nhật:** 2026-06-05  
> **Trạng thái:** Sprint 1 hoàn thành 95%, Sprint 2 đang tiến hành 20%  
>
> **Team Members (5 người):**
> - **Thái:** Business Analyst, Product Owner, Tester
> - **Thịnh:** Leader, Quản lý dự án, Vibecode
> - **Khoa:** Fullstack Developer
> - **Danh:** Fullstack Developer  
> - **Vinh:** Backend Developer, Scrum Master
```

### 2. Release Backlog - Thêm PB13 & PB14

Sau dòng RB28 (GitHub Actions CI), thêm:

```markdown
| PB13       | File Watcher            | Lập trình viên         | Theo dõi thay đổi file .java bằng WatchService             | graph tự động cập nhật khi code thay đổi  | RB29     | 3        | Low            | 2      | New   |      |
| PB13       | File Watcher            | Lập trình viên         | Debounce 500ms cho file watcher                            | tránh phân tích liên tục khi save nhiều lần | RB30   | 3        | Low            | 2      | New   |      |
| PB14       | Documentation           | Người dùng demo        | Có README và tài liệu cài đặt                              | hiểu cách chạy và sử dụng hệ thống        | RB31     | 3        | Low            | 2      | New   |      |
| PB14       | Documentation           | Người dùng demo        | Có tài liệu MCP integration                                | tích hợp với AI tools                     | RB32     | 3        | Low            | 2      | New   |      |
| PB14       | Documentation           | Người dùng demo        | Có project mẫu và script demo                              | demo end-to-end cho stakeholder           | RB33     | 3        | Low            | 2      | New   |      |
```

### 3. Sprint Backlog - Cập nhật TOÀN BỘ

#### Sprint 1 Tasks - Mark as DONE

Các task SAU ĐÂY cần đổi State từ "New" → "Done":

**Parser Module:**
- T12, T13, T14, T15, T16, T17, T18, T19 → **Done**
- T20 → **In Progress** (some tests disabled)

**Neo4j:**
- T21, T22, T23, T24, T25 → **Done**
- T26 → **New** (TODO)

**Services:**
- T27, T28, T29, T30 → **Done**
- T31, T32, T33 → **New** (Sprint 2)
- T34 → **In Progress**

**Frontend:**
- T48, T49, T50, T51, T53 → **Done**
- T52, T54 → **In Progress**

**Archive (BONUS):**
- T01, T02, T03, T04, T06 → **Done**
- T05 → **In Progress** (sync done, async 70%)

#### Cập nhật Assign và Note

**File Note Format:** Thêm cột "File Path" vào mỗi task

Example:
```
| T13 | Complete ClassVisitor | Trích xuất class/interface/enum | RB06 | PB03 | 1 | Done | 4 | Khoa | `parser/visitor/ClassVisitor.java` (250 LOC) |
```

#### Assignee Updates theo vai trò:

**Khoa (Fullstack):**
- Parser: T12, T13, T17, T18
- Service: T27, T28
- MCP: T43, T45
- Watcher: T37
- Diagram: T39

**Vinh (BE + Scrum Master):**
- Parser: T14, T19
- Neo4j: T21, T22, T23, T25
- Service: T29, T31
- WebSocket: T35, T36
- GitHub: T09, T10
- DevOps: T63, T64, T65, T67
- MCP: T44

**Danh (Fullstack):**
- Parser: T15, T16
- Frontend: T48, T49, T51, T56, T57, T60
- Service: T30
- Diagram: T40, T41
- MCP: T46, T47

**Thái (BA + PO + Tester):**
- Testing: T20 (partial), T34 (partial), T38, T42, T62, T70, T71
- Documentation: T72
- Integration tests

**Thịnh (Leader + PM + Vibecode):**
- Frontend: T50, T52, T54, T55, T58, T59, T61
- Testing: T06, T20 (partial), T62, T68
- DevOps: T66, T69

### 4. ED Section - Cập nhật Team Size

Thay đổi:
```
Nhóm      | 2   | Thành viên từng làm việc cùng nhau?  | Có | 2 |
          | 3   | Hiểu và tôn trọng lẫn nhau?          | Có | 2 |
```

Thành:
```
Nhóm      | 1   | Nhóm 5 người với vai trò rõ ràng?   | Có | 2 |
          | 2   | Thành viên từng làm việc cùng nhau?  | Có | 2 |
          | 3   | Hiểu và tôn trọng lẫn nhau?          | Có | 2 |
```

### 5. Capacity Calculation

Thay đổi footer từ:
```
số người: 4
Năng lực/sprint (4 người x 8h x 12 ngày): 384
```

Thành:
```
số người: 5 (3 fulltime devs + 2 part-time)
Năng lực/sprint hiệu quả: ~240h
(Khoa, Danh, Vinh: 120h + Thái, Thịnh: 40h testing/PM = 160h raw → ~240h effective với meetings)
```

---

## 🎯 PRIORITY ORDER ĐỂ CẬP NHẬT

1. **CRITICAL:** Cập nhật header với team 5 người
2. **HIGH:** Mark Sprint 1 tasks as Done
3. **HIGH:** Thêm File Path vào Note column
4. **MEDIUM:** Update Assignee theo vai trò
5. **MEDIUM:** Thêm RB29-RB33 (PB13, PB14)
6. **LOW:** Update capacity calculation

---

## 📂 FILES REFERENCE

Để dễ dàng cập nhật, tham khảo:

1. **SPRINT_TASK_UPDATE_SUMMARY.md** - Chi tiết tất cả tasks với file names
2. **FILES_READ_SUMMARY.md** - Danh sách 45 files đã đọc với status
3. **IMPLEMENTATION_STATUS_REVISED.md** - Phân tích chi tiết implementation
4. **EXECUTIVE_SUMMARY.md** - Tóm tắt ngắn gọn

---

## ⚠️ LƯU Ý QUAN TRỌNG

1. **KHÔNG XÓA** các task đã có
2. **CHỈ CẬP NHẬT** State, Assignee, Note columns
3. **THÊM MỚI** RB29-33 cho PB13, PB14
4. **GHI CHÚ** file path ở cột Note để dễ tìm

---

## 🔄 AFTER UPDATE

Sau khi cập nhật file .md, cần:

1. Export sang CSV để sync với Drive
2. Update Trello board (nếu có)
3. Notify team về changes
4. Review lại PPS scores (có thể cần adjust vì một số task done sớm)

---

*Created: 2026-06-05*  
*Purpose: Guide for updating main sprint backlog file*
