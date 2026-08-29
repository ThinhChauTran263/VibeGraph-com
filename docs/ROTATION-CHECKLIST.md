# Đợt 0 — Checklist xoay (rotate) secret đã lộ

> Tài liệu thao tác cho chủ repo. Mọi dữ kiện dưới đây đã được kiểm bằng lệnh trên máy
> vào **2026-08-13**, repo `D:\Users\User\IdeaProjects\VibeGraph`, branch
> `backup-full-fixed-20260728`. Các mục không kiểm được bằng lệnh (UI nhà cung cấp)
> được đánh dấu `[chưa xác minh]`.
>
> **Quy tắc khi làm theo checklist này:** không bao giờ in giá trị secret ra terminal,
> ra file, ra chat. Khi cần đối chiếu `.env`, chỉ liệt kê TÊN biến:
>
> ```bash
> grep -oE '^[A-Z_0-9]+=' .env | tr -d '='
> ```

---

## 1. Trạng thái hiện tại

### 1.1 Phần dọn git — ĐÃ XONG

| Kiểm tra | Lệnh | Kết quả thực tế |
|---|---|---|
| `.env` chưa bao giờ được commit | `git log --all --oneline -- .env` | rỗng (không có commit nào) |
| Commit nghi vấn đã biến mất | `git cat-file -t 388632b` | `fatal: Not a valid object name 388632b` |
| Không còn loose object / rác | `git count-objects -v` | `count: 0`, `garbage: 0`, `in-pack: 7586`, `packs: 1` |
| Quét toàn bộ object DB — pattern OAuth secret | `git cat-file --batch-all-objects --batch \| grep -c 'GOCSPX-'` | `0` |
| Quét toàn bộ object DB — pattern Google API key | `git cat-file --batch-all-objects --batch \| grep -c 'AIzaSy'` | `0` |
| Stash list | `git stash list` | rỗng |
| Remote đích | `git remote -v` | `https://github.com/ThinhChauTran263/VibeGraph-com.git` |

Kết luận: **object database của repo local hiện sạch** với 2 pattern trên (7586 object
đã quét). `refs/stash` là ref local-only nên chưa từng được push lên remote.

### 1.2 Đính chính về `refs/stash` — ĐỌC TRƯỚC KHI DROP

`git stash list` rỗng nhưng ref vẫn tồn tại:

```bash
git rev-parse --verify refs/stash   # -> 9958d82a9a317772e7c7d1cad618ef8e41b64084
git reflog show refs/stash          # -> rỗng (reflog đã bị xoá => stash "mồ côi")
```

Về mặt secret, stash này **an toàn**:

```bash
git ls-tree -r --name-only refs/stash | grep -E '(^|/)\.env'   # -> chỉ ".env.example", KHÔNG có .env
for p in 'GOCSPX-' 'AIzaSy' 'postgres://' '@db\.'; do \
  echo -n "$p : "; git show refs/stash:.env.example | grep -c "$p"; done
# -> tất cả đều 0
```

**Nhưng nó KHÔNG chỉ chứa `.env.example`:**

```bash
git diff --name-only refs/stash^ refs/stash | wc -l          # -> 103 file
git diff --name-only refs/stash^ refs/stash | grep -c '\.env' # -> 1
```

103 file, trong đó 102 file là source Java/Vue/test thật (`AdminService`, `JwtServiceTest`,
`GraphCanvas.vue`, `useSigma.ts`, …).

- [ ] **KHÔNG chạy `git stash drop` / `git update-ref -d refs/stash` cho đến khi đã đối chiếu
      103 file đó với working tree hiện tại.** Drop bây giờ có thể mất công việc thật.
      Việc này không liên quan tới rotate secret — để lại làm sau, riêng biệt.

### 1.3 Phần rotate — CHƯA LÀM

9 biến nhạy cảm, xác nhận có mặt trong `.env` (kiểm bằng grep chỉ lấy tên biến):

| # | Tên biến | Nhóm | Đã rotate? |
|---|---|---|---|
| 1 | `JWT_SECRET` | B — tự sinh | [ ] chưa |
| 2 | `POSTGRES_PASSWORD` | B — tự sinh | [ ] chưa |
| 3 | `NEO4J_PASSWORD` | B — tự sinh | [ ] chưa |
| 4 | `SUPABASE_DB_PASSWORD` | A — nhà cung cấp | [ ] chưa |
| 5 | `SUPABASE_MIGRATION_DB_PASSWORD` | A — nhà cung cấp | [ ] chưa |
| 6 | `GOOGLE_CLIENT_SECRET` | A — nhà cung cấp | [ ] chưa |
| 7 | `GITHUB_CLIENT_SECRET` | A — nhà cung cấp | [ ] chưa |
| 8 | `GEMINI_API_KEY` | A — nhà cung cấp | [ ] chưa |
| 9 | `GEMINI_API_KEYS` | A — nhà cung cấp | [ ] chưa |

### 1.4 Đính chính số lượng Gemini key: **7 key riêng biệt, không phải 9**

Đếm mà không in giá trị:

```bash
grep -E '^GEMINI_API_KEYS=' .env | sed 's/^[^=]*=//' | tr ',' '\n' \
  | sed 's/^ *//;s/ *$//' | grep -c .          # -> 8  (số phần tử thô)
grep -E '^GEMINI_API_KEYS=' .env | sed 's/^[^=]*=//' | tr ',' '\n' \
  | sed 's/^ *//;s/ *$//' | grep . | sort -u | wc -l   # -> 7  (sau khi dedup)
```

- `GEMINI_API_KEYS` có **8 phần tử nhưng chỉ 7 giá trị riêng biệt** (1 phần tử bị lặp).
- `GEMINI_API_KEY` (số ít) **trùng với 1 trong các key** của `GEMINI_API_KEYS`
  (kiểm bằng `grep -cF` giữa 2 biến → `1`).
- Union dedup của cả 2 biến = **7 key**.

=> Cần revoke **7 key Gemini**, không phải 9. Sau khi rotate nên dọn phần tử lặp luôn.

---

## 2. Vì sao vẫn phải rotate dù git đã sạch

Các giá trị này đã từng tồn tại trong git object của repo, trong file backup trên disk,
và trong nội dung hội thoại/chat trước đó. Xoá bản sao **không làm giá trị hết hiệu lực** —
một secret đã rời khỏi vùng kiểm soát thì phải coi như đã lộ vĩnh viễn, bất kể còn tìm
thấy bản sao nào hay không. Object DB sạch ở mục 1.1 chỉ chứng minh *repo local hiện tại*
không còn chứa chúng; nó không chứng minh được chưa ai từng đọc. Cách duy nhất khiến bản
lộ trở thành vô dụng là **đổi giá trị ở phía nhà cung cấp / phía hệ thống**.

---

## 3. Checklist rotate

### Nhóm A — phải vào tài khoản nhà cung cấp (làm TRƯỚC)

Đây là nhóm nguy hiểm thật: key còn sống là còn bị dùng để tiêu tiền hoặc chiếm phiên
đăng nhập của người dùng.

#### A1. Supabase — `SUPABASE_DB_PASSWORD` và `SUPABASE_MIGRATION_DB_PASSWORD`

- [ ] Đổi password DB chính (`SUPABASE_DB_PASSWORD`)
      → Supabase Dashboard → chọn project → **Project Settings → Database →
      Database password → Reset database password** `[chưa xác minh]`
- [ ] Đổi/tạo lại credential cho migration user (`SUPABASE_MIGRATION_DB_PASSWORD`).
      Nếu đây là một DB role riêng thì đổi bằng SQL editor (`ALTER ROLE ... WITH PASSWORD ...`),
      không dùng nút reset ở trên `[chưa xác minh]`
- [ ] Cập nhật cả `SUPABASE_DB_URL` / `SUPABASE_MIGRATION_DB_URL` nếu URL có nhúng password
- [ ] Ghi giá trị mới vào `.env` (chỉ sửa file, không echo ra terminal)

Lưu ý: `VIBEGRAPH_SUPABASE_ENABLED` mặc định `false` trong compose
(`docker-compose.yml:79`), nên nếu bạn chưa bật Supabase thì 2 biến này chưa được dùng
lúc chạy — nhưng **vẫn phải rotate**, vì giá trị đã lộ.

#### A2. Google OAuth — `GOOGLE_CLIENT_SECRET`

- [ ] Google Cloud Console → **APIs & Services → Credentials → OAuth 2.0 Client IDs**
      → chọn client tương ứng `GOOGLE_CLIENT_ID` → tạo secret mới, rồi **xoá secret cũ**
      `[chưa xác minh]`
- [ ] Ghi giá trị mới vào `.env`

#### A3. GitHub OAuth — `GITHUB_CLIENT_SECRET`

- [ ] GitHub → **Settings → Developer settings → OAuth Apps** → chọn app tương ứng
      `GITHUB_CLIENT_ID` → **Generate a new client secret**, rồi **Delete** secret cũ
      `[chưa xác minh]`
- [ ] Ghi giá trị mới vào `.env`

#### A4. Gemini — 7 key (`GEMINI_API_KEY` + `GEMINI_API_KEYS`)

> **Tạo key mới là KHÔNG ĐỦ.** Key cũ vẫn hoạt động và vẫn tính tiền vào project của bạn
> cho tới khi bị **revoke/delete**. Phải xoá từng key cũ.

- [ ] Google AI Studio → **API keys** (hoặc Google Cloud Console → APIs & Services →
      Credentials, nếu key được tạo ở đó) `[chưa xác minh]`
- [ ] Tạo đủ số key mới cần dùng
- [ ] **Xoá / revoke toàn bộ 7 key cũ** — đánh dấu từng key một:
      - [ ] key 1  - [ ] key 2  - [ ] key 3  - [ ] key 4
      - [ ] key 5  - [ ] key 6  - [ ] key 7
- [ ] Ghi lại `GEMINI_API_KEY` và `GEMINI_API_KEYS` trong `.env`;
      dọn phần tử trùng lặp trong `GEMINI_API_KEYS` (xem mục 1.4)
- [ ] Xác nhận số lượng sau khi sửa (không in giá trị):
      ```bash
      grep -E '^GEMINI_API_KEYS=' .env | sed 's/^[^=]*=//' | tr ',' '\n' \
        | sed 's/^ *//;s/ *$//' | grep . | sort -u | wc -l
      ```

### Nhóm B — tự sinh trên máy (làm SAU nhóm A)

Lệnh sinh dùng chung (OpenSSL đã có sẵn: `OpenSSL 3.2.1`, kiểm bằng `openssl version`):

```bash
openssl rand -base64 48
```

`base64` của 48 byte cho ra chuỗi 64 ký tự ASCII → thoả ngưỡng tối thiểu của `JWT_SECRET`.

#### B1. `JWT_SECRET`

- [ ] Sinh giá trị mới bằng `openssl rand -base64 48`
- [ ] Ghi vào `.env`

**Ràng buộc phải giữ:** app **fail-fast** nếu secret ngắn hơn 64 byte UTF-8.
Bằng chứng — `src/main/java/com/vibegraph/auth/service/JwtService.java`:

- dòng 39: `private static final int MIN_SECRET_BYTES = 64;`
- dòng 46: `if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {`
- dòng 47-50: ném `IllegalStateException` — backend sẽ không khởi động được.

(Tài liệu hoá thêm ở `src/main/java/com/vibegraph/auth/config/JwtProperties.java:12` và `:24`.)

**Hệ quả:** đổi `JWT_SECRET` làm **mọi access token và refresh token hiện có mất hiệu lực**
→ toàn bộ người dùng bị đăng xuất và phải đăng nhập lại. Đây là hệ quả mong muốn (bản lộ
cho phép tự ký token hợp lệ), nhưng hãy chọn thời điểm phù hợp.

#### B2. `POSTGRES_PASSWORD`

- [ ] Sinh giá trị mới
- [ ] Ghi vào `.env`

**Hệ quả:** biến này được truyền cho **cả 2 service** — container `postgres`
(`docker-compose.yml:14`) và `backend` (`docker-compose.yml:78`), và còn nằm trong
healthcheck của postgres (`docker-compose.yml:19` dùng `POSTGRES_USER`/`POSTGRES_DB`).
Giống Neo4j, Postgres chỉ đọc `POSTGRES_PASSWORD` **lúc initdb lần đầu**; volume
`vibegraph_postgres-data` đã init rồi nên đổi biến trong `.env` **không** đổi được password
của DB đang tồn tại. Cách đổi mà **không mất dữ liệu** — đổi ngay trong DB rồi mới sửa `.env`:

```bash
# đổi password thật trong Postgres (nhập ở prompt, KHÔNG truyền qua dòng lệnh)
docker compose exec postgres psql -U "$(grep -E '^POSTGRES_USER=' .env | sed 's/^[^=]*=//')" \
  -d postgres -c '\password'
```

- [ ] Đổi password trong DB trước
- [ ] Sau đó mới cập nhật `.env` cho khớp
- [ ] Recreate backend (xem mục 5)

#### B3. `NEO4J_PASSWORD` — LÀM CUỐI CÙNG, CÓ RỦI RO MẤT DỮ LIỆU

- [ ] Sinh giá trị mới
- [ ] Ghi vào `.env`
- [ ] Áp dụng theo mục 4 + mục 5 bên dưới

⚠️ **Cảnh báo mất dữ liệu.** Neo4j lưu credential trong data volume. Biến `NEO4J_AUTH`
chỉ được đọc **lúc khởi tạo container lần đầu** (`docker-compose.yml:34`:
`NEO4J_AUTH: ${NEO4J_USERNAME}/${NEO4J_PASSWORD}`). Volume `vibegraph_neo4j-data` đã init,
nên **đổi `NEO4J_PASSWORD` trong `.env` KHÔNG đổi được password của DB đã init** — backend
sẽ báo lỗi auth.

Cách xử lý theo `DEVOPS-GUIDE.md` mục *"Neo4j auth or password mismatch"* (dòng 270-287):

> 1. Xác nhận `.env` có `NEO4J_USERNAME` và `NEO4J_PASSWORD` khớp nhau.
> 2. Nếu Neo4j đã được khởi tạo với password cũ, phải tạo lại volume:
>
> ```bash
> docker compose down -v
> docker compose up -d --build
> ```
>
> Việc này xoá dữ liệu Neo4j local.

⚠️ **Bổ sung quan trọng mà guide không nói rõ:** `docker compose down -v` xoá **TẤT CẢ**
named volume của stack, không chỉ Neo4j. Kiểm bằng `docker volume ls`, các volume hiện có:

```
vibegraph_neo4j-data
vibegraph_neo4j-logs
vibegraph_postgres-data        <-- user, project, credit, audit log
vibegraph_upload-workspaces    <-- archive đã upload
```

Nghĩa là `down -v` cũng **xoá sạch Postgres** (toàn bộ tài khoản người dùng) và các
workspace upload. Nếu chỉ muốn reset Neo4j, dùng cách hẹp hơn:

```bash
docker compose stop neo4j
docker compose rm -f neo4j
docker volume rm vibegraph_neo4j-data vibegraph_neo4j-logs
docker compose up -d neo4j
docker compose up -d --force-recreate backend
```

- [ ] Đã quyết định: chấp nhận mất graph, phải **import + analyze lại toàn bộ project**
- [ ] Dùng cách hẹp (chỉ volume Neo4j), **không** dùng `down -v`, để giữ Postgres

---

## 4. Thứ tự thực hiện

```
1. Nhóm A trước — key còn sống là rủi ro thật (bị tiêu tiền, bị chiếm phiên)
   A1 Supabase  ->  A2 Google OAuth  ->  A3 GitHub OAuth  ->  A4 Gemini (7 key, PHẢI revoke)
2. Nhóm B sau — chỉ ảnh hưởng nội bộ stack
   B1 JWT_SECRET  ->  B2 POSTGRES_PASSWORD
3. B3 NEO4J_PASSWORD — LÀM CUỐI
   Lý do: đây là mục DUY NHẤT có thể mất dữ liệu. Để cuối để nếu phải dừng giữa đường
   thì mọi thứ nguy hiểm hơn đã được xử lý xong, và graph vẫn còn nguyên.
```

---

## 5. Lệnh verify sau mỗi bước

### 5.1 Biến nào thực sự tới container nào

Kiểm bằng `grep -c '<TÊN_BIẾN>' docker-compose.yml` (chỉ đếm, không in giá trị):

| Biến | Có trong `docker-compose.yml`? | Service nhận | Cần recreate |
|---|---|---|---|
| `POSTGRES_PASSWORD` | có — dòng 14, 78 | `postgres`, `backend` | cả hai |
| `NEO4J_PASSWORD` | có — dòng 34, 119 | `neo4j`, `backend` | cả hai |
| `JWT_SECRET` | có — dòng 106 | `backend` | `backend` |
| `SUPABASE_DB_PASSWORD` | có — dòng 82 | `backend` | `backend` |
| `SUPABASE_MIGRATION_DB_PASSWORD` | có — dòng 87 | `backend` | `backend` |
| `GOOGLE_CLIENT_SECRET` | **KHÔNG — grep = 0** | (không service nào) | — |
| `GITHUB_CLIENT_SECRET` | **KHÔNG — grep = 0** | (không service nào) | — |
| `GEMINI_API_KEY` | **KHÔNG — grep = 0** | (không service nào) | — |
| `GEMINI_API_KEYS` | **KHÔNG — grep = 0** | (không service nào) | — |

⚠️ **Phát hiện quan trọng ảnh hưởng cách verify.** `docker-compose.yml` **không** truyền
`GOOGLE_CLIENT_SECRET`, `GITHUB_CLIENT_SECRET`, `GEMINI_API_KEY`, `GEMINI_API_KEYS`,
`APP_BASE_URL` cho backend, và cũng **không** có `env_file:` nào
(`grep -n "env_file" docker-compose.yml` → rỗng). Comment ở `docker-compose.yml:137-139`
nói ".env mount is gone — the environment block above passes every variable the app needs",
nhưng thực tế 4 biến trên không có trong block đó.

Hệ quả cụ thể:

- Compose chạy profile `docker` (`docker-compose.yml:71`), profile này không override phần
  oauth (`grep oauth2 src/main/resources/application-docker.yaml` → rỗng), nên nó thừa
  hưởng default của `application.yaml`:
  - dòng 51: `client-secret: ${GOOGLE_CLIENT_SECRET:__disabled__}`
  - dòng 59: `client-secret: ${GITHUB_CLIENT_SECRET:__disabled__}`
- Nghĩa là **Google/GitHub OAuth đang bị vô hiệu trong stack Docker** và fail *im lặng*
  (rơi về giá trị `__disabled__`, không crash).
- Vì vậy: **không thể verify việc rotate OAuth/Gemini chỉ bằng `docker compose`.** Muốn
  verify phải chạy backend local (`./mvnw spring-boot:run`, xem `scripts/dev-up.ps1:62-68`)
  với biến được nạp vào shell, **hoặc** thêm 4 biến đó vào block `environment:` của service
  `backend`.
- Đối chiếu: `application-prod.yaml:39` và `:47` dùng `${GOOGLE_CLIENT_SECRET}` /
  `${GITHUB_CLIENT_SECRET}` **không có default** → profile `prod` sẽ fail-fast nếu thiếu.

- [ ] Đã quyết định sẽ verify OAuth/Gemini theo cách nào (local dev, hay thêm biến vào compose)

### 5.2 Sau khi cập nhật `.env` — recreate và kiểm health

```bash
# 0) sanity check: chỉ liệt kê TÊN biến, không in giá trị
grep -oE '^[A-Z_0-9]+=' .env | tr -d '='

# 1) compose đọc được .env, không thiếu biến bắt buộc (:? sẽ báo lỗi nếu thiếu)
docker compose config --quiet && echo "compose OK"

# 2) recreate service liên quan (tra bảng 5.1)
docker compose up -d --force-recreate backend

# 3) health — /actuator/health là endpoint public
#    (SecurityConfig.java:177 permit; :185 để /actuator/** còn lại cho ADMIN)
#    SERVER_PORT=8080 trong .env, map "${SERVER_PORT}:8080" ở docker-compose.yml:64
curl -s localhost:8080/actuator/health

# 4) nếu không UP thì đọc log (log có thể chứa thông tin cấu hình — đừng dán ra ngoài)
docker compose logs --tail=80 backend
```

Kỳ vọng ở bước 3: JSON có `"status":"UP"`.

- [ ] `docker compose config --quiet` không báo lỗi
- [ ] `/actuator/health` trả `UP`

### 5.3 Verify riêng cho từng biến

| Sau khi rotate | Lệnh verify |
|---|---|
| `JWT_SECRET` | `docker compose up -d --force-recreate backend` → `curl -s localhost:8080/actuator/health`. Nếu secret < 64 byte, backend **không khởi động** và log có `must be at least 64 UTF-8 bytes` (JwtService.java:46-50). Sau đó: đăng nhập lại (phiên cũ đã mất hiệu lực). |
| `POSTGRES_PASSWORD` | `docker compose ps` → `postgres` phải `healthy` (healthcheck `pg_isready`, dòng 18-19). Rồi `docker compose up -d --force-recreate backend` + health check. |
| `NEO4J_PASSWORD` | `docker compose ps` → `neo4j` `healthy` (healthcheck `wget localhost:7474`, dòng 48-52); rồi backend `healthy`. |
| `SUPABASE_*_PASSWORD` | Nếu `VIBEGRAPH_SUPABASE_ENABLED=true`: recreate backend + đọc log xem có lỗi kết nối Supabase. Nếu `false` (mặc định): chỉ cần backend `UP`. |
| `GOOGLE_CLIENT_SECRET` / `GITHUB_CLIENT_SECRET` | **Không verify được qua Docker** (mục 5.1). Chạy local dev rồi thử đăng nhập thật. |
| `GEMINI_API_KEY` / `GEMINI_API_KEYS` | **Không verify được qua Docker** (mục 5.1). Chạy local dev rồi gọi 1 chức năng dùng LLM (`VIBEGRAPH_USECASE_LLM_ENABLED`). |

---

## 6. Kiểm tra cuối

- [ ] Đăng nhập bằng **Google** thành công (bằng đường chạy đã chọn ở 5.1)
- [ ] Đăng nhập bằng **GitHub** thành công
- [ ] Import 1 project nhỏ, chạy analyze xong, graph hiển thị được
- [ ] Toàn bộ stack healthy:
      ```bash
      docker compose ps
      ```
      Kỳ vọng `postgres`, `neo4j`, `backend`, `frontend` đều `Up ... (healthy)`
      (baseline lúc viết tài liệu: 3 service `postgres`/`neo4j`/`backend` đang `(healthy)`)
- [ ] Quét lại object DB, xác nhận vẫn sạch (chỉ in số đếm, không in giá trị):
      ```bash
      git cat-file --batch-all-objects --batch | grep -c 'GOCSPX-'   # kỳ vọng 0
      git cat-file --batch-all-objects --batch | grep -c 'AIzaSy'    # kỳ vọng 0
      git count-objects -v                                          # kỳ vọng count: 0, garbage: 0
      git log --all --oneline -- .env                                # kỳ vọng rỗng
      ```
- [ ] `.env` vẫn nằm trong `.gitignore` và **không** xuất hiện trong `git status`:
      ```bash
      git check-ignore -v .env
      git status --porcelain | grep -E '(^|/)\.env$' || echo "OK: .env khong nam trong staging/untracked"
      ```
- [ ] Đã ghi lại ngày rotate cho từng key ở nhóm A (để đối chiếu billing về sau)

---

## Phụ lục — danh sách lệnh đã dùng để xác minh tài liệu này

```bash
git stash list
git rev-parse --verify refs/stash
git reflog show refs/stash
git cat-file -t 388632b
git count-objects -v
git log --all --oneline -- .env
git remote -v
git cat-file --batch-all-objects --batch-check | wc -l
git cat-file --batch-all-objects --batch | grep -c 'GOCSPX-'
git cat-file --batch-all-objects --batch | grep -c 'AIzaSy'
git ls-tree -r --name-only refs/stash | grep -E '(^|/)\.env'
git show refs/stash:.env.example | grep -c '<pattern>'
git diff --name-only refs/stash^ refs/stash | wc -l
grep -oE '^[A-Z_0-9]+=' .env | tr -d '='
grep -E '^GEMINI_API_KEYS=' .env | sed 's/^[^=]*=//' | tr ',' '\n' | grep -c .
grep -E '^GEMINI_API_KEYS=' .env | sed 's/^[^=]*=//' | tr ',' '\n' | sort -u | wc -l
grep -n "64\|MIN_SECRET" src/main/java/com/vibegraph/auth/service/JwtService.java
grep -n "Neo4j auth" DEVOPS-GUIDE.md
grep -nE 'NEO4J|POSTGRES|JWT_SECRET|volumes:|healthcheck:' docker-compose.yml
grep -c 'GOOGLE_CLIENT_SECRET' docker-compose.yml
grep -n "env_file" docker-compose.yml
grep -rn "client-secret" src/main/resources/*.yaml
grep -n "actuator" src/main/java/com/vibegraph/auth/config/SecurityConfig.java
docker volume ls
docker compose ps
openssl version
```
