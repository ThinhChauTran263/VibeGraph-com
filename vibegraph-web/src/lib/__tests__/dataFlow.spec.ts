import { describe, expect, it } from 'vitest'
import {
  describeFlow,
  flowDomain,
  flowFileCount,
  listFlows,
  listTraceableEndpoints,
  traceDataFlow,
  type FlowListItem,
} from '../dataFlow'
import type { GraphData, GraphEdge, GraphNode } from '@/types/graph'

function node(
  id: string,
  type: GraphNode['type'],
  options: { name?: string; filePath?: string; props?: Record<string, unknown> } = {},
): GraphNode {
  return {
    id,
    type,
    name: options.name ?? id,
    fullName: `com.example.${id}`,
    filePath: options.filePath ?? `${id}.java`,
    lineNumber: 1,
    properties: options.props ?? {},
  }
}

function edge(id: string, source: string, target: string, type: GraphEdge['type']): GraphEdge {
  return { id, source, target, type }
}

function graphOf(nodes: GraphNode[], edges: GraphEdge[]): GraphData {
  return {
    nodes,
    edges,
    nodeStats: {} as GraphData['nodeStats'],
    edgeStats: {} as GraphData['edgeStats'],
  }
}

/**
 * A complete Spring stack: GET /api/orders -> OrderController.getOrders ->
 * OrderService.findOrders -> OrderRepository(interface).findAll -> impl.findAll
 * -> Order (DBModel via READS).
 */
function completeGraph(): GraphData {
  const nodes: GraphNode[] = [
    node('ep', 'APIEndpoint', { name: 'GET /api/orders' }),
    node('ctrl', 'Class', {
      name: 'OrderController',
      props: { springLayer: 'Controller' },
      filePath: 'web/OrderController.java',
    }),
    node('getOrders', 'Method', {
      name: 'getOrders',
      props: { httpMethod: 'GET', routePath: '/api/orders' },
      filePath: 'web/OrderController.java',
    }),
    node('svc', 'Class', {
      name: 'OrderService',
      props: { springLayer: 'Service' },
      filePath: 'svc/OrderService.java',
    }),
    node('findOrders', 'Method', { name: 'findOrders', filePath: 'svc/OrderService.java' }),
    node('repoIface', 'Interface', {
      name: 'OrderRepository',
      filePath: 'repo/OrderRepository.java',
    }),
    node('findAllIface', 'Method', { name: 'findAll', filePath: 'repo/OrderRepository.java' }),
    node('repoImpl', 'Class', {
      name: 'OrderRepositoryImpl',
      props: { springLayer: 'Repository' },
      filePath: 'repo/OrderRepositoryImpl.java',
    }),
    node('findAllImpl', 'Method', { name: 'findAll', filePath: 'repo/OrderRepositoryImpl.java' }),
    node('order', 'DBModel', { name: 'Order', filePath: 'model/Order.java' }),
  ]
  const edges: GraphEdge[] = [
    edge('hm1', 'ctrl', 'getOrders', 'HAS_METHOD'),
    edge('hm2', 'svc', 'findOrders', 'HAS_METHOD'),
    edge('hm3', 'repoIface', 'findAllIface', 'HAS_METHOD'),
    edge('hm4', 'repoImpl', 'findAllImpl', 'HAS_METHOD'),
    edge('hr1', 'ep', 'getOrders', 'HANDLES_ROUTE'),
    edge('c1', 'getOrders', 'findOrders', 'CALLS'),
    edge('c2', 'findOrders', 'findAllIface', 'CALLS'),
    edge('o1', 'findAllImpl', 'findAllIface', 'OVERRIDES'),
    edge('r1', 'findAllImpl', 'order', 'READS'),
  ]
  return graphOf(nodes, edges)
}

describe('listTraceableEndpoints', () => {
  it('discovers endpoints from HANDLES_ROUTE with method + path from the handler', () => {
    const endpoints = listTraceableEndpoints(completeGraph())
    expect(endpoints).toHaveLength(1)
    expect(endpoints[0]).toMatchObject({
      handlerId: 'getOrders',
      method: 'GET',
      path: '/api/orders',
    })
  })

  it('falls back to handler methods carrying HTTP metadata when no HANDLES_ROUTE exists', () => {
    const graph = graphOf(
      [node('m', 'Method', { name: 'list', props: { httpMethod: 'get', routePath: '/x' } })],
      [],
    )
    const endpoints = listTraceableEndpoints(graph)
    expect(endpoints).toHaveLength(1)
    expect(endpoints[0]).toMatchObject({ handlerId: 'm', method: 'GET', path: '/x' })
  })

  it('returns nothing when there are no endpoints', () => {
    expect(listTraceableEndpoints(graphOf([node('c', 'Class')], []))).toEqual([])
  })
})

describe('traceDataFlow', () => {
  it('traces controller -> service -> repository -> DBModel and marks it complete', () => {
    const graph = completeGraph()
    const endpoint = listTraceableEndpoints(graph)[0]!
    const flow = traceDataFlow(graph, endpoint)

    expect(flow.complete).toBe(true)
    expect(flow.steps.map((step) => step.nodeId)).toEqual([
      'getOrders',
      'findOrders',
      'findAllIface',
      'findAllImpl',
      'order',
    ])
    expect(flow.steps[flow.steps.length - 1]!.nodeType).toBe('DBModel')
    expect(flow.incompleteReason).toBeUndefined()
  })

  it('numbers steps sequentially from 1 and records the relation between steps', () => {
    const graph = completeGraph()
    const flow = traceDataFlow(graph, listTraceableEndpoints(graph)[0]!)

    expect(flow.steps.map((step) => step.index)).toEqual([1, 2, 3, 4, 5])
    expect(flow.steps[0]!.relationFromPrev).toBeUndefined()
    expect(flow.steps[1]!.relationFromPrev).toBe('CALLS')
    expect(flow.steps[4]!.relationFromPrev).toBe('READS')
  })

  it('collects the edge ids connecting consecutive steps for highlighting', () => {
    const graph = completeGraph()
    const flow = traceDataFlow(graph, listTraceableEndpoints(graph)[0]!)
    expect(flow.edgeIds).toEqual(['c1', 'c2', 'o1', 'r1'])
  })

  it('marks a flow incomplete and still returns the full chain when no DB is reachable', () => {
    const nodes: GraphNode[] = [
      node('ep', 'APIEndpoint', { name: 'GET /ping' }),
      node('ctrl', 'Class', { name: 'PingController', props: { springLayer: 'Controller' } }),
      node('ping', 'Method', { name: 'ping', props: { httpMethod: 'GET', routePath: '/ping' } }),
      node('svc', 'Class', { name: 'PingService', props: { springLayer: 'Service' } }),
      node('work', 'Method', { name: 'work' }),
    ]
    const edges: GraphEdge[] = [
      edge('hm1', 'ctrl', 'ping', 'HAS_METHOD'),
      edge('hm2', 'svc', 'work', 'HAS_METHOD'),
      edge('hr1', 'ep', 'ping', 'HANDLES_ROUTE'),
      edge('c1', 'ping', 'work', 'CALLS'),
    ]
    const graph = graphOf(nodes, edges)
    const flow = traceDataFlow(graph, listTraceableEndpoints(graph)[0]!)

    expect(flow.complete).toBe(false)
    expect(flow.steps.map((step) => step.nodeId)).toEqual(['ping', 'work'])
    expect(flow.incompleteReason).toContain('work')
  })

  it('flags an interface step with multiple implementations as ambiguous', () => {
    const nodes: GraphNode[] = [
      node('ep', 'APIEndpoint', { name: 'GET /a' }),
      node('ctrl', 'Class', { name: 'AController', props: { springLayer: 'Controller' } }),
      node('handle', 'Method', { name: 'handle', props: { httpMethod: 'GET', routePath: '/a' } }),
      node('iface', 'Interface', { name: 'Repo' }),
      node('ifaceFind', 'Method', { name: 'find' }),
      node('impl1', 'Class', { name: 'RepoImplA', props: { springLayer: 'Repository' } }),
      node('find1', 'Method', { name: 'find' }),
      node('impl2', 'Class', { name: 'RepoImplB', props: { springLayer: 'Repository' } }),
      node('find2', 'Method', { name: 'find' }),
    ]
    const edges: GraphEdge[] = [
      edge('hm0', 'ctrl', 'handle', 'HAS_METHOD'),
      edge('hm1', 'iface', 'ifaceFind', 'HAS_METHOD'),
      edge('hm2', 'impl1', 'find1', 'HAS_METHOD'),
      edge('hm3', 'impl2', 'find2', 'HAS_METHOD'),
      edge('hr1', 'ep', 'handle', 'HANDLES_ROUTE'),
      edge('c1', 'handle', 'ifaceFind', 'CALLS'),
      edge('o1', 'find1', 'ifaceFind', 'OVERRIDES'),
      edge('o2', 'find2', 'ifaceFind', 'OVERRIDES'),
    ]
    const graph = graphOf(nodes, edges)
    const flow = traceDataFlow(graph, listTraceableEndpoints(graph)[0]!)

    const ambiguous = flow.ambiguities.find((item) =>
      flow.steps.some((step) => step.index === item.stepIndex && step.nodeId === 'ifaceFind'),
    )
    expect(ambiguous).toBeDefined()
    expect(ambiguous!.candidates.map((candidate) => candidate.nodeId).sort()).toEqual([
      'find1',
      'find2',
    ])
  })
})

describe('listFlows', () => {
  it('lists endpoint flows first with title, domain, step and file counts', () => {
    const flows = listFlows(completeGraph())
    expect(flows.length).toBeGreaterThanOrEqual(1)

    const endpointFlow = flows.find((item) => item.kind === 'endpoint')!
    expect(endpointFlow.method).toBe('GET')
    expect(endpointFlow.path).toBe('/api/orders')
    expect(endpointFlow.title).toContain('GET /api/orders →')
    expect(endpointFlow.stepCount).toBe(5)
    // Distinct files: OrderController, OrderService, OrderRepository, impl, Order = 5.
    expect(endpointFlow.fileCount).toBe(5)
    expect(endpointFlow.domain).toBe('Order')
  })

  it('includes call-graph root methods that are not endpoints', () => {
    const nodes: GraphNode[] = [
      node('svc', 'Class', { name: 'JobService', props: { springLayer: 'Service' } }),
      node('run', 'Method', { name: 'run' }),
      node('step', 'Method', { name: 'step' }),
    ]
    const edges: GraphEdge[] = [
      edge('hm1', 'svc', 'run', 'HAS_METHOD'),
      edge('hm2', 'svc', 'step', 'HAS_METHOD'),
      edge('c1', 'run', 'step', 'CALLS'),
    ]
    const flows = listFlows(graphOf(nodes, edges))
    const methodFlow = flows.find((item) => item.kind === 'method')
    expect(methodFlow).toBeDefined()
    expect(methodFlow!.title).toBe('run → step')
  })
})

describe('flow helpers', () => {
  it('derives a humanized domain from the entry class, stripping layer suffixes', () => {
    const graph = completeGraph()
    const flow = traceDataFlow(graph, listTraceableEndpoints(graph)[0]!)
    expect(flowDomain(flow)).toBe('Order')
  })

  it('counts distinct files in a flow', () => {
    const graph = completeGraph()
    const flow = traceDataFlow(graph, listTraceableEndpoints(graph)[0]!)
    expect(flowFileCount(flow)).toBe(5)
  })

  it('describes an endpoint flow that reaches the database', () => {
    const graph = completeGraph()
    const item = listFlows(graph).find((flow) => flow.kind === 'endpoint') as FlowListItem
    const text = describeFlow(item)
    expect(text).toContain('GET /api/orders')
    expect(text).toContain('database model')
  })
})
