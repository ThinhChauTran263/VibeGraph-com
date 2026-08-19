# 03 — LỘ TRÌNH TRIỂN KHAI + NGHIỆM THU + KỶ LUẬT

## Nguyên tắc bất biến (đọc lại trước MỌI pha)

1. **Không đoán mò / không bịa số**: mọi kết luận phải kèm số đo devtools (QA hook `window.__vibegraph_qa`) hoặc ảnh chụp; chỗ chưa đo ghi `⚠️ CHƯA ĐO`.
2. **Không chỉnh pipeline cũ (fa2)**: nó đóng băng làm rollback; mọi thay đổi chỉ nằm sau nhánh `d3`.
3. **Không thêm bước normalize/spread/density/settle/noverlap nào vào luồng d3**.
4. Mỗi pha = 1 commit kèm bảng số liệu; push chỉ khi người dùng bảo; trước push phải qua cửa security-scan (L3 gate theo quy trình).
5. Host-only: backend :8080 + vite :5173 chạy trực tiếp, KHÔNG docker.

## Protocol kiểm chứng devtools (dùng lại nguyên khối)

```js
// trong evaluate_script sau khi graph settle:
const { sigma, graph } = window.__vibegraph_qa
// ppu = px per graph-unit tại camera hiện tại:
const a = sigma.graphToViewport({x:0,y:0}), b = sigma.graphToViewport({x:1,y:0})
const ppu = Math.hypot(b.x-a.x, b.y-a.y)
// với engine d3, size attribute = graph-units → bán kính vẽ px = size * ppu * curve(zoom)
// touching = số cặp có dist_px < rA_px + rB_px
// bbox = max-min x/y của node visible; nn = nearest-neighbor distance
```
Chụp ảnh fit + zoom 2×/6.7× mỗi lần đo. Đợi settle: engine d3 ≈ ≤2 s (worker 300 tick); fa2 ≈ 8–30 s.

## PHA 1 — Nối dây công tắc (default `fa2`, rủi ro 0)

Bước:
1. `runtimeConfig.ts`: thêm `LAYOUT_ENGINE` (default `'fa2'`), `LAYOUT_MACRO` (default `'ngraph'`).
2. Tạo `layoutClient.ts`: fa2 path = move NGUYÊN khối FA2 hiện tại từ useSigma (không đổi một dòng); d3 path = spawn `d3LayoutWorker`, onDone ghi x/y.
3. `useSigma.ts`: startLayout/stopLayout qua layoutClient; khi `d3`: `itemSizesReference:'positions'`, set `size = max(3·val,10)` ở init, BỎ density/normalize/spread/settle/noverlap; khi `fa2`: giữ 100% cũ.
4. Specs: thêm `layoutClient.spec.ts` (MockWorker); `useSigma.spec.ts` giữ green.
5. `npx vitest run` + `vue-tsc --build` green. Commit `feat(graph): layout engine switch fa2|d3 (fa2 default, frozen) [QWEN-P1]`.

Nghiệm thu pha 1: devtools cả 2 dự án ở `fa2` cho số đo KHÁC BIỆT KHÔNG ĐÁNG KỂ so với bảng §01-EVIDENCE §5 (touching 8 / 282±).

## PHA 2 — Bật `d3`, đo A/B 2 macro

1. `.env`: `VITE_LAYOUT_ENGINE=d3` (macro default ngraph).
2. Đo SPX + fatc: touching (kỳ vọng 0), bbox (SPX ≥ ~10 000 đơn vị), nn median (≥ ~200), thời gian settle (≤ ~2 s), ảnh fit/zoom.
3. Đổi `VITE_LAYOUT_MACRO=d3`, đo lại SPX, so bảng: macro nào thưa + đều hơn thì đề xuất làm default.
4. So fa2 vs d3 trên fatc cho người dùng thấy khác biệt look.
5. Commit `feat(graph): d3 layout mode (ngraph macro + d3 collide) [QWEN-P2]` kèm bảng đo.

Nghiệm thu pha 2 (cửa DỪNG nếu không đạt):
- SPX: touching = 0 VÀ bbox ≥ 10 000 VÀ ảnh không còn cục tròn đặc.
- fatc: touching = 0 VÀ không blob.
- Nếu macro ngraph không đạt mà macro d3 đạt → default macro = d3 (vẫn đúng recipe grapuco).
- Nếu CẢ HAI không đạt → DỪNG, ghi `⚠️ CHƯA ĐO ĐẠT` + nguyên nhân đo được, KHÔNG tự chế thêm lực/tham số ngoài recipe.

## PHA 3 — Lật default `d3` cho mọi dự án

1. Code default `LAYOUT_ENGINE='d3'` (fa2 vẫn sau công tắc, đóng băng).
2. Người dùng duyệt ảnh 2 dự án × fit/zoom.
3. Commit `feat(graph): d3 layout default for all project sizes [QWEN-P3]`.

## PHA 4 — Đóng gói bằng chứng

1. Append bảng số liệu cuối + ảnh vào `update/graph/qwen/04-RESULTS.md` (file tạo ở pha 2).
2. Push khi người dùng bảo; trước push chạy cửa security-scan (L3 offer).

## Rollback (mọi thời điểm)

- Runtime: `VITE_LAYOUT_ENGINE=fa2` (1 dòng, vite tự restart).
- Code: revert commit pha 3 (fa2 path chưa từng bị sửa).

## Danh sách KHÔNG ĐƯỢC LÀM (bẫy đã trả giá)

- ❌ normalize/spread/density/settle/noverlap trong hoặc sau luồng d3.
- ❌ Bán kính collide đổi giữa chừng / đọc bbox sống / quên `.radius()` khi đổi accessor.
- ❌ Collide đan xen song song với macro trong cùng loop.
- ❌ Tự chế hằng số ngoài recipe §02 (charge/link/center/alpha/velocity/ticks/pad).
- ❌ Chỉnh bất kỳ thứ gì của fa2-mode.
- ❌ Commit không kèm số liệu; push không qua scan gate; báo cáo số không có nguồn đo.
