/**
 * Data Flow Analysis — pure client-side tracer.
 *
 * Traces how request data moves from an HTTP API endpoint, through Spring
 * controller/service layers, down to the repository and database model, using the
 * relationships already present in the loaded project graph (CALLS, HANDLES_ROUTE,
 * IMPLEMENTS/OVERRIDES, RETURNS/READS/WRITES/PARAMETER_TYPE) and the Spring layer
 * classification carried on Class nodes (`properties.springLayer`).
 *
 * Framework-free and side-effect-free so it stays unit-testable in isolation. The
 * panel/canvas layers consume {@link EndpointEntry} and {@link DataFlow}.
 */

import type { EdgeType, GraphData, GraphEdge, GraphNode, NodeType } from '@/types/graph'

/** A traceable HTTP endpoint discovered in the project graph. */
export interface EndpointEntry {
  /** Stable id for the list (the endpoint node id, else the handler method id). */
  id: string
  /** Graph node id of the controller handler Method where a trace begins. */
  handlerId: string
  /** HTTP method, upper-cased (e.g. `GET`); empty string when unknown. */
  method: string
  /** Route path (e.g. `/api/products`); empty string when unknown. */
  path: string
}

/** One ordered step of a traced data flow. */
export interface FlowStep {
  /** 1-based position; the controller handler Method is index 1. */
  index: number
  nodeId: string
  name: string
  nodeType: NodeType
  /** Owning class simple name, when the step is a member (Method/Field). */
  declaringType?: string
  /** Spring layer of the step's owning class, when defined. */
  springLayer?: string
  /** Source file path of the step's node (for the detail panel + file count). */
  filePath?: string
  /** Relationship type linking this step to the previous one (absent on step 1). */
  relationFromPrev?: EdgeType
}

/** A candidate continuation recorded at an ambiguous (multi-implementation) step. */
export interface FlowAmbiguity {
  stepIndex: number
  candidates: { nodeId: string; name: string }[]
}

/** The result of tracing one endpoint. */
export interface DataFlow {
  endpoint: EndpointEntry
  steps: FlowStep[]
  /** Edge ids connecting consecutive steps, for graph highlighting. */
  edgeIds: string[]
  /** True when the trace reached a Repository-layer method or a DBModel. */
  complete: boolean
  /** Human-readable reason when the flow is incomplete. */
  incompleteReason?: string
  ambiguities: FlowAmbiguity[]
}

// Edges followed when walking forward from controller toward the database.
const CALL_EDGES: ReadonlySet<EdgeType> = new Set<EdgeType>(['CALLS'])
// Edges that bridge an interface method to its concrete implementation.
const RESOLVE_EDGES: ReadonlySet<EdgeType> = new Set<EdgeType>(['IMPLEMENTS', 'OVERRIDES'])
// Edges linking a repository method to the persistence/domain model it touches.
const DB_LINK_EDGES: ReadonlySet<EdgeType> = new Set<EdgeType>([
  'RETURNS',
  'READS',
  'WRITES',
  'PARAMETER_TYPE',
])

// Safety bound so a pathological graph can never produce an unbounded chain.
const MAX_STEPS = 200

interface GraphIndex {
  nodeById: Map<string, GraphNode>
  /** Forward adjacency (source -> edges) limited to traversable edge types. */
  outByNode: Map<string, GraphEdge[]>
  /** Method node id -> owning Class node id (from HAS_METHOD). */
  methodToClass: Map<string, string>
}

function buildIndex(graph: GraphData): GraphIndex {
  const nodeById = new Map<string, GraphNode>()
  for (const node of graph.nodes) nodeById.set(node.id, node)

  const outByNode = new Map<string, GraphEdge[]>()
  const methodToClass = new Map<string, string>()

  for (const edge of graph.edges) {
    if (edge.type === 'HAS_METHOD') {
      methodToClass.set(edge.target, edge.source)
    }
    if (CALL_EDGES.has(edge.type) || RESOLVE_EDGES.has(edge.type) || DB_LINK_EDGES.has(edge.type)) {
      const list = outByNode.get(edge.source)
      if (list) list.push(edge)
      else outByNode.set(edge.source, [edge])
      // Interface bridging is undirected: an OVERRIDES/IMPLEMENTS edge should be
      // followable from either end to reach the concrete method.
      if (RESOLVE_EDGES.has(edge.type)) {
        const back = outByNode.get(edge.target)
        const mirrored: GraphEdge = { ...edge, source: edge.target, target: edge.source }
        if (back) back.push(mirrored)
        else outByNode.set(edge.target, [mirrored])
      }
    }
  }

  return { nodeById, outByNode, methodToClass }
}

function springLayerOf(index: GraphIndex, node: GraphNode | undefined): string | undefined {
  if (!node) return undefined
  const own = node.properties?.springLayer
  if (typeof own === 'string' && own) return own
  const classId = index.methodToClass.get(node.id)
  const cls = classId ? index.nodeById.get(classId) : undefined
  const clsLayer = cls?.properties?.springLayer
  return typeof clsLayer === 'string' && clsLayer ? clsLayer : undefined
}

function declaringTypeOf(index: GraphIndex, node: GraphNode | undefined): string | undefined {
  if (!node) return undefined
  const classId = index.methodToClass.get(node.id)
  return classId ? index.nodeById.get(classId)?.name : undefined
}

function isRepositoryMethod(index: GraphIndex, node: GraphNode | undefined): boolean {
  return springLayerOf(index, node)?.toLowerCase() === 'repository'
}

function stringProp(node: GraphNode | undefined, key: string): string {
  const value = node?.properties?.[key]
  return typeof value === 'string' ? value : ''
}

/**
 * Discover every traceable endpoint: a controller handler Method connected to an
 * API_Endpoint/Route by HANDLES_ROUTE. Falls back to Method nodes that directly
 * carry HTTP metadata when no HANDLES_ROUTE edges exist. Sorted by path then
 * method (ascending, case-insensitive) for a deterministic list.
 */
export function listTraceableEndpoints(graph: GraphData): EndpointEntry[] {
  const nodeById = new Map<string, GraphNode>()
  for (const node of graph.nodes) nodeById.set(node.id, node)

  const entries = new Map<string, EndpointEntry>()

  for (const edge of graph.edges) {
    if (edge.type !== 'HANDLES_ROUTE') continue
    const a = nodeById.get(edge.source)
    const b = nodeById.get(edge.target)
    if (!a || !b) continue
    const handler = a.type === 'Method' || a.type === 'Constructor' ? a : b
    const endpoint = handler === a ? b : a
    if (handler.type !== 'Method' && handler.type !== 'Constructor') continue

    const method = (stringProp(handler, 'httpMethod') || endpointMethod(endpoint)).toUpperCase()
    const path = stringProp(handler, 'routePath') || endpointPath(endpoint)
    entries.set(endpoint.id, { id: endpoint.id, handlerId: handler.id, method, path })
  }

  if (entries.size === 0) {
    // Fallback: controller handler methods that carry HTTP metadata directly.
    for (const node of graph.nodes) {
      if (node.type !== 'Method') continue
      const method = stringProp(node, 'httpMethod')
      if (!method) continue
      entries.set(node.id, {
        id: node.id,
        handlerId: node.id,
        method: method.toUpperCase(),
        path: stringProp(node, 'routePath'),
      })
    }
  }

  return [...entries.values()].sort((left, right) => {
    const byPath = left.path.toLowerCase().localeCompare(right.path.toLowerCase())
    return byPath !== 0 ? byPath : left.method.toLowerCase().localeCompare(right.method.toLowerCase())
  })
}

function endpointMethod(node: GraphNode): string {
  const fromProp = stringProp(node, 'httpMethod')
  if (fromProp) return fromProp
  // Endpoint names are often "GET /api/x"; take the leading verb when present.
  const head = node.name.trim().split(/\s+/)[0] ?? ''
  return /^[A-Z]+$/i.test(head) ? head : ''
}

function endpointPath(node: GraphNode): string {
  const fromProp = stringProp(node, 'routePath')
  if (fromProp) return fromProp
  const parts = node.name.trim().split(/\s+/)
  return parts.length > 1 ? parts.slice(1).join(' ') : node.name
}

function toStep(index: GraphIndex, node: GraphNode, stepIndex: number, relation?: EdgeType): FlowStep {
  return {
    index: stepIndex,
    nodeId: node.id,
    name: node.name,
    nodeType: node.type,
    declaringType: declaringTypeOf(index, node),
    springLayer: springLayerOf(index, node),
    filePath: node.filePath || undefined,
    relationFromPrev: relation,
  }
}

interface Parent {
  edge: GraphEdge
  from: string
}

/**
 * Trace a Data_Flow for the given endpoint by breadth-first search from the
 * controller handler Method to the nearest database terminal (a DBModel, or a
 * repository-layer method), following CALLS and interface-resolution edges. The
 * shortest such path is returned as ordered steps with the relationship used at
 * each hop, plus the edge ids for graph highlighting.
 */
export function traceDataFlow(graph: GraphData, endpoint: EndpointEntry): DataFlow {
  return traceFromIndex(buildIndex(graph), graph.nodes.length, endpoint)
}

/**
 * Core trace using a prebuilt {@link GraphIndex}, so callers that trace many
 * flows (see {@link listFlows}) build the index once. {@code nodeCount} bounds
 * the BFS visited set.
 */
function traceFromIndex(index: GraphIndex, nodeCount: number, endpoint: EndpointEntry): DataFlow {
  const start = index.nodeById.get(endpoint.handlerId)

  const base: DataFlow = { endpoint, steps: [], edgeIds: [], complete: false, ambiguities: [] }
  if (!start) {
    return { ...base, incompleteReason: 'Handler method not found in the graph.' }
  }

  // BFS to the nearest DBModel; remember the nearest repository method, and the
  // farthest reachable node, as fallback terminals when no DBModel is reachable.
  const parents = new Map<string, Parent>()
  const visited = new Set<string>([start.id])
  const depth = new Map<string, number>([[start.id, 0]])
  const queue: string[] = [start.id]
  let target: string | null = null
  let repoFallback: string | null = null
  let farthest: string | null = null
  let maxDepth = 0

  while (queue.length > 0) {
    const currentId = queue.shift() as string
    const currentNode = index.nodeById.get(currentId)

    if (currentId !== start.id && currentNode?.type === 'DBModel') {
      target = currentId
      break
    }
    if (!repoFallback && currentId !== start.id && isRepositoryMethod(index, currentNode)) {
      repoFallback = currentId
    }
    if (visited.size > nodeCount) break

    for (const edge of index.outByNode.get(currentId) ?? []) {
      if (visited.has(edge.target)) continue
      visited.add(edge.target)
      parents.set(edge.target, { edge, from: currentId })
      const nextDepth = (depth.get(currentId) ?? 0) + 1
      depth.set(edge.target, nextDepth)
      if (nextDepth > maxDepth) {
        maxDepth = nextDepth
        farthest = edge.target
      }
      queue.push(edge.target)
    }
  }

  // Prefer a DBModel, then a repository method, then the farthest reachable node
  // so an incomplete trace still shows the whole call chain (not just the entry).
  const terminal = target ?? repoFallback ?? farthest
  if (!terminal) {
    // Nothing reachable at all: the flow is just the handler.
    return {
      ...base,
      steps: [toStep(index, start, 1)],
      complete: false,
      incompleteReason: 'No methods reachable from this endpoint.',
    }
  }

  // Reconstruct the path terminal -> start, then reverse.
  const pathNodes: string[] = []
  const pathEdges: (GraphEdge | undefined)[] = []
  let cursor: string | undefined = terminal
  while (cursor && cursor !== start.id) {
    const parent = parents.get(cursor)
    pathNodes.push(cursor)
    pathEdges.push(parent?.edge)
    cursor = parent?.from
  }
  pathNodes.push(start.id)
  pathNodes.reverse()
  pathEdges.reverse() // pathEdges[i] links step i to step i+1

  const steps: FlowStep[] = []
  const edgeIds: string[] = []
  for (let i = 0; i < pathNodes.length && i < MAX_STEPS; i += 1) {
    const node = index.nodeById.get(pathNodes[i] as string)
    if (!node) continue
    const incoming = i > 0 ? pathEdges[i - 1] : undefined
    steps.push(toStep(index, node, steps.length + 1, incoming?.type))
    if (incoming?.id) edgeIds.push(incoming.id)
  }

  // If the terminal is a repository method that links to a DBModel, append it.
  let complete = target != null
  const lastNode = index.nodeById.get(terminal)
  if (!target && isRepositoryMethod(index, lastNode)) {
    const dbEdge = (index.outByNode.get(terminal) ?? []).find(
      (edge) => DB_LINK_EDGES.has(edge.type) && index.nodeById.get(edge.target)?.type === 'DBModel',
    )
    const dbNode = dbEdge ? index.nodeById.get(dbEdge.target) : undefined
    if (dbEdge && dbNode && steps.length < MAX_STEPS) {
      steps.push(toStep(index, dbNode, steps.length + 1, dbEdge.type))
      if (dbEdge.id) edgeIds.push(dbEdge.id)
      complete = true
    }
  }

  const ambiguities = collectAmbiguities(index, steps)

  return {
    endpoint,
    steps,
    edgeIds,
    complete,
    ambiguities,
    incompleteReason: complete
      ? undefined
      : `Trace stopped at "${steps[steps.length - 1]?.name ?? 'start'}" before reaching a database model.`,
  }
}

/**
 * Flag steps that are interface methods with more than one concrete
 * implementation, recording each candidate so the panel can offer alternatives.
 */
function collectAmbiguities(index: GraphIndex, steps: FlowStep[]): FlowAmbiguity[] {
  const ambiguities: FlowAmbiguity[] = []
  for (const step of steps) {
    const classId = index.methodToClass.get(step.nodeId)
    const cls = classId ? index.nodeById.get(classId) : undefined
    if (cls?.type !== 'Interface') continue

    const candidates = (index.outByNode.get(step.nodeId) ?? [])
      .filter((edge) => edge.type === 'OVERRIDES' || edge.type === 'IMPLEMENTS')
      .map((edge) => index.nodeById.get(edge.target))
      .filter((node): node is GraphNode => node != null && node.id !== step.nodeId)
      .map((node) => ({ nodeId: node.id, name: node.name }))

    const unique = [...new Map(candidates.map((candidate) => [candidate.nodeId, candidate])).values()]
    if (unique.length > 1) {
      ambiguities.push({ stepIndex: step.index, candidates: unique })
    }
  }
  return ambiguities
}

// ---------------------------------------------------------------------------
// Flow listing (reference-style "Flows" list with domain + step/file counts)
// ---------------------------------------------------------------------------

export type FlowKind = 'endpoint' | 'method'

/** A precomputed entry in the Flows list: its meta plus the traced Data_Flow. */
export interface FlowListItem {
  id: string
  kind: FlowKind
  /** HTTP method for endpoint flows; '' for method-entry flows. */
  method: string
  /** Route path for endpoint flows; '' for method-entry flows. */
  path: string
  /** Display title, e.g. `GET /x → getProfile` or `update → save`. */
  title: string
  /** Inferred feature/domain label (from the entry's owning class/package). */
  domain: string
  /** Entry (handler/root) method node id. */
  entryId: string
  flow: DataFlow
  stepCount: number
  fileCount: number
}

// Spring layer/class suffixes stripped when humanizing a domain label.
const CLASS_SUFFIXES = /(Controller|RestController|Service|ServiceImpl|Impl|Repository|Repo|Manager|Component|Handler)$/

/** Distinct source files referenced by a flow's steps. */
export function flowFileCount(flow: DataFlow): number {
  return new Set(flow.steps.map((step) => step.filePath).filter(Boolean)).size
}

/** Split a CamelCase / snake identifier into a spaced Title Case label. */
function humanize(identifier: string): string {
  const spaced = identifier
    .replace(/[_-]+/g, ' ')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2')
    .trim()
  return spaced.replace(/\b\w/g, (char) => char.toUpperCase())
}

/**
 * Infer a human-readable domain label for a flow from its entry step's owning
 * class (e.g. `ServiceConfigController` -> "Service Config"). Falls back to the
 * declaring type or the entry name.
 */
export function flowDomain(flow: DataFlow): string {
  const entry = flow.steps[0]
  const owner = entry?.declaringType
  if (owner) return humanize(owner.replace(CLASS_SUFFIXES, '') || owner)
  return entry ? humanize(entry.name) : 'General'
}

/** Build a plain-language summary of a traced flow for the detail panel. */
export function describeFlow(item: FlowListItem): string {
  const { flow } = item
  const first = flow.steps[0]
  const last = flow.steps[flow.steps.length - 1]
  if (!first || !last) return 'No data flow could be traced for this entry.'

  const entryPhrase = item.kind === 'endpoint' && item.path
    ? `the ${item.method || 'HTTP'} ${item.path} endpoint`
    : `${first.name}()`
  const through = first.declaringType ? ` via ${first.declaringType}` : ''
  const tail = flow.complete
    ? `reaching ${last.nodeType === 'DBModel' ? 'the ' + last.name + ' database model' : last.name}`
    : `stopping at ${last.name} before reaching a database model`
  return `Traces ${entryPhrase}${through} through ${flow.steps.length} step${flow.steps.length === 1 ? '' : 's'}, ${tail}.`
}

/**
 * Enumerate traceable flows for the project: one per HTTP endpoint, plus
 * call-graph "root" methods (entry points that call others but are never called)
 * in the controller/service layers. Each flow is fully traced. Results are
 * capped and sorted (endpoints first, then by title) for a stable list.
 */
export function listFlows(graph: GraphData, limit = 80): FlowListItem[] {
  const index = buildIndex(graph)
  const items: FlowListItem[] = []
  const usedEntries = new Set<string>()

  // 1. Endpoint flows.
  for (const endpoint of listTraceableEndpoints(graph)) {
    const flow = traceFromIndex(index, graph.nodes.length, endpoint)
    if (flow.steps.length === 0) continue
    usedEntries.add(endpoint.handlerId)
    items.push(toListItem('endpoint', endpoint.handlerId, endpoint.method, endpoint.path, flow))
  }

  // 2. Call-graph root methods (have outgoing CALLS, no incoming CALLS).
  const hasIncomingCall = new Set<string>()
  const hasOutgoingCall = new Set<string>()
  for (const edge of graph.edges) {
    if (edge.type !== 'CALLS') continue
    hasOutgoingCall.add(edge.source)
    hasIncomingCall.add(edge.target)
  }

  const roots: GraphNode[] = []
  for (const node of graph.nodes) {
    if (node.type !== 'Method') continue
    if (usedEntries.has(node.id)) continue
    if (!hasOutgoingCall.has(node.id) || hasIncomingCall.has(node.id)) continue
    const layer = springLayerOf(index, node)?.toLowerCase()
    if (layer !== 'controller' && layer !== 'service' && layer !== 'component') continue
    roots.push(node)
  }

  for (const root of roots) {
    if (items.length >= limit) break
    const flow = traceFromIndex(index, graph.nodes.length, {
      id: root.id,
      handlerId: root.id,
      method: '',
      path: '',
    })
    if (flow.steps.length < 2) continue // single-node "flows" aren't interesting
    items.push(toListItem('method', root.id, '', '', flow))
  }

  return items
    .sort((left, right) => {
      if (left.kind !== right.kind) return left.kind === 'endpoint' ? -1 : 1
      return left.title.toLowerCase().localeCompare(right.title.toLowerCase())
    })
    .slice(0, limit)
}

function toListItem(
  kind: FlowKind,
  entryId: string,
  method: string,
  path: string,
  flow: DataFlow,
): FlowListItem {
  const terminal = flow.steps[flow.steps.length - 1]
  const entry = flow.steps[0]
  const title =
    kind === 'endpoint'
      ? `${method} ${path} → ${terminal?.name ?? ''}`.trim()
      : `${entry?.name ?? ''} → ${terminal?.name ?? ''}`
  const item: FlowListItem = {
    id: entryId,
    kind,
    method,
    path,
    title,
    domain: 'General',
    entryId,
    flow,
    stepCount: flow.steps.length,
    fileCount: flowFileCount(flow),
  }
  item.domain = flowDomain(flow)
  return item
}
