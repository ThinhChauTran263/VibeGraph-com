# projects/

Thư mục chứa **source code Java cần phân tích** bằng VibeGraph.

## Cách dùng

1. Copy project Java muốn phân tích vào đây, ví dụ:
   ```
   projects/
   └── sample-java/
       ├── pom.xml
       └── src/...
   ```

2. Docker mount thư mục này vào container backend (chế độ read-only):
   ```
   ./projects  ->  /projects
   ```
   (xem `docker-compose.yml`, service `backend`)

3. Khi đăng ký project qua API, dùng `rootPath` trỏ vào đường dẫn bên trong container:
   ```powershell
   $body = @{ name = "sample-java"; rootPath = "/projects/sample-java" } | ConvertTo-Json
   Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/projects -ContentType "application/json" -Body $body
   ```

## Ghi chú

- Nếu chạy backend **local** (ngoài Docker), `rootPath` có thể là đường dẫn thật trên máy,
  ví dụ `D:\Users\User\IdeaProjects\SomeJavaProject` — không bắt buộc phải nằm trong thư mục này.
- File `.gitkeep` chỉ để Git giữ lại thư mục rỗng.
