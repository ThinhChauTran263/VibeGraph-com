export type Translate = (key: string) => string

const KNOWN_PLAN_CODES = new Set(['FREE', 'PRO', 'PRO_PLUS', 'MAX', 'ENTERPRISE'])
const DEFAULT_PLAN_NAMES: Record<string, string> = {
  FREE: 'Free',
  PRO: 'Pro',
  PRO_PLUS: 'Pro Plus',
  MAX: 'Max',
  ENTERPRISE: 'Enterprise',
}

export function displayPlanName(
  t: Translate,
  planCode?: string | null,
  planName?: string | null,
  fallback = '',
): string {
  const code = planCode?.trim().toUpperCase()
  const name = planName?.trim()
  if (name && (!code || DEFAULT_PLAN_NAMES[code]?.toLowerCase() !== name.toLowerCase())) return name
  if (code && KNOWN_PLAN_CODES.has(code)) return t(`planNames.${code}`)
  return name || planCode?.trim() || fallback
}
