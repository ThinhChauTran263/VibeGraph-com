# 02 — ĐẶC TẢ KIẾN TRÚC CUỐI

## 1. Stack

| Tầng | Thư viện | Vai trò | Ghi chú |
|---|---|---|---|
| Render | **Sigma** (WebGL) | vẽ node/edge, camera, zoom curve, clamp, label, ghost/focus | giữ nguyên toàn bộ UX hiện tại |
| Data model | **graphology** | container node/edge + attributes cho Sigma | bắt buộc (Sigma ăn graphology); KHÔNG dùng cho layout |
| Layout macro | **ngraph.forcelayout** (mặc định) hoặc **d3-force 4 lực** (slot A/B) | bung khung xương | chạy trong Web Worker |
| Chống đè | **d3-force `forceCollide`** | ràng buộc cứng trong sim, pad +100 cố định | chạy TUẦN TỰ sau macro |

Khác grapuco đúng 1 điểm: render Sigma thay force-graph (và graphology thay ngraph.graph).

## 2. Hằng số layout (CỐ ĐỊNH, trích từ bundle grapuco — không tự chế)

```
CHARGE_STRENGTH   = -3000        CHARGE_DISTANCE_MAX = 5000
LINK_DISTANCE      = 150          CENTER_STRENGTH     = 0.02
ALPHA_DECAY        = 0.02         VELOCITY_DECAY      = 0.3
TICKS              = 300 (đồng bộ, rồi ghim fx/fy)
drawRadius(n)      = max(3 * (n.val || 8), 10)      // graph-units
collideRadius(n)   = drawRadius(n) + 100            // pad CỐ ĐỊNH
```

`val` = size cơ bản theo loại node hiện tại (`NODE_SIZE_BY_TYPE`, 4–14) — chính là attribute `size` mà graphAdapter gán ở baseline.

## 3. Worker `src/lib/layout/d3LayoutWorker.ts` (đã viết, chưa nối dây)

Protocol:
```
main → worker: { type:'init',  nodes:[{id,x,y,val}], links:[{source,target}],
                 macro:'ngraph'|'d3' }
             | { type:'stop' }
worker → main: { type:'done', xs:number[], ys:number[] }   // sau 300 tick + ghim
```

Luồng `macro:'ngraph'` (mặc định — hybrid):
1. `ngraph.forcelayout` (timeStep 0.08, springLength 150, springCoefficient 0.0008, dragCoeff 0.02, gravity −1.2, theta 0.8) — 300 bước.
2. d3 `forceSimulation` CHỈ `forceCollide(collideRadius)` — 300 tick, alphaDecay 0.02, velocityDecay 0.3. **Tuần tự, không đan xen** (sửa lỗi thất bại #3).
3. Ghim `fx/fy`, post `done` 1 lần. Không post tiến độ liên tục (graph tĩnh như grapuco).

Luồng `macro:'d3'`: 1 simulation đủ 4 lực đúng recipe grapuco, 300 tick, ghim, post.

Bán kính là **hằng số theo val**, set 1 lần qua accessor (không đổi giữa chừng → miễn nhiễm lỗi cache #4; không đọc bbox sống → miễn nhiễm #2). **Không có normalize/spread nào trong hoặc sau worker** → miễn nhiễm #1.

## 4. Client `src/lib/layout/layoutClient.ts` (sẽ tạo)

`createLayoutEngine(graph, { onDone })` trả `{ start(), kill() }`:
- Đọc `LAYOUT_ENGINE` (`'fa2' | 'd3'`, env `VITE_LAYOUT_ENGINE`, default **`d3`** sau khi lật pha 3; trong pha 1–2 default `fa2`).
- `'fa2'`: giữ NGUYÊN code FA2 worker hiện tại (đóng băng, không chỉnh nữa) + pipeline cũ.
- `'d3'`: spawn `d3LayoutWorker`; seed x/y deterministic (hash id, bán kính 500 — dùng lại `seededPosition` của graphAdapter); `val = size attribute`; links từ `graph.edges()` + `extremities()`.
- `onDone(xs, ys)`: ghi x/y vào graphology → `refresh()` FULL → `onLayoutSettled()` (GraphCanvas zoomToFit).

## 5. useSigma (sẽ sửa) — ma trận hành vi theo chế độ

| Thành phần | `fa2` (đóng băng) | `d3` (mới) |
|---|---|---|
| startLayout | FA2 worker + timer 8s | layoutClient d3 |
| normalize / spread / center | chạy | **BỎ** |
| density sizing (`applyDensitySizeScale`) | chạy | **BỎ** |
| settle + noverlap | chạy | **BỎ** |
| `itemSizesReference` | `'screen'` | **`'positions'`** |
| node `size` attribute | px (density-scaled) | **graph-units** `max(3·val,10)` (set ở init khi engine d3) |
| zoom curve / clamp 4 & 0.01 / label / ghost / positionCache / QA hook | giữ | giữ |

## 6. runtimeConfig (sẽ thêm)

```
LAYOUT_ENGINE: 'fa2'|'d3'   ← VITE_LAYOUT_ENGINE   (default 'fa2' pha 1–2; 'd3' pha 3)
LAYOUT_MACRO:  'ngraph'|'d3'← VITE_LAYOUT_MACRO    (default 'ngraph')
```
Các knob FA2/noverlap/settle/density GIỮ NGUYÊN (chế độ fa2 dùng), không chỉnh thêm.

## 7. File cần tạo / sửa (thứ tự code)

1. `src/lib/layout/d3LayoutWorker.ts` — ✅ đã có.
2. `src/lib/layout/layoutClient.ts` — tạo mới (fa2 path = move nguyên khối FA2 hiện tại từ useSigma).
3. `src/composables/useSigma.ts` — startLayout/stopLayout qua layoutClient; gate post-pass theo engine; `itemSizesReference` theo engine; set size graph-units khi d3; bỏ gọi density khi d3.
4. `src/lib/runtimeConfig.ts` — thêm 2 knob công tắc.
5. Specs: `layoutClient.spec.ts` (MockWorker: init payload, done→ghi x/y+size, kill), giữ `useSigma.spec.ts` green (default fa2).
6. `npm uninstall` KHÔNG làm ở pha này (fa2 còn sống sau công tắc).

## 8. Kỳ vọng định lượng (để nghiệm thu, so với bằng chứng §01)

- Dự án nhỏ SPX: 0 cặp đè thị giác; bbox thưa cùng bậc grapuco (≥ ~10 000 đơn vị cho ~230–400 node); nn median ≥ ~200 đơn vị.
- Dự án lớn fatc: 0 cặp đè; không blob tròn; node fit nhỏ tự nhiên (graph-units / span lớn) giống fit của grapuco.
- Cả hai chế độ zoom: vào thì tách + to đọc được; ra thì chấm li ti, clamp 4×.
- Thời gian layout ≤ ~2 s (300 tick worker), không pha "Finalizing" 22 s.
