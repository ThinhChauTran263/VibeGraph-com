#!/usr/bin/env python3
"""
Export VibeGraph Sprint Backlog from Markdown to CSV
Usage: python export_to_csv.py
"""

import re
import csv
from pathlib import Path


def parse_markdown_table(lines, start_idx):
    """Parse a markdown table starting from start_idx"""
    rows = []
    i = start_idx
    
    # Skip header separator line (|---|---|)
    if i + 1 < len(lines) and '---' in lines[i + 1]:
        i += 2
    
    # Parse data rows
    while i < len(lines):
        line = lines[i].strip()
        if not line.startswith('|'):
            break
        
        # Split by | and clean up
        cells = [cell.strip() for cell in line.split('|')[1:-1]]
        rows.append(cells)
        i += 1
    
    return rows, i


def export_product_backlog(md_file, output_dir):
    """Export Product Backlog table to CSV"""
    with open(md_file, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    # Find Product Backlog section
    for i, line in enumerate(lines):
        if '## Product Backlog' in line:
            # Find table start
            for j in range(i, min(i + 20, len(lines))):
                if lines[j].strip().startswith('| ID'):
                    rows, _ = parse_markdown_table(lines, j)
                    
                    # Write to CSV
                    output_file = output_dir / 'product_backlog.csv'
                    with open(output_file, 'w', newline='', encoding='utf-8-sig') as f:
                        writer = csv.writer(f)
                        writer.writerows(rows)
                    
                    print(f"✅ Exported Product Backlog: {output_file}")
                    return
    
    print("WARNING: Product Backlog table not found")


def export_release_backlog(md_file, output_dir):
    """Export Release Backlog table to CSV"""
    with open(md_file, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    # Find Release Backlog section
    for i, line in enumerate(lines):
        if '## Release Backlog' in line:
            # Find table start
            for j in range(i, min(i + 20, len(lines))):
                if lines[j].strip().startswith('| Backlog ID'):
                    rows, _ = parse_markdown_table(lines, j)
                    
                    # Write to CSV
                    output_file = output_dir / 'release_backlog.csv'
                    with open(output_file, 'w', newline='', encoding='utf-8-sig') as f:
                        writer = csv.writer(f)
                        writer.writerows(rows)
                    
                    print(f"✅ Exported Release Backlog: {output_file}")
                    return
    
    print("WARNING: Release Backlog table not found")


def export_sprint_backlog(md_file, output_dir):
    """Export Sprint Backlog table to CSV"""
    with open(md_file, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    # Find Sprint Backlog section
    for i, line in enumerate(lines):
        if '## Sprint Backlog' in line:
            # Find table start
            for j in range(i, min(i + 20, len(lines))):
                if lines[j].strip().startswith('| Task ID'):
                    rows, _ = parse_markdown_table(lines, j)
                    
                    # Write to CSV
                    output_file = output_dir / 'sprint_backlog.csv'
                    with open(output_file, 'w', newline='', encoding='utf-8-sig') as f:
                        writer = csv.writer(f)
                        writer.writerows(rows)
                    
                    print(f"✅ Exported Sprint Backlog: {output_file}")
                    return
    
    print("WARNING: Sprint Backlog table not found")


def export_pps_table(md_file, output_dir):
    """Export PPS calculation table to CSV"""
    with open(md_file, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    # Find PPS section
    for i, line in enumerate(lines):
        if '## PPS' in line:
            # Find table start (skip empty lines)
            for j in range(i, min(i + 10, len(lines))):
                line = lines[j].strip()
                if line.startswith('|') and 'PPS' in line and 'AP' in line and 'ED' in line:
                    rows, _ = parse_markdown_table(lines, j)
                    
                    # Write to CSV
                    output_file = output_dir / 'pps_calculation.csv'
                    with open(output_file, 'w', newline='', encoding='utf-8-sig') as f:
                        writer = csv.writer(f)
                        writer.writerows(rows)
                    
                    print(f"✅ Exported PPS Table: {output_file}")
                    return
    
    print("WARNING: PPS table not found")


def export_ed_table(md_file, output_dir):
    """Export ED (Environment Difficulty) table to CSV"""
    with open(md_file, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    # Find ED section
    for i, line in enumerate(lines):
        if '## ED' in line:
            # Find table start
            for j in range(i, min(i + 10, len(lines))):
                line = lines[j].strip()
                if line.startswith('|') and 'STT' in line and '(0/2)' in line:
                    rows, _ = parse_markdown_table(lines, j)
                    
                    # Write to CSV
                    output_file = output_dir / 'ed_calculation.csv'
                    with open(output_file, 'w', newline='', encoding='utf-8-sig') as f:
                        writer = csv.writer(f)
                        writer.writerows(rows)
                    
                    print(f"✅ Exported ED Table: {output_file}")
                    return
    
    print("WARNING: ED table not found")


def main():
    # File paths
    script_dir = Path(__file__).parent
    md_file = script_dir / 'VibeGraph_WS3_Sprint-Trello-BBCH-ERD.md'
    output_dir = script_dir / 'csv_exports'
    
    # Create output directory
    output_dir.mkdir(exist_ok=True)
    
    print("🚀 Starting CSV export...")
    print(f"📂 Input: {md_file}")
    print(f"📂 Output: {output_dir}\n")
    
    if not md_file.exists():
        print(f"ERROR: File not found: {md_file}")
        return
    
    # Export all tables
    export_product_backlog(md_file, output_dir)
    export_release_backlog(md_file, output_dir)
    export_sprint_backlog(md_file, output_dir)
    export_pps_table(md_file, output_dir)
    export_ed_table(md_file, output_dir)
    
    print(f"\n✅ Export complete! Files saved to: {output_dir}")
    print("\n📋 Exported files:")
    for csv_file in sorted(output_dir.glob('*.csv')):
        print(f"   - {csv_file.name}")


if __name__ == '__main__':
    main()
