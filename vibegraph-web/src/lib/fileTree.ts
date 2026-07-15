/**
 * File-tree builder for the Explorer panel.
 *
 * Turns a flat list of graph nodes into a nested folder/file tree derived from
 * each node's {@link GraphNode.filePath}. The tree mirrors the project's source
 * layout (e.g. `src/main/java/com/...`) so the Explorer can browse files and
 * focus the matching graph node on click.
 *
 * Pure + framework-free so it stays unit-testable in isolation.
 */

import type { GraphNode, NodeType } from '@/types/graph'

export interface FileTreeFileNode {
  kind: 'file'
  /** Basename shown in the tree (e.g. `SecurityConfig.java`). */
  name: string
  /** Full normalized path (forward slashes). */
  path: string
  /** Graph node id to focus when this file is clicked. */
  nodeId: string
  /** Type of the representative graph node (drives the icon tint). */
  nodeType: NodeType
}

export interface FileTreeFolderNode {
  kind: 'folder'
  /** Folder segment name. */
  name: string
  /** Full normalized folder path. */
  path: string
  children: FileTreeNode[]
}

export type FileTreeNode = FileTreeFolderNode | FileTreeFileNode

// When several nodes share one filePath (the common case: a File plus its
// Class/Method nodes), pick the most representative one to focus on click.
const REPRESENTATIVE_PRIORITY: NodeType[] = [
  'File',
  'Class',
  'Interface',
  'Enum',
  'Record',
  'DBModel',
]

/** Options controlling how the absolute file paths are rebased onto a clean project root. */
export interface BuildFileTreeOptions {
  /**
   * Absolute project source root (the backend `Project` node `path`). When given,
   * it is stripped from every file path so the tree starts inside the project
   * instead of the machine's upload/clone directory.
   */
  rootPath?: string | null
  /**
   * Display name for the single root folder (the backend `Project` node `name`,
   * e.g. `ASM_Final_Java6`). When given, the whole tree is nested under it.
   */
  rootName?: string | null
}

function normalizePath(filePath: string): string {
  return filePath.replace(/\\/g, '/').replace(/^\.\//, '').replace(/^\/+/, '')
}

/** Drop a trailing slash so a directory prefix can be matched as `${prefix}/`. */
function stripTrailingSlash(path: string): string {
  return path.replace(/\/+$/, '')
}

/**
 * The longest directory path shared by every file (excluding the file names).
 * Used as a fallback root when no explicit project root is supplied, so the tree
 * never renders the absolute machine path chain (`D:/Users/.../uploads/...`).
 */
function commonDirPrefix(paths: string[]): string {
  const first = paths[0]
  if (!first) return ''
  let common = first.split('/').slice(0, -1)
  for (let i = 1; i < paths.length; i += 1) {
    const segments = (paths[i] ?? '').split('/').slice(0, -1)
    let j = 0
    while (j < common.length && j < segments.length && common[j] === segments[j]) j += 1
    common = common.slice(0, j)
    if (common.length === 0) break
  }
  return common.join('/')
}

/**
 * Rebase a normalized file path onto the project root: strip {@code basePrefix}
 * (case-insensitive, to tolerate Windows drive-letter casing) when the path lives
 * under it. Paths outside the prefix are returned unchanged.
 */
function rebase(path: string, basePrefix: string): string {
  if (!basePrefix) return path
  const prefix = `${basePrefix.toLowerCase()}/`
  return path.toLowerCase().startsWith(prefix) ? path.slice(basePrefix.length + 1) : path
}

function basename(path: string): string {
  const segments = path.split('/')
  return segments[segments.length - 1] || path
}

function representativeRank(type: NodeType): number {
  const index = REPRESENTATIVE_PRIORITY.indexOf(type)
  return index === -1 ? REPRESENTATIVE_PRIORITY.length : index
}

/**
 * Collapse all nodes that share a file path down to a single file entry, keeping
 * the highest-priority node as the click target.
 */
function collectFiles(nodes: GraphNode[]): Map<string, FileTreeFileNode> {
  const files = new Map<string, FileTreeFileNode>()

  for (const node of nodes) {
    if (!node.filePath) continue
    const path = normalizePath(node.filePath)
    if (!path) continue

    const existing = files.get(path)
    if (existing && representativeRank(existing.nodeType) <= representativeRank(node.type)) {
      continue
    }

    files.set(path, {
      kind: 'file',
      name: basename(path),
      path,
      nodeId: node.id,
      nodeType: node.type,
    })
  }

  return files
}

function sortTree(nodes: FileTreeNode[]): FileTreeNode[] {
  nodes.sort((left, right) => {
    if (left.kind !== right.kind) {
      // Folders first, then files.
      return left.kind === 'folder' ? -1 : 1
    }
    return left.name.localeCompare(right.name, undefined, { numeric: true, sensitivity: 'base' })
  })

  for (const node of nodes) {
    if (node.kind === 'folder') sortTree(node.children)
  }

  return nodes
}

/**
 * Build a sorted folder/file tree from the given graph nodes.
 *
 * Nodes without a usable file path are ignored. Absolute paths are rebased onto
 * the project root: when {@code options.rootPath} is supplied it is stripped from
 * every path; otherwise the longest common directory prefix is stripped so the
 * tree never shows the machine's upload/clone directory chain. The result is
 * sorted with folders before files and case-insensitive alphabetical ordering
 * within each level — matching a typical IDE explorer.
 */
export function buildFileTree(
  nodes: GraphNode[],
  options: BuildFileTreeOptions = {},
): FileTreeNode[] {
  const files = collectFiles(nodes)
  if (files.size === 0) return []

  const allPaths = [...files.keys()]
  const explicitRoot = options.rootPath ? stripTrailingSlash(normalizePath(options.rootPath)) : ''
  const hasExplicitRoot =
    explicitRoot.length > 0 &&
    allPaths.some((path) => path.toLowerCase().startsWith(`${explicitRoot.toLowerCase()}/`))

  // Prefer the backend project root; else fall back to the common directory prefix
  // so absolute machine paths never leak into the tree.
  const basePrefix = hasExplicitRoot ? explicitRoot : commonDirPrefix(allPaths)
  // Root folder label: explicit project name, else the last segment of whatever
  // prefix we stripped (e.g. the project folder name).
  const rootName =
    options.rootName?.trim() || (basePrefix ? (basePrefix.split('/').pop() ?? '') : '')

  const roots: FileTreeNode[] = []
  // Index of folder path -> folder node, so repeated paths reuse the same branch.
  const folderIndex = new Map<string, FileTreeFolderNode>()
  // Root paths are prefixed with the root name (when set) to stay globally unique.
  const pathPrefix = rootName ? `${rootName}/` : ''

  for (const file of files.values()) {
    const relative = rebase(file.path, basePrefix)
    const segments = relative.split('/')
    const fileName = segments.pop() as string

    let parentChildren = roots
    let currentPath = rootName

    for (const segment of segments) {
      currentPath = currentPath ? `${currentPath}/${segment}` : segment
      let folder = folderIndex.get(currentPath)
      if (!folder) {
        folder = { kind: 'folder', name: segment, path: currentPath, children: [] }
        folderIndex.set(currentPath, folder)
        parentChildren.push(folder)
      }
      parentChildren = folder.children
    }

    parentChildren.push({
      ...file,
      name: fileName,
      path: `${pathPrefix}${relative}`,
    })
  }

  const sortedRoots = sortTree(roots)

  // Nest everything under a single project-root folder when we have a name.
  if (rootName) {
    return [{ kind: 'folder', name: rootName, path: rootName, children: sortedRoots }]
  }
  return sortedRoots
}

/**
 * Derive the Explorer tree root anchor from the graph's `Project` node: its
 * `path` property (absolute source root) and `name` (display label). Returns
 * empty options when there is no Project node, so the builder falls back to the
 * common-prefix heuristic.
 */
export function deriveTreeRoot(nodes: GraphNode[]): BuildFileTreeOptions {
  const project = nodes.find((node) => node.type === 'Project')
  if (!project) return {}
  const rawPath = project.properties?.path
  return {
    rootPath: typeof rawPath === 'string' ? rawPath : null,
    rootName: project.name || null,
  }
}

/** Collect every folder path in the tree (used to expand-all). */
export function collectFolderPaths(
  nodes: FileTreeNode[],
  acc: Set<string> = new Set(),
): Set<string> {
  for (const node of nodes) {
    if (node.kind === 'folder') {
      acc.add(node.path)
      collectFolderPaths(node.children, acc)
    }
  }
  return acc
}

/**
 * Filter the tree to entries whose file name (or any path segment) matches the
 * query. Folders are kept when they contain at least one matching descendant.
 * Returns the matching subtree plus the set of folder paths that must be
 * expanded to reveal the matches.
 */
export function filterFileTree(
  nodes: FileTreeNode[],
  query: string,
): { tree: FileTreeNode[]; expand: Set<string> } {
  const term = query.trim().toLowerCase()
  if (!term) return { tree: nodes, expand: new Set() }

  const expand = new Set<string>()

  function walk(items: FileTreeNode[]): FileTreeNode[] {
    const result: FileTreeNode[] = []
    for (const item of items) {
      if (item.kind === 'file') {
        if (item.name.toLowerCase().includes(term) || item.path.toLowerCase().includes(term)) {
          result.push(item)
        }
        continue
      }
      const children = walk(item.children)
      const selfMatches = item.name.toLowerCase().includes(term)
      if (children.length > 0 || selfMatches) {
        expand.add(item.path)
        result.push({ ...item, children: children.length > 0 ? children : item.children })
      }
    }
    return result
  }

  return { tree: walk(nodes), expand }
}
