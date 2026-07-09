# Dev3: Frontend User Workspace Va Admin Console

## Muc Tieu

Lam UI cho user va admin trong `vibegraph-web/**`.

## User Side Can Lam

- Profile ca nhan.
- Quan ly API key.
- Danh sach project da import.
- Hien thi plan hien tai.
- Hien thi dung luong da dung/con lai.
- Gui report/feedback.
- Xem va phan hoi trong report thread.
- Thay thong bao neu account bi block.

## Admin Side Can Lam

- Dashboard tong quan.
- So user dang ky.
- So user online realtime/polling chart.
- So project da import.
- Quan ly user.
- Tao user.
- Block/deactivate user kem ly do.
- Cap nhat plan.
- Tang quota override.
- Vo hieu hoa tinh nang tao API key.
- Vo hieu hoa API key cu the.
- Quan ly feedback/report.
- Admin phan hoi qua lai voi user.
- Dong report.

## Trang Thai API

Co the lam ngay voi backend da co:
- `GET /api/account/profile`
- `PATCH /api/account/profile`
- `GET /api/account/usage`
- `GET /api/account/projects`
- `POST /api/account/api-keys`
- `GET /api/account/api-keys`
- `PATCH /api/account/api-keys/{id}/disable`

Cho Dev1/Dev2 handoff roi moi wire:
- report/feedback APIs.
- admin overview/users/report APIs.
- quota enforcement states neu backend contract co thay doi.

## Copy Bat Buoc

Blocked:
`Your account is blocked. Project analysis, imports, patches, and API keys are paused. Reason: {safeReason}. You can still open a report if this looks incorrect.`

Quota exceeded:
`Source storage quota exceeded. Free up storage or ask an admin for a quota override.`

API keys disabled:
`API key creation is disabled for your account.`

Plan limit reached:
`API key limit reached for your current plan.`

Soft deactivate:
`Deactivate user`
`This disables sign-in and API access without immediately removing account data.`

## UI Yeu Cau

- UI dang dashboard/operation tool, khong lam landing page.
- Desktop: table day du, sticky header, filter nam tren.
- Tablet: table co horizontal scroll, action gom vao menu.
- Mobile: row xep thanh block, action nguy hiem co confirm.
- Quota meter, status chip, action button phai co min/fixed width de khong giat layout.
- Report thread tren mobile co reply box sticky bottom.
- Admin online chart poll overview moi 15-30 giay khi tab visible.

## Test Bat Buoc

- Profile load/update.
- API key list/create/disable.
- Render loi blocked/quota/API-key-disabled/plan-limit.
- Admin overview polling test voi fake timers.
- Quota override validation tren UI.
- Feedback thread reply/close khi API co.

## Khong Duoc Lam

- Khong sua backend Java.
- Khong sua CLI.
- Khong commit/push neu chua duoc approve.
- Khong dung `git add .`.

## Lenh Verify

Chay trong `vibegraph-web/`:
- `npm test -- --run`
- `npm run typecheck` hoac lenh `vue-tsc --build --noEmit` hien co.
- `npm run build`

Neu co thay doi UI lon, chay dev server port 5173 va smoke bang browser.
