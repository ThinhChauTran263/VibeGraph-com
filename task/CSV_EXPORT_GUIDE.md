# Hướng Dẫn Export CSV từ Markdown

## ✅ ĐÃ EXPORT THÀNH CÔNG!

Script đã tạo **5 files CSV** trong thư mục `csv_exports/`:

```
task/
├── csv_exports/
│   ├── product_backlog.csv      ← Product Backlog (PB01-PB14)
│   ├── release_backlog.csv      ← Release Backlog (RB01-RB33)
│   ├── sprint_backlog.csv       ← Sprint Backlog (T01-T72)
│   ├── pps_calculation.csv      ← PPS điểm và tính toán
│   └── ed_calculation.csv       ← ED (Environment Difficulty)
├── export_to_csv.py             ← Script Python
└── VibeGraph_WS3_Sprint-Trello-BBCH-ERD.md
```

---

## 📋 CÁCH SỬ DỤNG

### Phương án 1: Chạy Script (ĐÃ CHẠY)

```bash
cd "c:\Users\Tan Phat Computer Q8\OneDrive\Desktop\CODE\Project\vibegraph\VibeGraph-com\task"
python export_to_csv.py
```

**Kết quả:** ✅ 5 files CSV đã được tạo trong thư mục `csv_exports/`

### Phương án 2: Chạy lại khi cần (Sau khi cập nhật .md)

Mỗi khi bạn cập nhật file `VibeGraph_WS3_Sprint-Trello-BBCH-ERD.md`, chỉ cần:

```bash
python export_to_csv.py
```

Script sẽ tự động:
1. Đọc file Markdown
2. Parse các bảng
3. Export sang CSV (overwrite files cũ)

---

## 📤 UPLOAD LÊN GOOGLE DRIVE

### Bước 1: Mở Google Drive

Truy cập: https://drive.google.com

### Bước 2: Tạo folder (nếu chưa có)

```
My Drive/
└── VibeGraph Project/
    └── Sprint Backlogs/
```

### Bước 3: Upload files CSV

**Cách 1: Drag & Drop**
1. Mở folder `csv_exports/` trên Windows Explorer
2. Chọn tất cả 5 files CSV (Ctrl+A)
3. Kéo thả vào Google Drive folder

**Cách 2: Upload button**
1. Click nút "New" → "File upload"
2. Navigate đến `csv_exports/`
3. Chọn tất cả 5 files
4. Click "Open"

### Bước 4: Open với Google Sheets

Sau khi upload:
1. Right-click file CSV
2. Chọn "Open with" → "Google Sheets"
3. Google sẽ tự động convert CSV → Google Sheets
4. Save lại (File → Save)

---

## 🔄 SYNC VỚI GOOGLE SHEETS

### Option A: Manual Update

Mỗi khi có thay đổi:
1. Chạy `python export_to_csv.py`
2. Upload files CSV mới lên Drive (overwrite)
3. Refresh Google Sheets

### Option B: Import CSV vào Google Sheets có sẵn

Nếu đã có Google Sheets:
1. Mở Google Sheets
2. File → Import
3. Chọn "Upload" tab
4. Chọn CSV file mới
5. Import location: "Replace spreadsheet"
6. Click "Import data"

### Option C: Script tự động (Advanced)

Tạo script sync tự động với Google Drive API (nếu cần):
```python
# Cần cài: pip install google-auth google-auth-oauthlib google-api-python-client
# Code mẫu có thể cung cấp nếu cần
```

---

## 📊 IMPORT VÀO EXCEL

### Cách 1: Mở trực tiếp

1. Double-click file CSV
2. Excel tự động mở (nếu đã set default)
3. Save as .xlsx nếu cần

### Cách 2: Import với format control

1. Mở Excel → Data tab
2. "Get Data" → "From File" → "From Text/CSV"
3. Chọn CSV file
4. Preview và adjust delimiter (comma)
5. Click "Load"

**Lưu ý:** CSV đã dùng `utf-8-sig` encoding để hỗ trợ tiếng Việt trong Excel.

---

## 🔧 CUSTOMIZE SCRIPT

### Thêm columns hoặc filters

Edit `export_to_csv.py`:

```python
# Ví dụ: Chỉ export Sprint 1 tasks
def export_sprint_backlog(md_file, output_dir):
    # ... existing code ...
    
    # Filter Sprint 1 only
    filtered_rows = [row for row in rows if row[6] == '1']  # Sprint# column
    
    # Write filtered data
    # ...
```

### Export format khác

```python
# JSON format
import json
with open('sprint_backlog.json', 'w', encoding='utf-8') as f:
    json.dump(rows, f, ensure_ascii=False, indent=2)

# Excel format (cần openpyxl)
import openpyxl
wb = openpyxl.Workbook()
ws = wb.active
for row in rows:
    ws.append(row)
wb.save('sprint_backlog.xlsx')
```

---

## 🐛 TROUBLESHOOTING

### Lỗi: "ModuleNotFoundError: No module named 'openpyxl'"

Script hiện tại không cần openpyxl. Chỉ cần Python standard library.

Nếu muốn export Excel trực tiếp:
```bash
pip install openpyxl
```

### Lỗi: Encoding issue trong Excel

CSV đã dùng `utf-8-sig` (BOM) để Excel tự nhận tiếng Việt.

Nếu vẫn lỗi:
1. Mở CSV bằng Notepad
2. Save As → Encoding chọn "UTF-8 with BOM"
3. Mở lại trong Excel

### Lỗi: Bảng không parse đúng

Kiểm tra format Markdown table:
```markdown
| Col1 | Col2 | Col3 |
| ---- | ---- | ---- |
| Val1 | Val2 | Val3 |
```

Đảm bảo:
- Có separator line với `---`
- Mỗi row bắt đầu và kết thúc bằng `|`

---

## 📝 UPDATE WORKFLOW

### Quy trình chuẩn khi cập nhật Sprint Backlog:

```mermaid
graph LR
    A[Update .md file] --> B[python export_to_csv.py]
    B --> C[Review CSV files]
    C --> D[Upload to Drive]
    D --> E[Import to Google Sheets]
    E --> F[Share with team]
```

**Timeline:**
1. Update .md: 10-30 phút
2. Export CSV: < 5 giây
3. Upload Drive: < 1 phút
4. Import Sheets: < 1 phút
5. **Total: ~15-35 phút**

---

## 📂 FILE STRUCTURE

```
task/
├── VibeGraph_WS3_Sprint-Trello-BBCH-ERD.md  ← SOURCE (update this)
├── export_to_csv.py                         ← SCRIPT
├── csv_exports/                             ← OUTPUT
│   ├── product_backlog.csv
│   ├── release_backlog.csv
│   ├── sprint_backlog.csv
│   ├── pps_calculation.csv
│   └── ed_calculation.csv
├── SPRINT_TASK_UPDATE_SUMMARY.md            ← Reference
├── UPDATE_INSTRUCTIONS.md                   ← Guide
└── CSV_EXPORT_GUIDE.md                      ← This file
```

---

## 🎯 NEXT STEPS

1. ✅ **Review exported CSV files** trong `csv_exports/`
2. ✅ **Upload lên Google Drive** folder dự án
3. ✅ **Convert sang Google Sheets** để team dễ edit
4. 🔄 **Share link** với team members
5. 📅 **Schedule sync** (ví dụ: mỗi tuần export lại)

---

## 💡 TIPS

### Tip 1: Git ignore CSV files

Thêm vào `.gitignore`:
```
task/csv_exports/*.csv
```

Lý do: CSV là output file, không nên commit. Team dùng Google Sheets làm source of truth.

### Tip 2: Versioning

Khi export, thêm date vào filename:
```python
from datetime import datetime
date_str = datetime.now().strftime('%Y%m%d')
output_file = output_dir / f'sprint_backlog_{date_str}.csv'
```

### Tip 3: Validation

Trước khi upload, kiểm tra:
- [ ] Số rows đúng không?
- [ ] Có row nào bị thiếu data?
- [ ] Encoding tiếng Việt OK?
- [ ] Open được trong Excel?

---

## 📞 SUPPORT

Nếu cần hỗ trợ:
1. Check script output messages
2. Review this guide
3. Check file `export_to_csv.py` comments
4. Ask team lead (Thịnh)

---

*Created: 2026-06-05*  
*Last Export: 2026-06-05*  
*Files: 5 CSV files exported successfully*
