# 📊 Task Distribution - VibeGraph Project

## 🎯 Overall Statistics

```
Total Project Duration: 8 weeks (4 sprints x 2 weeks)
Total Tasks: 121 (T01-T121)
Total Estimate: 598 hours
Team Size: 5 people
Capacity per Sprint: 480 hours (5 people x 8h/day x 12 days)
```

---

## 📅 Sprint Breakdown

### Sprint 1 (Weeks 1-2) - Foundation
**Status:** ✅ 95% Complete  
**Tasks:** T01-T72 (focused on Sprint 1 only: ~38 tasks)  
**Hours:** ~145h actual Sprint 1 work  

**Key Deliverables:**
- ✅ Archive import (ZIP/TAR)
- ✅ JavaParser infrastructure (visitors)
- ✅ Neo4j storage layer
- ✅ Basic REST API
- ✅ Frontend foundation (Sigma.js)
- ✅ Search functionality

```
Progress: ████████████████░░░░ 95%
```

### Sprint 2 (Weeks 3-4) - Core Features
**Status:** 🚧 20% In Progress  
**Tasks:** T07-T72 (remaining Sprint 2 tasks: ~34 tasks)  
**Hours:** ~145h Sprint 2 work  

**Key Deliverables:**
- 🚧 GitHub import (T07-T11)
- 🚧 WebSocket realtime (T35-T38)
- 🚧 Diagram services (T39-T42)
- 🚧 MCP tools basic (T43-T47)
- 🚧 Filter & focus (T56-T57)
- 🚧 Docker setup (T63-T65)

```
Progress: ████░░░░░░░░░░░░░░░░ 20%
```

### Sprint 3 (Weeks 5-6) - Polish & Performance
**Status:** ❌ Not Started  
**Tasks:** T73-T98 (26 tasks)  
**Hours:** ~165h  

**Key Deliverables:**
- MCP tools completion (T73-T77)
- OpenAPI docs (T78-T79)
- Parser robustness (T80-T83)
- Performance optimization (T84-T88)
- UI polish (T89-T92)
- Technical debt (T93-T95)
- Test coverage 70% (T96-T98)

```
Progress: ░░░░░░░░░░░░░░░░░░░░ 0%
Capacity: ████████████████████ 165/480h (34%)
```

### Sprint 4 (Weeks 7-8) - Deployment & Demo
**Status:** ❌ Not Started  
**Tasks:** T99-T121 (23 tasks)  
**Hours:** ~143h  

**Key Deliverables:**
- Production deployment (T99-T102)
- Domain + SSL (T103-T105)
- CI/CD completion (T106-T108)
- Demo preparation (T109-T112)
- Documentation (T113-T117)
- Final polish (T118-T121)

```
Progress: ░░░░░░░░░░░░░░░░░░░░ 0%
Capacity: ████████████████████ 143/480h (30%)
```

---

## 👥 Workload by Team Member

### Khoa (Fullstack Developer)
**Total:** ~170 hours across 4 sprints

| Sprint   | Tasks                                            | Hours | Focus Area                              |
| -------- | ------------------------------------------------ | ----- | --------------------------------------- |
| Sprint 1 | T01, T02, T05, T13, T17, T18, T23, T24, T27, T28 | ~45h  | Archive import, Parser, Neo4j, API      |
| Sprint 2 | T07, T08, T32, T33, T37, T39, T43, T45           | ~50h  | GitHub import, Impact, FileWatcher, MCP |
| Sprint 3 | T73, T76, T83, T88, T93, T94                     | ~45h  | MCP tools, Performance, Tech debt       |
| Sprint 4 | T120                                             | ~8h   | Performance tuning                      |

```
Workload: ███████████████████░ 170/192h (89%)
Balanced: YES ✅
```

### Vinh (Backend + DevOps + Scrum Master)
**Total:** ~185 hours across 4 sprints

| Sprint   | Tasks                                  | Hours | Focus Area                            |
| -------- | -------------------------------------- | ----- | ------------------------------------- |
| Sprint 1 | T03, T04, T14, T15, T21, T22, T29      | ~35h  | Archive extract, Parser, Neo4j        |
| Sprint 2 | T09, T10, T19, T31, T35, T36, T44      | ~40h  | GitHub tarball, SymbolSolver, WS, MCP |
| Sprint 3 | T74, T80, T81, T82, T84, T86, T95      | ~55h  | Parser robust, Caching, Neo4j opt     |
| Sprint 4 | T99-T101, T103, T104, T106, T108, T120 | ~55h  | Docker, SSL, CI/CD, Perf              |

```
Workload: ████████████████████ 185/192h (96%)
Balanced: YES ✅ (High but manageable)
```

### Danh (Fullstack Developer)
**Total:** ~60 hours (Sprint 3-4 only)

| Sprint   | Tasks                                  | Hours | Focus Area                   |
| -------- | -------------------------------------- | ----- | ---------------------------- |
| Sprint 1 | -                                      | 0h    | Not assigned (joined later?) |
| Sprint 2 | -                                      | 0h    | Not assigned                 |
| Sprint 3 | T75, T77, T78, T79, T87, T89, T90, T92 | ~44h  | MCP, OpenAPI, UI polish      |
| Sprint 4 | T116, T121                             | ~10h  | API docs, UI/UX              |

```
Workload: ██████░░░░░░░░░░░░░░ 60/192h (31%)
Balanced: NO ⚠️ (Underutilized - can take more)
```

### Thái (BA + PO + Tester)
**Total:** ~155 hours across 4 sprints

| Sprint   | Tasks                                      | Hours | Focus Area                      |
| -------- | ------------------------------------------ | ----- | ------------------------------- |
| Sprint 1 | T12, T16, T30, T40, T41, T48-T53, T66, T69 | ~50h  | Data contracts, FE core, Config |
| Sprint 2 | T46, T47, T56, T57, T60, T72               | ~35h  | MCP config, Filter, WS, Docs    |
| Sprint 3 | T85, T96, T97                              | ~28h  | Performance test, Test coverage |
| Sprint 4 | T105, T109-T112, T114, T117, T119          | ~42h  | Demo, Docs, Integration test    |

```
Workload: ████████████████░░░░ 155/192h (81%)
Balanced: YES ✅
```

### Thịnh (Leader + PM)
**Total:** ~140 hours across 4 sprints

| Sprint   | Tasks                                                      | Hours | Focus Area                       |
| -------- | ---------------------------------------------------------- | ----- | -------------------------------- |
| Sprint 1 | T06, T20, T26, T34, T54                                    | ~18h  | Testing, Integration             |
| Sprint 2 | T11, T25, T38, T42, T55, T58, T59, T61, T62, T68, T70, T71 | ~55h  | Testing, FE panels, CI, QA       |
| Sprint 3 | T91, T96, T98                                              | ~28h  | Error handling, Testing          |
| Sprint 4 | T102, T107, T110, T113, T115, T119                         | ~39h  | Deployment guide, CI, Docs, Test |

```
Workload: ██████████████░░░░░░ 140/192h (73%)
Balanced: YES ✅
```

---

## 📈 Sprint Load Distribution

### Capacity Analysis

```
Sprint 1: 145h / 480h = 30% ████████░░░░░░░░░░░░
Sprint 2: 145h / 480h = 30% ████████░░░░░░░░░░░░
Sprint 3: 165h / 480h = 34% █████████░░░░░░░░░░░
Sprint 4: 143h / 480h = 30% ████████░░░░░░░░░░░░
```

**Analysis:**
- ✅ All sprints under 50% capacity
- ✅ Good buffer for unknowns
- ⚠️ Danh underutilized (31% vs team avg 78%)
- 💡 Can assign more FE/integration work to Danh

---

## 🎯 Recommended Adjustments

### 1. Balance Danh's Workload
**Current:** 60h (31% capacity)  
**Suggestion:** Add 60-80h more work from Sprint 3-4

**Tasks to Consider:**
- Move some FE components from Thái to Danh
- Assign more UI polish tasks (T89-T92)
- Share documentation work (T113-T117)
- Frontend integration tests (T62)

### 2. Reduce Vinh's Load Slightly
**Current:** 185h (96% capacity)  
**Suggestion:** Move 15-20h to Danh

**Tasks to Delegate:**
- T87 (5000-node rendering) → Danh ✅ (already assigned)
- Some Docker setup → Danh (can help with nginx)

### 3. Sprint 2 Focus
**Priority Tasks (Finish First):**
1. T07-T11: GitHub import (critical path)
2. T19: JavaSymbolSolver CALLS (foundation)
3. T35-T38: WebSocket realtime (user-facing)
4. T43-T47: MCP tools basic (demo blocker)

---

## 📊 Task Categories Summary

| Category          | Tasks | Hours | % of Total |
| ----------------- | ----- | ----- | ---------- |
| **Backend Core**  | 35    | 180h  | 30%        |
| **Frontend**      | 18    | 90h   | 15%        |
| **Testing/QA**    | 15    | 75h   | 13%        |
| **DevOps**        | 12    | 60h   | 10%        |
| **Documentation** | 10    | 50h   | 8%         |
| **MCP Tools**     | 10    | 52h   | 9%         |
| **Performance**   | 8     | 40h   | 7%         |
| **Integration**   | 13    | 51h   | 8%         |

```
Distribution:
Backend:  ████████████████████████████████ 30%
Frontend: ███████████████░░░░░░░░░░░░░░░░░ 15%
Testing:  █████████████░░░░░░░░░░░░░░░░░░░ 13%
DevOps:   ██████████░░░░░░░░░░░░░░░░░░░░░░ 10%
Others:   ████████████████████░░░░░░░░░░░░ 32%
```

---

## 🎯 Success Metrics

### Sprint 1 (DONE ✅)
- [x] 95% complete
- [x] Parser infrastructure working
- [x] Neo4j storage functional
- [x] Basic visualization live

### Sprint 2 (IN PROGRESS 🚧)
- [ ] GitHub import complete
- [ ] WebSocket realtime working
- [ ] MCP tools functional
- [ ] Docker compose running

### Sprint 3 (PLANNED 📋)
- [ ] Parser handles 95% of Java patterns
- [ ] Test coverage >70%
- [ ] Performance <30s for 500 files
- [ ] UI polish complete

### Sprint 4 (PLANNED 📋)
- [ ] HTTPS deployment live
- [ ] CI/CD passing
- [ ] Demo ready
- [ ] Documentation complete

---

## 📅 Timeline Overview

```
Week 1-2:  [████████████████░░░░] Sprint 1 - 95% DONE
Week 3-4:  [████░░░░░░░░░░░░░░░░] Sprint 2 - 20% IN PROGRESS
Week 5-6:  [░░░░░░░░░░░░░░░░░░░░] Sprint 3 - NOT STARTED
Week 7-8:  [░░░░░░░░░░░░░░░░░░░░] Sprint 4 - NOT STARTED
           └─────────────────────┘
           8 weeks / 598 hours / 121 tasks
```

**Risk Level:** 🟢 LOW  
**Reason:** 
- All sprints have 60-70% buffer
- Tasks well-distributed
- No critical dependencies blocking

**Recommendations:**
- ✅ Continue current pace
- ✅ Balance Danh's workload  
- ✅ Focus Sprint 2 on critical path
- ✅ Start Sprint 3 planning early

---

**Generated:** 2026-06-05  
**Source:** `VibeGraph_WS3_Sprint-Trello-BBCH-ERD.md`  
**Tool:** Task breakdown analysis

