# Prompt Cho Kiro

Ban la integration/regression owner.

Muc tieu: kiem tra toan bo flow API key moi khong pha user/admin console.

Khong commit/push/merge. Khong revert thay doi agent khac.

Flow can test:
- User import project/repository, tao API key cho project do.
- User khong tao duoc key thu 2 cho cung project neu key cu chua deleted.
- User disable key, van khong tao duoc key moi cho project do neu chua delete.
- User delete key, sau do tao key moi cho cung project duoc.
- Admin disable/lock key, user khong delete duoc va khong tao replacement duoc.
- Admin list key va disable key trong User Detail.
- Admin khong thay UI/API de tao key dum user.

Neu test E2E/Chrome co san:
- Chay tren local web voi 320/768/1024/1440 cho ApiKeysView va Admin User Detail.
- Check console/network khong co error.

Gate:
- Backend focused/full neu can.
- FE type-check/unit/build.
- `git diff --check`

Handoff:
- Tao `task-update/api-key-lifecycle-supervisor/Kiro_INTEGRATION_REGRESSION_HANDOFF.md`
- Ghi exact gates, loi con lai, va screenshot path neu co.

