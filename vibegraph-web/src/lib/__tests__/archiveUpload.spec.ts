import { describe, it, expect } from 'vitest'
import {
  ARCHIVE_ACCEPT_ATTRIBUTE,
  ARCHIVE_ALLOWED_EXTENSIONS,
  formatFileSize,
  validateArchiveFile,
} from '../archiveUpload'

/**
 * Helper to build a fake `File` with a specific size without allocating
 * tens-of-megabytes of real bytes. The browser File API only requires a
 * BlobPart array, so we lean on a single Uint8Array sized to match.
 *
 * For large-file validation cases we cheat by overriding the `size` getter
 * to avoid allocating large buffers in jsdom.
 */
function makeFile(name: string, size: number): File {
  const bytes = size > 0 ? new Uint8Array(Math.min(size, 64)) : new Uint8Array(0)
  const file = new File([bytes], name, { type: 'application/octet-stream' })
  if (file.size !== size) {
    Object.defineProperty(file, 'size', { value: size, configurable: true })
  }
  return file
}

describe('archiveUpload constants', () => {
  it('lists exactly the four supported archive extensions', () => {
    expect([...ARCHIVE_ALLOWED_EXTENSIONS].sort()).toEqual(
      ['.zip', '.tar', '.tar.gz', '.tgz'].sort(),
    )
  })

  it('builds an accept attribute that includes every supported extension', () => {
    for (const ext of ARCHIVE_ALLOWED_EXTENSIONS) {
      expect(ARCHIVE_ACCEPT_ATTRIBUTE).toContain(ext)
    }
  })
})

describe('validateArchiveFile - allowed extensions', () => {
  it('accepts .zip', () => {
    expect(validateArchiveFile(makeFile('project.zip', 1024))).toBeNull()
  })

  it('accepts .tar', () => {
    expect(validateArchiveFile(makeFile('project.tar', 1024))).toBeNull()
  })

  it('accepts .tar.gz', () => {
    expect(validateArchiveFile(makeFile('project.tar.gz', 1024))).toBeNull()
  })

  it('accepts .tgz', () => {
    expect(validateArchiveFile(makeFile('project.tgz', 1024))).toBeNull()
  })

  it('is case-insensitive on the extension check', () => {
    expect(validateArchiveFile(makeFile('PROJECT.ZIP', 1024))).toBeNull()
    expect(validateArchiveFile(makeFile('project.TAR.GZ', 1024))).toBeNull()
  })
})

describe('validateArchiveFile - rejections', () => {
  it('rejects an unsupported extension with kind=extension', () => {
    const result = validateArchiveFile(makeFile('project.rar', 1024))
    expect(result).not.toBeNull()
    expect(result?.kind).toBe('extension')
    expect(result?.message).toContain('Unsupported archive type')
  })

  it('rejects a file with no extension at all', () => {
    const result = validateArchiveFile(makeFile('project', 1024))
    expect(result?.kind).toBe('extension')
  })

  it('rejects an empty archive even if the extension is valid', () => {
    const result = validateArchiveFile(makeFile('project.zip', 0))
    expect(result?.kind).toBe('empty')
    expect(result?.message).toContain('empty')
  })

  it('accepts a large archive and leaves quota enforcement to the backend', () => {
    expect(validateArchiveFile(makeFile('project.zip', 2 * 1024 * 1024 * 1024))).toBeNull()
  })

  it('checks extension before size so tiny non-archives still fail on extension', () => {
    // A 0-byte non-archive: extension error wins, not the empty error.
    const result = validateArchiveFile(makeFile('readme.txt', 0))
    expect(result?.kind).toBe('extension')
  })
})

describe('formatFileSize', () => {
  it('formats bytes under 1 KB with the B unit', () => {
    expect(formatFileSize(0)).toBe('0 B')
    expect(formatFileSize(1)).toBe('1 B')
    expect(formatFileSize(1023)).toBe('1023 B')
  })

  it('formats kilobytes with one decimal', () => {
    expect(formatFileSize(1024)).toBe('1.0 KB')
    expect(formatFileSize(2048)).toBe('2.0 KB')
    expect(formatFileSize(1024 * 1024 - 1)).toBe('1024.0 KB')
  })

  it('formats megabytes with one decimal', () => {
    expect(formatFileSize(1024 * 1024)).toBe('1.0 MB')
    expect(formatFileSize(10 * 1024 * 1024)).toBe('10.0 MB')
    expect(formatFileSize(100 * 1024 * 1024)).toBe('100.0 MB')
  })
})
