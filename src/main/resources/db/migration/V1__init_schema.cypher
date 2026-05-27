// =============================================================================
// VibeGraph - Neo4j Schema V1 (Initial)
// =============================================================================
// Version: 1.0.0
// Date:    2026-05-27
//
// This script creates uniqueness constraints, lookup indexes, and a fulltext
// index for the VibeGraph code knowledge graph. It contains NO data — nodes
// and relationships are inserted by the parser pipeline at runtime.
//
// Mental model (Neo4j vs MySQL):
//   - MySQL: schema = CREATE TABLE + FOREIGN KEY (rigid columns, FK rows).
//   - Neo4j: schema is "schema-optional" — labels and properties exist as
//     soon as you write a node. The "DDL" you really need is:
//       1. Uniqueness constraints — enforce identity (replaces PRIMARY KEY)
//       2. Indexes               — make MERGE / MATCH fast
//       3. Fulltext indexes      — search bar lookups
//     Relationships do not need a schema; they exist by being written.
//
// Multi-tenancy: every domain node carries a `projectId` property. Composite
// constraints `(projectId, fullName[, paramTypes])` give us per-project
// uniqueness without a separate tenant table.
//
// Safe to re-run: every statement uses IF NOT EXISTS.
// =============================================================================


// -----------------------------------------------------------------------------
// 1. UNIQUENESS CONSTRAINTS  (identity / "primary key" equivalent)
// -----------------------------------------------------------------------------

// Project — global UUID (no projectId scoping; this IS the tenant root)
CREATE CONSTRAINT project_id_unique IF NOT EXISTS
FOR (p:Project) REQUIRE p.id IS UNIQUE;

// Package — fully-qualified package name within a project
CREATE CONSTRAINT package_unique IF NOT EXISTS
FOR (p:Package) REQUIRE (p.projectId, p.fullName) IS UNIQUE;

// File — relative path within a project
CREATE CONSTRAINT file_unique IF NOT EXISTS
FOR (f:File) REQUIRE (f.projectId, f.filePath) IS UNIQUE;

// Class / Interface / Enum / Annotation — fully-qualified type name
CREATE CONSTRAINT class_unique IF NOT EXISTS
FOR (c:Class) REQUIRE (c.projectId, c.fullName) IS UNIQUE;

CREATE CONSTRAINT interface_unique IF NOT EXISTS
FOR (i:Interface) REQUIRE (i.projectId, i.fullName) IS UNIQUE;

CREATE CONSTRAINT enum_unique IF NOT EXISTS
FOR (e:Enum) REQUIRE (e.projectId, e.fullName) IS UNIQUE;

CREATE CONSTRAINT annotation_unique IF NOT EXISTS
FOR (a:Annotation) REQUIRE (a.projectId, a.fullName) IS UNIQUE;

// Method — composite key handles overloads (same name, different paramTypes)
CREATE CONSTRAINT method_unique IF NOT EXISTS
FOR (m:Method) REQUIRE (m.projectId, m.fullName, m.paramTypes) IS UNIQUE;

// Field — fullName already includes owner class
CREATE CONSTRAINT field_unique IF NOT EXISTS
FOR (f:Field) REQUIRE (f.projectId, f.fullName) IS UNIQUE;

// Route — uniqueness on (httpMethod, routePath) per project
CREATE CONSTRAINT route_unique IF NOT EXISTS
FOR (r:Route) REQUIRE (r.projectId, r.httpMethod, r.routePath) IS UNIQUE;


// -----------------------------------------------------------------------------
// 2. LOOKUP INDEXES  (speed up search-bar / filter / watcher queries)
// -----------------------------------------------------------------------------

// Search bar — find symbol by simple name within a project
CREATE INDEX class_proj_name IF NOT EXISTS
FOR (c:Class) ON (c.projectId, c.name);

CREATE INDEX interface_proj_name IF NOT EXISTS
FOR (i:Interface) ON (i.projectId, i.name);

CREATE INDEX method_proj_name IF NOT EXISTS
FOR (m:Method) ON (m.projectId, m.name);

CREATE INDEX field_proj_name IF NOT EXISTS
FOR (f:Field) ON (f.projectId, f.name);

// Filter Panel — group classes by Spring layer
CREATE INDEX class_spring_layer IF NOT EXISTS
FOR (c:Class) ON (c.projectId, c.springLayer);

// Route inspection — locate handler by URL path
CREATE INDEX route_path IF NOT EXISTS
FOR (r:Route) ON (r.projectId, r.routePath);

// Watcher — quickly resolve a changed file path to its node
CREATE INDEX file_path IF NOT EXISTS
FOR (f:File) ON (f.projectId, f.filePath);

// Stub cleanup — orphan stub Methods produced by unresolved CALLS
CREATE INDEX method_stub IF NOT EXISTS
FOR (m:Method) ON (m.projectId, m.isStub);


// -----------------------------------------------------------------------------
// 3. FULLTEXT INDEX  (search bar fuzzy / case-insensitive lookups)
// -----------------------------------------------------------------------------

CREATE FULLTEXT INDEX node_search IF NOT EXISTS
FOR (n:Class|Interface|Enum|Method|Field|Annotation)
ON EACH [n.name, n.fullName];


// =============================================================================
// End of V1__init_schema.cypher
// =============================================================================
