#!/usr/bin/env python3
"""
Check which Sprint 1 files actually exist in the codebase
"""

import os
from pathlib import Path

# Base path resolved dynamically based on script location
SCRIPT_DIR = Path(__file__).parent
BASE = SCRIPT_DIR.parent
SRC_MAIN = BASE
FRONTEND = BASE / "vibegraph-web/src"

# Sprint 1 tasks to check
sprint1_files = {
    # Import Archive
    "T01": ["test: (multipart direct - no DTO)"],
    "T02": ["src/main/java/com/vibegraph/graph/service/ArchiveImportService.java"],
    "T03": ["src/main/java/com/vibegraph/graph/importer/ArchiveTypeDetector.java"],
    "T04": ["src/main/java/com/vibegraph/graph/importer/ArchiveExtractor.java"],
    "T05": ["src/main/java/com/vibegraph/graph/controller/ImportController.java"],
    "T06": ["test: ArchiveImportServiceImplTest.java"],
    
    # Parser
    "T12": ["src/main/java/com/vibegraph/parser/node/NodeData.java", "src/main/java/com/vibegraph/parser/node/EdgeData.java", "src/main/java/com/vibegraph/parser/node/ParseResult.java"],
    "T13": ["src/main/java/com/vibegraph/parser/visitor/ClassVisitor.java"],
    "T14": ["src/main/java/com/vibegraph/parser/visitor/MethodVisitor.java"],
    "T15": ["src/main/java/com/vibegraph/parser/visitor/FieldVisitor.java"],
    "T16": ["src/main/java/com/vibegraph/parser/visitor/ImportVisitor.java"],
    "T17": ["src/main/java/com/vibegraph/parser/visitor/SpringAnnotationVisitor.java"],
    "T18": ["src/main/java/com/vibegraph/parser/visitor/ClassVisitor.java"],  # same as T13
    "T20": ["test: *VisitorTest.java"],
    
    # Neo4j
    "T21": ["src/main/java/com/vibegraph/common/config/Neo4jMigrationRunner.java"],
    "T22": ["src/main/resources/db/migration/V1__init_schema.cypher"],
    "T23": ["src/main/java/com/vibegraph/graph/repository/impl/neo4j/Neo4jGraphRepository.java"],
    "T24": ["src/main/java/com/vibegraph/graph/repository/impl/neo4j/Neo4jGraphRepository.java"],  # same as T23
    "T26": ["test: ArchUnit"],
    
    # REST API
    "T27": ["src/main/java/com/vibegraph/graph/controller/ProjectController.java"],
    "T28": ["src/main/java/com/vibegraph/graph/service/impl/AnalyzeServiceImpl.java"],
    "T29": ["src/main/java/com/vibegraph/graph/controller/GraphController.java"],
    "T30": ["src/main/java/com/vibegraph/common/exception/GlobalExceptionHandler.java"],
    "T34": ["test: integration"],
    
    # Frontend
    "T48": ["frontend: lib/api.ts"],
    "T49": ["frontend: stores/graph.ts"],
    "T50": ["frontend: lib/graphAdapter.ts"],
    "T51": ["frontend: composables/useSigma.ts", "frontend: components/graph/GraphCanvas.vue"],
    "T52": ["frontend: components/graph/SearchBar.vue"],
    "T53": ["frontend: components/graph/GraphCanvas.vue"],  # same as T51
    "T54": ["frontend: components/projects/AddProjectArchive.vue"],
}

print("🔍 Checking Sprint 1 Files Existence...\n")
print("=" * 80)

done_count = 0
new_count = 0
missing_files = []

for task_id, files in sorted(sprint1_files.items()):
    print(f"\n{task_id}:")
    all_exist = True
    
    for file_path in files:
        if file_path.startswith("test:"):
            print(f"  ⚠️  {file_path} (test/skip check)")
            continue
            
        if file_path.startswith("frontend:"):
            # Frontend file
            fe_path = file_path.replace("frontend: ", "")
            full_path = FRONTEND / fe_path
            exists = full_path.exists()
        else:
            # Backend file
            full_path = SRC_MAIN / file_path
            exists = full_path.exists()
        
        if exists:
            print(f"  ✅ {file_path}")
        else:
            print(f"  ❌ {file_path} - NOT FOUND")
            all_exist = False
            missing_files.append(f"{task_id}: {file_path}")
    
    if all_exist:
        done_count += 1
        print(f"  → Status: DONE ✅")
    else:
        new_count += 1
        print(f"  → Status: NEW (files missing)")

print("\n" + "=" * 80)
print(f"\n📊 Summary:")
print(f"  ✅ Tasks DONE (files exist):    {done_count}")
print(f"  ❌ Tasks NEW (files missing):   {new_count}")
print(f"  📋 Total Sprint 1 tasks:        {len(sprint1_files)}")

if missing_files:
    print(f"\n🚨 Missing Files ({len(missing_files)}):")
    for missing in missing_files:
        print(f"  - {missing}")
else:
    print("\n🎉 All Sprint 1 files exist!")

print("\n" + "=" * 80)
