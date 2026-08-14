#!/usr/bin/env bash
# Kiểm chứng lại mọi claim trong CROSS-AUDIT-VERIFICATION.md
#
# READ-ONLY: script này không ghi, không xoá, không sửa gì trong repo.
# Chạy từ gốc repo:  bash update/docs/claude/verify-claims.sh
#
# KHÔNG in giá trị secret — chỉ in tên biến và số lượng.

set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 1

h() { printf '\n\033[1m=== %s ===\033[0m\n' "$*"; }

h "0. Hai báo cáo codex/docs có phải cùng một file"
diff -q docs/audit-report-v2-2026-08-12.md \
        update/docs/codex/VibeGraph-audit-report-2026-08-12.md \
  && echo "IDENTICAL" || echo "DIFFERENT (kỳ vọng: 5 hunk — docs/ thêm H8 + §10)"
echo "-- nhãn phiên bản trong audit-report-v2 --"
grep -oE 'v2\.[0-9]' docs/audit-report-v2-2026-08-12.md | sort | uniq -c

h "1. SECRET TRONG GIT OBJECT DATABASE (Qwen S2)"
echo "-- .env có bao giờ được commit lên branch nào? (rỗng = chưa) --"
git log --all --oneline -- .env '.env.codex*' '.env.*backup*' || true
echo "-- stash@{0} có mấy parent? (3 parent = có untracked files) --"
git rev-list --parents -n1 "stash@{0}" 2>/dev/null
echo "-- parent thứ 3 --"
git rev-parse "stash@{0}^3" 2>/dev/null
echo "-- file trong parent thứ 3 --"
git show --stat --format= "stash@{0}^3" 2>/dev/null | grep -i env || true
echo "-- biến nhạy cảm trong file đó (CHỈ TÊN, không giá trị) --"
obj=$(git rev-parse "stash@{0}^3" 2>/dev/null)
envf=$(git show --stat --format= "$obj" 2>/dev/null | grep -oE '\.env[^ |]*' | head -1)
[ -n "${envf:-}" ] && git show "$obj:$envf" 2>/dev/null \
  | grep -oE '^[A-Z_0-9]+=' | tr -d '=' \
  | grep -E 'SECRET|PASSWORD|KEY|TOKEN' | sort -u
echo "-- có secret OAuth thật trong history? (kỳ vọng: 1 commit) --"
printf 'commit chứa GOCSPX- : '; git log --all -S'GOCSPX-' --oneline 2>/dev/null | wc -l
echo "-- LƯU Ý: 'git stash show --name-only' KHÔNG thấy các file này --"
git stash show --name-only "stash@{0}" 2>/dev/null | grep -ci env || echo "0  <-- chính là cái bẫy"

h "2. SỐ DÒNG THẬT (đối chiếu §2 của báo cáo)"
for f in vibegraph-web/src/views/admin/UserDetailDrawer.vue \
         vibegraph-web/src/views/LandingView.vue \
         vibegraph-web/src/components/graph/GraphCanvas.vue \
         vibegraph-web/src/composables/useSigma.ts \
         vibegraph-web/src/lib/api.ts \
         vibegraph-web/src/stores/admin.ts \
         src/main/java/com/vibegraph/diagram/service/impl/UseCaseInferenceEngine.java; do
  [ -f "$f" ] && printf '%6d  %s\n' "$(wc -l < "$f")" "$f"
done
echo "(UseCaseInferenceEngine: cả 3 báo cáo ghi 1283)"

h "3. THỨ TỰ FILTER — rate-limit sau BCrypt?"
grep -nE 'addFilter(At|Before|After)' src/main/java/com/vibegraph/auth/config/SecurityConfig.java

h "4. .env — CHỈ giá trị boolean/rỗng, không phải secret"
grep -nE '^(VIBEGRAPH_TRUST_PROXY|VIBEGRAPH_TRUSTED_PROXIES|AUTH_COOKIE_SECURE|VIBEGRAPH_PROJECTS_ALLOWED_ROOT|VITE_GRAPH_SAFE_NODE_LIMIT|VIBEGRAPH_PARSER_USE_CACHE)=' .env

h "5. readRange OOM + redact private key"
grep -nE 'readAllLines|Files\.size|MAX_FILE_BYTES_TO_SCAN|PRIVATE_KEY_HEADER|REDACTED' \
  src/main/java/com/vibegraph/mcp/source/impl/SourceFileServiceImpl.java | head -20
echo "(dòng 110 = readRange KHÔNG chốt size; dòng 196 = search CÓ chốt)"

h "6. Project ID substring(0,8) — bao nhiêu chỗ, dòng nào"
grep -n 'substring(0, 8)' src/main/java/com/vibegraph/graph/service/impl/ProjectServiceImpl.java
echo "(Qwen H7 ghi 'dòng 62, 72')"

h "7. Actuator lộ metrics cho USER thường"
grep -nA6 'exposure' src/main/resources/application-prod.yaml | head -10
grep -n '/actuator/health' src/main/java/com/vibegraph/auth/config/SecurityConfig.java

h "8. .dockerignore"
ls -la .dockerignore 2>&1
ls -la vibegraph-web/.dockerignore 2>&1

h "9. DEAD CODE FRONTEND (Qwen F-M1) — 9 file"
cd vibegraph-web/src || exit 1
total=0
for n in HeaderBar MainLayout SidePanel StatusBar GraphControls CodeInspector \
         AddProjectLocal DirectoryBrowserModal useLocalImport; do
  p=$(find . -name "$n.vue" -o -name "$n.ts" | head -1)
  imports=$(grep -rEl "from ['\"].*/$n(\.vue|\.ts)?['\"]" . --include=*.ts --include=*.vue 2>/dev/null | grep -v "/$n\." | wc -l)
  l=$([ -n "$p" ] && wc -l < "$p" || echo 0)
  total=$((total + l))
  printf '%-24s dòng=%-5s import=%s\n' "$n" "$l" "$imports"
done
echo "TỔNG DÒNG = $total  (Qwen ghi ~1328)"
echo "-- tham chiếu còn lại là comment hay import? --"
grep -rn "HeaderBar\|SidePanel\|CodeInspector" . 2>/dev/null \
  | grep -vE "(layout|panels)/(HeaderBar|SidePanel|CodeInspector)\.vue:"
cd ../.. || exit 1

h "10. N+1 AdminService (Qwen H9) — trong toAdminUserResponse"
awk '/private AdminUserResponse toAdminUserResponse/,/^    }/' \
  src/main/java/com/vibegraph/auth/service/AdminService.java \
  | grep -nE 'settingsRepository\.findById|sumStorageBytesByOwnerId'
echo "(2 query cho MỖI user khi map danh sách phân trang)"

h "11. UsersTableView — gọi store vs try/catch (Qwen H11)"
uv=vibegraph-web/src/views/admin/UsersTableView.vue
printf 'gọi adminStore.*: %s\n' "$(grep -cE 'adminStore\.[a-zA-Z]+\(' $uv)"
printf 'try {          : %s\n' "$(grep -c 'try {' $uv)"
printf 'catch          : %s\n' "$(grep -c 'catch' $uv)"

h "12. IpBlockService không cache + telemetry shed-oldest"
grep -nE '@Cacheable|@Transactional|findActive' src/main/java/com/vibegraph/abuse/IpBlockService.java | head -5
find src -name RequestEventService.java | head -1 | xargs grep -nE 'freshQueue.poll|securityDropped' | head -5

h "13. MethodVisitor đọc system property (Qwen B-M3)"
grep -rn 'Boolean.getBoolean' src/main/java/ || true
echo "-- key này có trong resources không? (rỗng = không có đường cấu hình) --"
grep -rn 'emit-unresolved-call-stubs' src/main/resources/ 2>/dev/null || echo "(không có)"

h "14. task/ vs task-final/"
printf 'task/      : %s file\n' "$(git ls-files | grep -cE '^task/')"
printf 'task-final/: %s file\n' "$(git ls-files | grep -cE '^task-final/')"

h "15. RÁC — git clean -fdX sẽ xoá GẤP 9 LẦN thứ Qwen mô tả"
git status --porcelain --ignored | awk '/^!!/{sub(/^!! /,"");print}' > /tmp/vg-ign.txt 2>/dev/null
printf 'số entry ignored: %s\n' "$(wc -l < /tmp/vg-ign.txt)"
echo "-- top 8 --"
du -sh $(tr -d '"' < /tmp/vg-ign.txt | sed 's|/$||') 2>/dev/null | sort -rh | head -8
echo "-- phần THỰC SỰ là rác (log/dump/json ở root) --"
printf 'số file: %s\n' "$(ls -1 ./*.log ./*.out ./*.err ./*.stackdump ./*.diff graph_check.json 2>/dev/null | wc -l)"
du -sch ./*.log ./*.out ./*.err ./*.stackdump ./*.diff graph_check.json 2>/dev/null | tail -1
rm -f /tmp/vg-ign.txt
echo
echo ">>> KHÔNG chạy 'git clean -fdX': nó xoá cả node_modules, .gitnexus (index), target, .vibegraph"

h "16. npm audit (Qwen H16 vs v2 H8)"
if [ -d vibegraph-web/node_modules ]; then
  ( cd vibegraph-web && npm audit 2>/dev/null | tail -5 )
  echo "(kỳ vọng: 1 critical / 6 high / 1 moderate = 8. v2 H8 chỉ kể 7 package, thiếu jsdom)"
else
  echo "(bỏ qua: chưa npm install)"
fi

h "17. Tỷ lệ nội dung meta trong audit-report-v2 (tôi từng ghi 40% — sai)"
awk '/^## /{s=$2} {c[s]+=length($0)+1; t+=length($0)+1}
     END{m=c["3."]+c["9."]+c["10."]; printf "meta = %d / %d ký tự = %.1f%%\n", m, t, 100*m/t}' \
  docs/audit-report-v2-2026-08-12.md

printf '\n\033[1mXONG. Không có file nào bị sửa.\033[0m\n'
