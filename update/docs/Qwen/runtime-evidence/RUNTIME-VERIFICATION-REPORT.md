# Báo cáo kiểm thử runtime trên Chrome

| Test | Kết luận | Bằng chứng (file ảnh/log) | Số liệu đo được |
|---|---|---|---|
| T1 | CONFIRMED | `T1-landing.png`, `T1-login.png`, `T1-network.txt` | Landing tải 117 module/script, khoảng 4.17 MB transfer. Có `sigma.js` 160,987 B, `graphology.js` 153,901 B, `GraphCanvas.vue` 125,697 B, hai worker ForceAtlas2/NoOverlap và `GraphView.vue`. `/login` vẫn có cùng graph stack. |
| T2 | CONFIRMED | `T2-cookie-flags.txt` | `vg_session`: HttpOnly, SameSite=Lax, Secure vắng mặt. `vg_refresh`: HttpOnly, SameSite=Lax, Secure vắng mặt. Giá trị cookie không được lưu vào artifact. |
| T3 | BLOCKED | `T3-blocked.txt`, `T3-import-attempts.txt` | `octocat/Hello-World` trả 400; `spring-guides/gs-rest-service` trả 422 với `GITHUB_IMPORT_ERROR: Failed to contact GitHub: request timed out`. Không import nào đạt trạng thái ANALYZING nên không thể quan sát polling sau unmount. |
| T4 | CONFIRMED | `T4-admin-users-offline.png`, `T4-console.txt` | Khi Network Offline rồi bấm Search: UI vẫn giữ bảng cũ, không có thông báo lỗi; Console có `ERR_INTERNET_DISCONNECTED`, Vue warning tại `UsersTableView`, và `Uncaught (in promise)`. |
| T5 | NOT REPRODUCED | `T5-api-key-timing.png`, `T5-timing.txt` | Không cookie, 30 request: NO-KEY trung bình 3.2 ms; FAKE-KEY 3.0 ms; delta -0.2 ms. Không thấy chi phí bcrypt >=50 ms. Fake key nhận 401 x22 và 429 x8. |
| T6 | CONFIRMED | `T6-large-source.png`, `T6-large-source.txt` | File text tạm 200 MiB, 3,883,615 dòng. Endpoint chỉ trả 300 dòng/16,769 ký tự trong 915.2 ms nhưng memory container tăng từ 680.6 MiB lên 892.2 MiB (khoảng +211.6 MiB). Không có 500/OOM. |
| T7 | CONFIRMED | `T7-graph-no-cap.png`, `T7-graph.txt` | Hai request graph dùng `nodeLimit=0&edgeLimit=0`; thời gian khoảng 576.8/559.3 ms. UI hiển thị khoảng 2,405 node theo legend, project báo 2,495 node, không có cảnh báo cap/truncation. |
| T8 | NOT REPRODUCED | `T8-cors.png`, `T8-cors.txt` | Request có `Origin: https://evil.example` trả 200 nhưng `Access-Control-Allow-Origin=null` và `Access-Control-Allow-Credentials=null`; không có wildcard `*`. |

Ghi chú môi trường: Chrome 151.0.7922.110; frontend `http://localhost:5173`; backend `http://localhost:8080`; tài khoản dùng: `admin@vibegraph.com` và `user@vibegraph.com` (không lưu mật khẩu trong báo cáo). Backend vẫn `UP` sau kiểm thử.

File tạm của T6 vẫn được giữ lại theo yêu cầu không tự dọn `/uploads`:

`/uploads/github-04e0b065-39f6-484b-bc84-7bf25f8b2704/source/ThinhChauTran263-fatc-Grocery-Store-ce1c762/runtime-t6-large.txt`

Tóm tắt: runtime xác nhận H12/T1 (landing tải graph stack), H4/T2 (cookie HTTP thiếu Secure), H11/T4 (Users không xử lý lỗi API), H14/T6 (đọc toàn file lớn trước khi cắt), và B-M10/T7 (graph không áp cap). H13/T5 và B-L5/T8 không tái hiện trong môi trường hiện tại. H10/T3 bị chặn vì backend không liên hệ được GitHub để tạo một import đang phân tích.
