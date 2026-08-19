# VibeGraph × Grapuco — Hồ sơ triển khai layout (d3-force + ngraph + Sigma)

> Folder này là **single source of truth** để BẤT KỲ model nào đọc vào cũng hiểu và triển khai được kiến trúc layout mới của VibeGraph, đồng bộ với cơ chế đã đo từ grapuco.
> **Nguyên tắc bắt buộc**: mọi khẳng định trong folder này đều kèm nguồn bằng chứng (file artifact, số đo devtools, hoặc trích bundle). Không đoán mò. Chỗ nào chưa đo thì ghi rõ `⚠️ CHƯA ĐO`.

## Cách đọc (theo thứ tự)

| # | File | Nội dung |
|---|---|---|
| 1 | [01-EVIDENCE.md](./01-EVIDENCE.md) | Toàn bộ sự thật đã đo: tham số grapuco (trích bundle), số đo mật độ từ dump, số đo VibeGraph qua từng cấu hình, danh sách các lần THẤT BẠI kèm nguyên nhân gốc |
| 2 | [02-ARCHITECTURE.md](./02-ARCHITECTURE.md) | Đặc tả kiến trúc CUỐI: stack, worker protocol, hằng số, config keys, ma trận hành vi theo chế độ, file cần tạo/sửa, thứ tự code |
| 3 | [03-ROLLOUT.md](./03-ROLLOUT.md) | Lộ trình 4 pha + tiêu chí nghiệm thu bằng số + protocol kiểm chứng devtools + danh sách KHÔNG ĐƯỢC LÀM + chính sách commit/rollback |

## Tóm tắt 1 phút

- Grapuco KHÔNG precompute tọa độ; layout chạy 100% client bằng **d3-force** với recipe cố định (charge −3000/dMax 5000, link 150, collide `max(3·val,10)+100`, center 0.02, alphaDecay 0.02, velocityDecay 0.3, **300 tick đồng bộ rồi ghim fx/fy**). ngraph trong bundle của họ đóng vai cấu trúc dữ liệu; demo vật lý cho thấy hybrid *ngraph macro + d3 collide* là cấu hình bung khỏe + nhẹ.
- VibeGraph hiện tại (baseline `backup-full-fixed-20260728` @ `119da9b`) dùng FA2 + normalize/spread/density/settle/noverlap — hoạt động tốt cho dự án lớn nhưng **vón cục ở dự án nhỏ** vì FA2 không có collide trong sim và normalize phá bảo chứng khoảng hở.
- Kiến trúc mới: **Sigma+graphology giữ render/data-model**; layout = worker headless `ngraph.forcelayout 300 bước → d3 forceCollide(+100) 300 tick tuần tự → ghim`; size node **graph-units** qua `itemSizesReference:'positions'`; **bỏ** normalize/spread/density/settle/noverlap ở chế độ mới; công tắc `VITE_LAYOUT_ENGINE=fa2|d3` (fa2 đóng băng, chỉ rollback) và `VITE_LAYOUT_MACRO=ngraph|d3` (A/B).

## Trạng thái hiện tại của code

- **ĐÃ TRIỂN KHAI (P1–P3)**: engine `d3` là **default** cho mọi dự án (công tắc rollback
  `VITE_LAYOUT_ENGINE=fa2`); macro default `d3` (`VITE_LAYOUT_MACRO=ngraph` để A/B).
- Worker `src/lib/layout/d3LayoutWorker.ts` + `layoutClient.ts` đã nối dây; size graph-units
  qua `itemSizesReference:'positions'`; fa2-mode đóng băng nguyên trạng.
- Bằng chứng hình dạng + số liệu: [04-RESULTS.md](./04-RESULTS.md). Suite 579/579, vue-tsc clean.
