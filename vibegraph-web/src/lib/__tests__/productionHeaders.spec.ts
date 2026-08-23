import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('production security headers', () => {
  it('allows the external stylesheets and font files used by the application', () => {
    const nginxConfig = readFileSync(resolve(process.cwd(), 'nginx.conf.template'), 'utf8')
    const fontPolicy =
      "font-src 'self' data: https://fonts.gstatic.com; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com"

    expect(nginxConfig.split(fontPolicy)).toHaveLength(3)
  })

  it('allows SockJS to register its same-origin unload cleanup handler', () => {
    const nginxConfig = readFileSync(resolve(process.cwd(), 'nginx.conf.template'), 'utf8')

    expect(nginxConfig.split('unload=(self)')).toHaveLength(3)
  })
})
