# 01 — BẰNG CHỨNG (mọi khẳng định đều có nguồn)

## 1. Grapuco: tham số layout thật (trích bundle production `0qy4-68qybnav.js`)

Nguồn: `.vibegraph/grapuco-spx-density-report.json` + đối chiếu trực tiếp bundle trong phiên 2026-08-19.

```js
forceSimulation(nodes)
  .force("charge",    forceManyBody().strength(-3000).distanceMax(5000))
  .force("collision", forceCollide(n => Math.max(3*(n.val||8),10) + 100))
  .force("link",      forceLink(links).id(d=>d.id).distance(150))
  .force("center",    forceCenter(0,0).strength(0.02))
  .alphaDecay(0.02).velocityDecay(0.3).stop();
for (i<300) sim.tick();   // đồng bộ, rồi GHIM fx/fy toàn bộ node → graph tĩnh
```

## 2. Grapuco: backend KHÔNG precompute tọa độ

Nguồn: `.vibegraph/grapuco-spx-full-dump.json` (612 KB, repo SPX_Tracking, 401 nodes / 1039 edges).
Kiểm tra tự động: **0/401 node có x/y/position** → `backendPrecomputesCoordinates: false`.
Endpoint `graph/architecture` chỉ trả metadata: id, label, name, description, filePath, startLine/endLine, language, val.

## 3. Grapuco: mật độ thực tế (đo từ vị trí đang render, repo SPX)

Nguồn: `.vibegraph/grapuco-spx-density-report.json`.

| Đại lượng | Giá trị |
|---|---|
| bounding box | 13 115 × 16 025 đơn vị |
| khoảng cách node gần nhất | min 247 · mean 272 · median 260 (p10 248 / p90 298) |
| bán kính vẽ | 24–60 |
| bán kính collision | trung bình 135 (= vẽ + 100) |
| overlap thị giác | **0 cặp** |
| edge length | mean 728 · median 573 (≫ link 150 → collide thắng) |
| vi phạm bán kính collide sau 300 tick | 432 cặp, nhưng KHÔNG cặp nào thành overlap thị giác nhờ pad +100 |

Kết luận: **padding collide CỐ ĐỊNH +100 (≈4× bán kính vẽ)** là thứ đảm bảo 0 overlap ở mọi cỡ dự án — không phải density-adaptive sizing.

## 4. Grapuco: stack thư viện (bundle scan)

Nguồn: `update/graph/SessionChatClaude.md` (Claude quét 32 chunk `/_next/`): `force-graph` ×5, `ngraph` ×2, `forceCollide` ×4; **0** kết quả cho sigma/graphology/forceatlas2/cytoscape/cosmograph.
Vai trò (đối chiếu demo vật lý + code đo được): d3-force = layout; ngraph = cấu trúc dữ liệu graph; force-graph = render.
Demo vật lý (Gemini canvas, ảnh chụp trong phiên): (1) D3 thuần không collide → overlap; (2) D3+forceCollide → 0 overlap; (3) NGraph repulsion mạnh → tách cụm nhẹ CPU; (4) **Hybrid NGraph + D3 Collide** → khung xương nhanh + tách đè chắc.

## 5. VibeGraph baseline (branch `backup-full-fixed-20260728` @ `119da9b`) — số đo devtools

Project lớn `980ae531` (fatc, 1 512 node hiển thị, cửa sổ devtools 1440×769, canvas 984×612):

| Cấu hình | touching | size med | Ghi chú |
|---|---|---|---|
| coverage 0.45 + iter 20 (BẢN DUYỆT) | 8 | 2.7 | thoáng, zoom 2×/6.7× = 0 touching |
| iter 30 | 3 | 2.7 | bắt đầu "sát đều" |
| iter 35 | 0 | 2.7 | sát đều hơn nữa (người dùng chê đặc) |
| zoom-out tối đa (ratio 4, clamp) | 2 784 | 1.0 | cố hữu: 1 512 node trong 138px — overview chỉ để nắm toàn cảnh |

Project nhỏ `424bff0c` (SPX, 229 node):

| Cấu hình | touching | nn med | Hình |
|---|---|---|---|
| baseline (iter 20) | 282 | 9.85 | **vón cục tròn**, core ~200px |
| + adaptive settle 150 lượt | 0–1 | 11.5 | hết đè nhưng VẪN tròn (settle không kéo dài edges) |
| + FA2 scalingRatio 60000 | 1 | 11.5 | **không đổi hình** → chứng minh FA2 không bung được kiểu grapuco (normalize scale lại toàn bộ) |

## 6. Các lần THẤT BẠI trước và nguyên nhân gốc (để không lặp lại)

Bản ngraph+collide đầu tiên (branch feat, commit c439409 era) hỏng (blob/tròn) vì:
1. Collide chạy TRƯỚC `normalizeLayout` → normalize rescale → bảo chứng khoảng hở bị phá. (Cơ chế: xem `update/graph/02-SIGMA-INTERNALS.md` + `03-ROOT-CAUSE.md` — bẫy scale-invariance: giãn hậu kỳ bị camera fit chiết khấu.)
2. Bán kính collide tính từ bbox "sống" mỗi 10 tick → vòng phản hồi → nở tròn đều.
3. Collide song song/đan xen với springs ngraph trong cùng loop → springs kéo co nhanh hơn collide nở.
4. d3-forceCollide **cache bán kính lúc initialize** — đổi `node.r` mà không gọi lại `force.radius(accessor)` thì vô tác dụng.

Các bẫy khác đã ghi nhận:
- `itemSizesReference:'positions'` KHÔNG phải cần gạt zoom (chỉ nhân hằng số K); cần gạt zoom là `zoomToSizeRatioFunction`. Với giá trị graph-units, `'positions'` lại là chế độ ĐÚNG để render graph-space size.
- Post-hoc de-overlap KHÔNG tạo được diện tích ròng sau camera fit (scale-invariance) → chỉ là "trang điểm".
- FA2 không có ràng buộc va chạm → core dự án nhỏ đặc hơn khoảng hở; tune repulsion FA2 vô ích vì normalize scale lại.

## 7. Hệ quả thiết kế (chốt)

- Chống đè phải là **ràng buộc TRONG simulation**, bán kính **hằng số** `max(3·val,10)+100`, **cùng hệ đơn vị** với bán kính vẽ, và **không có bước normalize nào sau đó**.
- Macro engine: ngraph (hybrid, mặc định) hoặc d3 4 lực (A/B) — cả hai đều ổn vì collide mới là bảo chứng.
- Render giữ Sigma+graphology; size graph-units qua `'positions'`; zoom curve/clamp/label/ghost giữ nguyên.
