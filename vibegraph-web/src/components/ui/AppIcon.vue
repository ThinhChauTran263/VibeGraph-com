<script setup lang="ts">
const props = withDefaults(defineProps<{ name: string; size?: number }>(), { size: 20 })

const paths: Record<string, string[]> = {
  overview: ['M4 13h6V4H4z', 'M14 20h6v-9h-6z', 'M14 7h6V4h-6z', 'M4 20h6v-3H4z'],
  users: [
    'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2',
    'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
    'M22 21v-2a4 4 0 0 0-3-3.87',
    'M16 3.13a4 4 0 0 1 0 7.75',
  ],
  shield: ['M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z', 'M9 12l2 2 4-4'],
  audit: ['M6 3h12v18H6z', 'M9 7h6', 'M9 11h6', 'M9 15h4'],
  system: ['M4 5h16v12H4z', 'M8 21h8', 'M12 17v4'],
  announcement: ['M3 11v2a2 2 0 0 0 2 2h2l3 5h3l-2-5 8-3V6l-12 5z'],
  account: ['M20 21a8 8 0 0 0-16 0', 'M12 13a5 5 0 1 0 0-10 5 5 0 0 0 0 10z'],
  repository: ['M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z'],
  key: ['M15 7a4 4 0 1 0-3.8 5.3L3 20h4v-3h3v-3h2.2A4 4 0 0 0 15 7z'],
  usage: ['M4 19V9', 'M10 19V5', 'M16 19v-7', 'M22 19H2'],
  subscription: ['M3 6h18v12H3z', 'M3 10h18'],
  reports: ['M5 3h14v18l-7-3-7 3z', 'M9 8h6', 'M9 12h6'],
  notification: ['M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9', 'M10 21h4'],
  tutorial: [
    'M4 5h7a3 3 0 0 1 3 3v11a3 3 0 0 0-3-3H4z',
    'M20 5h-3a3 3 0 0 0-3 3v11a3 3 0 0 1 3-3h3z',
  ],
  settings: [
    'M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z',
    'M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21h-4v-.1a1.7 1.7 0 0 0-1-1.5 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3v-4h.1a1.7 1.7 0 0 0 1.5-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3 1.7 1.7 0 0 0 1-1.5V3h4v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.5 1h.1v4h-.1a1.7 1.7 0 0 0-1.5 1z',
  ],
  logout: ['M10 17l5-5-5-5', 'M15 12H3', 'M15 4h4a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-4'],
  wallet: ['M3 6h16a2 2 0 0 1 2 2v10H5a2 2 0 0 1-2-2z', 'M16 11h5v4h-5z'],
  menu: ['M5 6.5h14', 'M5 12h14', 'M5 17.5h14'],
  chevron: ['M9 18l6-6-6-6'],
  plus: ['M12 5v14', 'M5 12h14'],
  trash: ['M4 7h16', 'M9 7V4h6v3', 'M7 7l1 13h8l1-13'],
  graph: [
    'M6 5a2 2 0 1 0 0 .1',
    'M18 7a2 2 0 1 0 0 .1',
    'M12 19a2 2 0 1 0 0 .1',
    'M7.7 6l8.6 1',
    'M7 7l4 10',
    'M17 9l-4 8',
  ],
  close: ['M6 6l12 12', 'M18 6 6 18'],
  info: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M12 11v5', 'M12 8h.01'],
  warning: ['M12 3 2.5 20h19L12 3z', 'M12 10v4', 'M12 17h.01'],
  critical: [
    'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z',
    'M12 7v6',
    'M12 16h.01',
  ],
  inbox: [
    'M3 12h5l2 3h4l2-3h5',
    'M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z',
  ],
  check: ['M4 12.5 9 17.5 20 6.5'],
  restore: ['M3 12a9 9 0 1 0 3-6.7', 'M3 4v5h5'],
  // F-L4: import-method icons moved here from ImportProjectPanel's inline SVG paths.
  terminal: ['M4 5h16v14H4z M8 9l3 3-3 3 M13 15h4'],
  package: ['M3 7l9-4 9 4v10l-9 4-9-4z M3 7l9 4 9-4 M12 11v10'],
  github: [
    'M9 19c-4.3 1.4-4.3-2.5-6-3m12 5v-3.5c0-1 .1-1.4-.5-2 2.8-.3 5.5-1.4 5.5-6a4.6 4.6 0 0 0-1.3-3.2 4.2 4.2 0 0 0-.1-3.2s-1.1-.3-3.5 1.3a12 12 0 0 0-6.2 0C6.5 2.8 5.4 3.1 5.4 3.1a4.2 4.2 0 0 0-.1 3.2A4.6 4.6 0 0 0 4 9.5c0 4.6 2.7 5.7 5.5 6-.6.6-.6 1.2-.5 2V21',
  ],
  branch: [
    'M6 3v12',
    'M15 6a3 3 0 1 0 6 0a3 3 0 1 0 -6 0',
    'M3 18a3 3 0 1 0 6 0a3 3 0 1 0 -6 0',
    'M18 9a9 9 0 0 1-9 9',
  ],
  commit: ['M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0', 'M3 12h6', 'M15 12h6'],
  eye: [
    'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z',
    'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
  ],
  copy: ['M9 9h11v11H9z', 'M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1'],
}
</script>

<template>
  <svg
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-for="path in paths[props.name] ?? paths.overview" :key="path" :d="path" />
  </svg>
</template>
