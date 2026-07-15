# Ke hoach Phase 4: User Workspace va Admin Console

Tai lieu nay la ban tieng Viet de dev doc nhanh. Cac file `dev1/README.md`, `dev2/README.md`, `dev3/README.md` van la brief chi tiet ve contract, file scope va test.

## User Side Can Hoan Thanh

- Profile ca nhan.
- Quan ly API key.
- Danh sach project da import.
- Hien thi plan hien tai.
- Hien thi dung luong da dung/con lai.
- Hien thi credit theo plan va credit con lai.
- Gui report/feedback.
- Xem va phan hoi trong report thread.
- Thay thong bao neu account bi block.

## Admin Side Can Hoan Thanh

- Dashboard tong quan.
- So user dang ky.
- So user online realtime/polling chart.
- So project da import.
- Quan ly user.
- Tao user.
- Block/deactivate user kem ly do.
- Cap nhat plan.
- Tang quota override, nhap va validate theo MB.
- Tang credit override cho user Enterprise/custom.
- Xem va dieu chinh credit cua user.
- Quan ly pricing rule credit neu can.
- Vo hieu hoa tinh nang tao API key.
- Vo hieu hoa API key cu the.
- Quan ly feedback/report.
- Admin phan hoi qua lai voi user.
- Dong report.

## Backend Nen Tang Con Phai Xong

- Quota enforcement that su khi import/patch/analyze.
- Plan/credit DB foundation: Free 100MB+100 credit, Pro 500MB+500 credit, Pro Plus 1024MB+1000 credit, Max 2048MB+2000 credit, Enterprise lien he dam phan.
- Credit deduction khi user call MCP hoac dung VibeGraph CLI, khong hardcode gia trong code ma doc tu bang pricing rule.
- Enterprise dung `contact_sales_required=true`; admin set dung luong override theo MB va credit override trong dashboard.
- Feedback/report APIs.
- Admin APIs.
- Cleanup report sau 1 tuan tinh tu luc closed.

## Cong Thuc Tinh Credit

Credit khong hardcode trong business code. Backend doc tu bang `credit_pricing_rules`.

Cong thuc chung:

```text
credits = base_credits
        + (file_count * per_file_credits)
        + (source_mb * per_mb_credits)
        + (node_count / 1000 * per_1k_nodes_credits)
```

Sau khi tinh xong, backend lam tron theo rule ma Dev1 chot trong implementation, nhung khong duoc tinh thap hon `minimum_credits`.

Vi du pricing hien tai:

| Operation | Base | Per file | Per MB | Vi du |
| --- | ---: | ---: | ---: | --- |
| `MCP_TOOL_CALL` | 1 | 0 | 0 | Goi 1 MCP tool = 1 credit |
| `CLI_PUSH` | 1 | 0.1 | 0 | Push 20 file = 1 + 20*0.1 = 3 credits |
| `PROJECT_ANALYZE` | 5 | 0.01 | 1 | Analyze 100 file, 20MB = 5 + 1 + 20 = 26 credits |
| `IMPORT_ARCHIVE` | 3 | 0 | 1 | Import archive 50MB = 3 + 50 = 53 credits |
| `IMPORT_GITHUB` | 3 | 0 | 1 | Import GitHub 80MB = 3 + 80 = 83 credits |

Y nghia:
- `base_credits`: phi co dinh moi lan dung tinh nang.
- `per_file_credits`: phi theo so file.
- `per_mb_credits`: phi theo dung luong source.
- `per_1k_nodes_credits`: phi theo do lon graph/analyze, tinh theo moi 1000 nodes.
- `minimum_credits`: muc toi thieu phai tru cho operation do.

## Frontend Con Phai Lam

- Toan bo man hinh user account/workspace.
- Toan bo admin dashboard/user/report UI.
- Ket noi voi backend APIs.

## Chia Viec Cho 3 Dev

### Dev1: Backend User

Dev1 lam cac phan backend phuc vu user:
- Quota enforcement that su khi import/patch/analyze.
- Feedback/report APIs cho user.
- Dam bao account bi block khong bypass duoc project/import/patch/API key.
- Dam bao usage/quota cap nhat dung.

Doc chi tiet: `task-update/dev1/README.md`

### Dev2: Backend Admin

Dev2 lam cac phan backend phuc vu admin:
- Admin dashboard overview.
- Quan ly user.
- Tao user.
- Block/unblock/deactivate user kem ly do.
- Cap nhat plan.
- Tang quota override.
- Vo hieu hoa tao API key.
- Vo hieu hoa API key cu the.
- Quan ly feedback/report va admin reply.

Doc chi tiet: `task-update/dev2/README.md`

### Dev3: Frontend

Dev3 lam UI:
- User account/workspace.
- Profile.
- API key UI.
- My projects.
- Plan/quota meter.
- Report/feedback UI.
- Admin dashboard.
- Admin user management.
- Admin feedback/report UI.
- Ket noi voi backend APIs da co.

Doc chi tiet: `task-update/dev3/README.md`

## Rule De Tranh Xung Dot

- Dev1 so huu `/api/account/**` va user report/quota behavior.
- Dev2 so huu `/api/admin/**` va admin management.
- Dev3 chi sua `vibegraph-web/**`.
- Khong ai dung `git add .`.
- Khong ai commit/push neu chua duoc Supervisor approve.
- Backend phai chay test va GitNexus detect truoc handoff.
- Frontend phai chay typecheck/test/build truoc handoff.
