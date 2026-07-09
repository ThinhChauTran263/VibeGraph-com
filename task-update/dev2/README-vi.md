# Dev2: Backend Admin

## Muc Tieu

Hoan thanh backend cho admin:
- Dashboard tong quan.
- Quan ly user.
- Quan ly plan/quota override.
- Quan ly API key cua user.
- Quan ly feedback/report.

## Viec Can Lam

### 1. Admin Dashboard

Endpoint:
- `GET /api/admin/overview`

Can tra:
- So user da dang ky.
- So user online realtime/polling count.
- So project da import.
- So report.
- So report dang open.
- So user bi block.
- Timestamp hien tai.

Frontend se poll moi 15-30 giay va ve chart online users.

### 2. Admin User Management

Can lam:
- List/search/filter user.
- Tao user bang temporary password.
- Block user kem ly do.
- Unblock user.
- Deactivate user theo nghia soft disable, khong hard delete.
- Cap nhat plan.
- Tang quota override.
- Chan quota override nho hon current usage.
- Vo hieu hoa tinh nang tao API key cua user.
- Vo hieu hoa API key cu the.

Contract tao user:
```ts
interface AdminCreateUserRequest {
  email: string
  displayName: string
  role: 'USER' | 'ADMIN'
  planCode: 'FREE' | 'PRO' | 'TEAM'
  temporaryPassword: string
}
```

### 3. Admin Feedback/Report

Can lam:
- Admin xem tat ca report.
- Admin xem thread report.
- Admin reply qua lai voi user.
- Admin dong report.
- Closed report co `deletesAfter`.

## Test Bat Buoc

- Normal user bi forbidden voi admin endpoints.
- Admin xem overview.
- Admin tao user.
- Duplicate email reject an toan.
- Admin block/unblock user kem ly do.
- Blocked user login/JWT bi `ACCOUNT_BLOCKED`.
- Deactivate disable sign-in/API access.
- Update plan FREE/PRO/TEAM.
- Quota override nho hon current usage bi reject.
- API key creation disabled lam user khong tao key duoc.
- Admin xem/reply/close report.

## Khong Duoc Lam

- Khong sua frontend.
- Khong sua CLI.
- Khong sua quota internals cua Dev1 neu khong can.
- Khong commit/push neu chua duoc approve.
- Khong dung `git add .`.

## Lenh Verify

- Focused tests cho admin.
- `.\mvnw.cmd test`
- `.\mvnw.cmd verify`
- `npx gitnexus detect-changes --scope all --repo VibeGraph-com`
