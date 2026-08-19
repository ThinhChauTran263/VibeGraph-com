# 04 — KẾT QUẢ ĐO PHA 2 (bằng chứng hình dạng + số liệu)

Nguồn đo: chrome-devtools (`window.__vibegraph_qa`), cửa sổ 1440×769, canvas ~1050×770,
engine `d3` (`VITE_LAYOUT_ENGINE=d3`), 2026-08-19. Ảnh chụp kèm phiên (fit + zoom 6.7×).

## 1. Dự án nhỏ SPX_Tracking (229 node hiển thị)

| Macro | touching | bbox (đơn vị) | nn median | Hình dạng (ảnh) |
|---|---|---|---|---|
| `ngraph` | 0 | 7 712 × 6 633 | 19.2px | ❌ "mạng nhện": core rối + vài spoke dài vắt ngang, không ra cụm |
| **`d3`** | **0** | **14 474 × 12 392** | 10.5px | ✅ cụm bung đều, cùng bậc với grapuco (13 115 × 16 025 @401 node) |

→ Chọn **macro `d3` làm default** (đổi trong `runtimeConfig.ts`).

## 2. Dự án lớn fatc-Grocery-Store (1 512 node hiển thị, id mới `1ace0a0f`)

| Chế độ | touching | bbox | nn med / p10 | Hình dạng |
|---|---|---|---|---|
| `d3` macro `d3`, fit | **0** | 33 444 × 28 199 | 4.5 / 4.4px | ✅ cụm + vệ tinh tách rời, không blob tròn |
| `d3` macro `d3`, zoom 6.7× | 0 | — | — | ✅ node tách, edge hairline, label hiện theo ngưỡng |

So fa2 baseline (đóng băng): fit touching 8 (vô hình) nhưng dự án nhỏ vón cục 282 cặp →
d3 thắng tuyệt đối ở dự án nhỏ và ngang/ngon hơn ở dự án lớn.

## 3. Thời gian settle

- d3: worker 300 tick đồng bộ ≈ **1–3 s** (1512 node), không pha "Finalizing" 22 s của noverlap.
- fa2: 8 s FA2 + tối đa 22 s noverlap (chỉ còn khi bật `VITE_LAYOUT_ENGINE=fa2`).

## 4. Khác biệt thị giác cần lưu ý (không phải lỗi)

- Node ở fit của d3-mode là chấm ~0.5–1.5px (giống fit dự án lớn của grapuco); zoom curve hiện tại
  (`VITE_SIGMA_NODE_ZOOM_SIZE_POWER=0.7`) làm node to chậm hơn grapuco (p=1). Nếu muốn bóng to
  hơn khi zoom như grapuco: nâng `VITE_SIGMA_NODE_ZOOM_SIZE_POWER` lên 1 (ảnh hưởng cả fa2-mode).
- Macro `ngraph` giữ làm fallback A/B qua `VITE_LAYOUT_MACRO=ngraph`.

## 5. Trạng thái commit

- P1 `bdbbe66`: công tắc engine + docs qwen (README/01/02/03) + worker + layoutClient + specs.
- P2 (commit này): macro default `d3` + file kết quả này. Suite 579+ green, vue-tsc clean.
