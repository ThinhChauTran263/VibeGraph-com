# Prompt Cho opencode

Ban thay the ClaudeChat. Khong dung ClaudeChat nua.

Ban la frontend owner cho user/admin API key UI theo backend contract moi. Chi bat dau sua sau khi doc:
- `task-update/api-key-lifecycle-supervisor/README.md`
- `task-update/api-key-lifecycle-supervisor/CladueCli_API_KEY_LIFECYCLE_HANDOFF.md` neu file da ton tai.

Khong revert thay doi cua agent khac. Khong commit/push/merge. Neu file da bi agent khac sua, doc diff va merge bang tay trong scope cua minh, khong hoi user yes/no.

Product decision:
- User tu tao API key, bat buoc chon repository/project.
- User co the disable key cua minh.
- User co the delete key cua minh neu key khong bi admin locked.
- User phai delete key cu truoc khi tao key moi cho cung project.
- Neu key bi admin locked/disabled, UI phai hien ly do ro va disable nut delete/recreate.
- Admin khong tao API key dum user.
- Admin chi xem list key va disable/lock key.

Scope frontend:
- `vibegraph-web/src/types/api.ts`
- `vibegraph-web/src/lib/api.ts`
- `vibegraph-web/src/stores/account.ts`
- `vibegraph-web/src/stores/admin.ts`
- `vibegraph-web/src/views/user/ApiKeysView.vue`
- `vibegraph-web/src/views/admin/UserDetailDrawer.vue`
- Tests lien quan.

Yeu cau UI:
- User API Keys:
  - Form tao key co select repository/project.
  - Neu project da co key chua deleted, form khong cho tao key moi cho project do va hien thong bao: delete key cu truoc.
  - Nut delete co confirm dialog custom, khong dung browser alert/confirm.
  - Key admin locked: hien badge/label ro, disable delete, disable recreate cho project do.
  - One-time secret van chi hien sau create thanh cong.
- Admin User Detail:
  - Khong co form tao API key.
  - Chi hien danh sach key cua user, project binding, status, disabled by neu backend tra ve.
  - Admin disable/lock key bang button ro rang.
- Tat ca disabled state phai fail closed va co ly do, khong de user click nut roi im lang.

Tests bat buoc:
- Type-check pass.
- User ApiKeysView tests cover create, duplicate project blocked, delete, admin-locked key cannot delete.
- Admin drawer tests cover no create UI, list/disable only.
- API contract tests confirm khong goi `POST /api/admin/api-keys`.
- Khong localStorage JWT, khong mock business logic production, khong alert/confirm.

Gate:
- `npm --prefix vibegraph-web run type-check`
- `npm --prefix vibegraph-web run test:unit -- --run`
- `npm --prefix vibegraph-web run build`
- `git diff --check`

Handoff:
- Tao `task-update/api-key-lifecycle-supervisor/opencode_API_KEY_UI_HANDOFF.md`
- Ghi exact files changed, screenshots neu co, test results, va blocker neu backend chua san sang.

