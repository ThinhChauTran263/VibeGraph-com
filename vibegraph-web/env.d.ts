/// <reference types="vite/client" />

interface ImportMetaEnv {
  // Connectivity
  readonly VITE_API_URL?: string
  readonly VITE_WS_URL?: string

  // Graph rendering
  readonly VITE_GRAPH_SAFE_NODE_LIMIT?: string
  readonly VITE_EXPAND_MAX_NEIGHBORS?: string
  readonly VITE_NODE_SIZE_DEFAULT?: string
  readonly VITE_NODE_SIZE_MIN?: string
  readonly VITE_NODE_SIZE_MAX?: string
  readonly VITE_FOCUS_OPACITY_ACTIVE?: string
  readonly VITE_FOCUS_OPACITY_DIMMED?: string

  // Import / analysis polling
  readonly VITE_IMPORT_POLL_INTERVAL_MS?: string
  readonly VITE_IMPORT_STALL_TIMEOUT_MS?: string
  readonly VITE_IMPORT_ABSOLUTE_TIMEOUT_MS?: string

  // Project list
  readonly VITE_PROJECTS_AUTO_REFRESH_INTERVAL_MS?: string

  // Archive upload
  readonly VITE_ARCHIVE_MAX_SIZE_MB?: string

  // WebSocket (STOMP/SockJS)
  readonly VITE_WS_RECONNECT_DELAY_MS?: string
  readonly VITE_WS_HEARTBEAT_INCOMING_MS?: string
  readonly VITE_WS_HEARTBEAT_OUTGOING_MS?: string

  // Sigma labels
  readonly VITE_SIGMA_BASE_NODE_LABEL_SIZE?: string
  readonly VITE_SIGMA_BASE_EDGE_LABEL_SIZE?: string
  readonly VITE_SIGMA_MIN_LABEL_ZOOM_SCALE?: string
  readonly VITE_SIGMA_MAX_LABEL_ZOOM_SCALE?: string
  readonly VITE_SIGMA_MIN_EDGE_LABEL_ZOOM_SCALE?: string
  readonly VITE_SIGMA_MAX_EDGE_LABEL_ZOOM_SCALE?: string
  readonly VITE_SIGMA_LABEL_RENDERED_SIZE_THRESHOLD?: string

  // ForceAtlas2 layout
  readonly VITE_FA2_GRAVITY?: string
  readonly VITE_FA2_SCALING_RATIO?: string
  readonly VITE_FA2_BARNES_HUT_MIN_NODES?: string
  readonly VITE_FA2_SLOW_DOWN?: string
  readonly VITE_LAYOUT_AUTO_STOP_MS?: string
  readonly VITE_ZOOM_FIT_DURATION_MS?: string
  readonly VITE_LAYOUT_BRANCH_ENABLED?: string
  readonly VITE_LAYOUT_BRANCH_MIN_NODES?: string
  readonly VITE_LAYOUT_BRANCH_STRENGTH?: string
  readonly VITE_LAYOUT_BRANCH_LEVEL_GAP?: string
  readonly VITE_LAYOUT_BRANCH_JITTER?: string
  readonly VITE_LAYOUT_BRANCH_COMPONENT_GAP?: string
  readonly VITE_LAYOUT_SCREEN_OVERLAP_ENABLED?: string
  readonly VITE_LAYOUT_SCREEN_OVERLAP_GAP_PX?: string
  readonly VITE_LAYOUT_SCREEN_OVERLAP_ITERATIONS?: string
  readonly VITE_LAYOUT_SCREEN_OVERLAP_STRENGTH?: string

  // Node detail panel
  readonly VITE_NODE_DETAIL_MAX_PROPERTIES?: string
  readonly VITE_NODE_DETAIL_MAX_CONNECTIONS?: string

  // Search
  readonly VITE_SEARCH_SUGGESTIONS_LIMIT?: string

  // UML use-case diagram
  readonly VITE_UML_USECASE_MAX_CHARS?: string
  readonly VITE_UML_ACTOR_MAX_CHARS?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
