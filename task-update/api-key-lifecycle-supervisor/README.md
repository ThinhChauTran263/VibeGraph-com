# API Key Lifecycle Supervisor

Muc tieu: cap nhat API key dung product decision moi.

Quyet dinh da chot:
- Admin khong tao API key dum user.
- User tu tao API key va bat buoc chon repository/project.
- API key la dinh danh project cho CLI/MCP.
- Moi user moi project chi duoc co 1 key chua bi xoa.
- Neu key A da dung cho project B, user phai xoa key A truoc khi tao key moi cho project B.
- Neu admin disable/lock key A vi sai pham, user khong duoc xoa key A va khong duoc tao key moi cho project B cho toi khi admin xu ly/mo khoa.
- Admin van duoc xem metadata key, disable/lock key cu the, va tat quyen tao API key cua user.

Agent roster hien tai:
- CladueCli: backend API key lifecycle.
- opencode: frontend user/admin API key UI. Thay the ClaudeChat, khong dung ClaudeChat nua.
- Droid: CLI/MCP contract reverify.
- Kiro: integration and regression tests.
- CodexCli: supervisor integration cleanup.
- gemini: final strict review.

Rule lam viec:
- Khong dung, khong hoi yes khi gap file da co thay doi cua agent khac. Doc diff, giu thay doi cua nguoi khac, chi sua dung scope.
- Khong revert file khong thuoc minh.
- Khong commit, push, merge.
- Moi agent phai tao handoff rieng trong folder nay.
- Neu backend contract thay doi, FE agent phai doc handoff backend truoc khi sua UI.

