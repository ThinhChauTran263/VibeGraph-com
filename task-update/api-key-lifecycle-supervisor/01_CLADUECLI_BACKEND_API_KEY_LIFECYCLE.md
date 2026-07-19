# Prompt Cho CladueCli

Ban la backend owner cho API key lifecycle cua VibeGraph.

Branch/worktree: dung branch hien tai, dirty worktree da co thay doi cua nhieu agent. Khong revert thay doi cua agent khac. Khong commit/push/merge.

Product decision bat buoc:
- Admin khong tao API key dum user. Khong duoc them lai `POST /api/admin/api-keys`.
- User tao API key cho repository/project cua ho.
- API key la dinh danh project cho CLI/MCP.
- Moi `(user_id, project_id)` chi duoc co 1 API key chua bi xoa.
- User muon tao key moi cho cung project thi bat buoc xoa key cu truoc.
- Neu admin disable/lock key cu, user khong duoc xoa key do va khong duoc tao key moi cho project do.
- Admin duoc list metadata key cua user va disable/lock key cu the.

Scope backend:
- `src/main/java/com/vibegraph/auth/domain/ApiKey.java`
- `src/main/java/com/vibegraph/auth/repository/ApiKeyRepository.java`
- `src/main/java/com/vibegraph/auth/service/ApiKeyService.java`
- `src/main/java/com/vibegraph/auth/web/AccountApiKeyController.java`
- `src/main/java/com/vibegraph/auth/web/AdminApiKeyController.java`
- API key DTOs.
- Flyway migration moi neu can.
- Tests lien quan API key.

Yeu cau ky thuat:
- Them soft delete cho API key, vi can giu audit/history. De xuat `deleted_at`.
- Them phan biet ai disable key. De xuat enum/string `disabled_by`: `USER`, `ADMIN`; them `disabled_reason` neu can.
- User disable key: `disabled_by=USER`.
- Admin disable key: `disabled_by=ADMIN` va duoc xem nhu locked.
- User delete endpoint: `DELETE /api/account/api-keys/{id}`.
- User chi duoc delete key cua minh khi key khong bi admin locked.
- Creation phai reject neu ton tai key chua `deleted_at` cho cung `(user_id, project_id)`, ke ca key da disabled.
- Creation phai reject neu key ton tai cho project do va key bi admin locked, message phai ro: can lien he support/admin.
- List endpoints mac dinh khong tra secret/hash, va co status/metadata du de FE biet key nao user co the delete.
- Admin endpoint chi giu:
  - `GET /api/admin/api-keys?userId=...`
  - `PATCH /api/admin/api-keys/{id}/disable`
  - Khong co create.
- Cap nhat audit log cho create, disable, delete.

Tests bat buoc:
- User create project-bound key thanh cong.
- User create key lan 2 cho cung project bi 409/validation conflict neu key cu chua deleted.
- User delete key cua minh thanh cong neu khong admin locked.
- Sau delete, user tao key moi cho cung project thanh cong.
- User khong delete duoc key bi admin disable/locked.
- Admin disable key lam user khong tao replacement cho cung project.
- Admin list/disable van hoat dong.
- Khong con compile ref toi `AdminApiKeyCreateRequest`, `createForUser`, hoac `POST /api/admin/api-keys`.

Gate:
- `.\mvnw.cmd "-Dtest=*ApiKey*,*Feature*,*Session*,*Account*" test`
- `.\mvnw.cmd clean test`
- `git diff --check`

Handoff:
- Tao `task-update/api-key-lifecycle-supervisor/CladueCli_API_KEY_LIFECYCLE_HANDOFF.md`
- Ghi exact files changed, test results, API contract moi, migration moi, va cac blocker neu co.

