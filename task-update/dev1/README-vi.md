# Dev1: Backend User Workspace

## Muc Tieu

Hoan thanh backend cho phan user:
- Quota enforcement that su khi import/patch/analyze.
- Feedback/report APIs cho user.
- Dam bao blocked account bi chan dung contract.

## Viec Can Lam

### 1. Quota Enforcement

Can enforce quota cho:
- import local project.
- upload/import archive.
- GitHub/tarball import neu co.
- `POST /api/projects/{projectId}/patch`.
- analyze neu analyze co cap nhat source storage.

Quy tac:
- Quota tinh theo source storage.
- Plan FREE co 500MB.
- Neu admin co quota override cao hon plan thi dung override.
- Vuot quota tra `409 QUOTA_EXCEEDED`.
- Message an toan:
  - `Source storage quota exceeded. Free up storage or ask an admin for a quota override.`
- Khong leak host path, file content, token, secret.
- Patch phai atomic: neu vuot quota thi khong ghi bat ky file nao.
- `dryRun=true` khong duoc persist usage.
- Replace file tinh delta, khong tinh double.
- Delete file phai giam usage.

### 2. User Feedback/Report APIs

Can lam:
- User tao report/feedback.
- User xem danh sach report cua minh.
- User xem thread report cua minh.
- User reply trong report thread.
- User dong report khi da giai quyet.
- Report closed co `deletesAfter = closedAt + 7 ngay`.

## Test Bat Buoc

- Duoi quota thi pass.
- Vuot quota thi `409 QUOTA_EXCEEDED`.
- Patch vuot quota khong ghi file nao.
- Replace file tinh dung delta.
- Delete file giam usage.
- Dry-run khong persist usage.
- Account usage hien dung sau import/patch/delete.
- Quota override cho phep vuot plan.
- Blocked account tra `ACCOUNT_BLOCKED`, khong tra `QUOTA_EXCEEDED`.
- User khong xem/sua report cua user khac.
- Closed report co deletes-after timestamp.

## Khong Duoc Lam

- Khong sua frontend.
- Khong sua CLI.
- Khong commit/push neu chua duoc approve.
- Khong dung `git add .`.

## Lenh Verify

- Focused tests cho quota/report/patch/import.
- `.\mvnw.cmd test`
- `.\mvnw.cmd verify`
- `npx gitnexus detect-changes --scope all --repo VibeGraph-com`
