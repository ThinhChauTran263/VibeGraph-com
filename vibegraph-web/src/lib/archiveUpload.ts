/**
 * Client-side archive upload constants and validation.
 *
 * Mirrors the backend contract for `POST /api/projects/import-archive`:
 * see `.kiro/specs/project-folder-upload/design.md`.
 *
 * Backend is the authority on these limits - this module provides a fast
 * client-side gate so the user gets immediate feedback before the upload
 * leaves the browser. The backend MUST still re-validate.
 */

import { ARCHIVE_MAX_SIZE_BYTES } from '@/lib/runtimeConfig'

/** Maximum archive size in bytes. Sourced from `VITE_ARCHIVE_MAX_SIZE_MB` (runtimeConfig). */
export { ARCHIVE_MAX_SIZE_BYTES }

/** Supported archive file extensions, lower-case, with leading dot. */
export const ARCHIVE_ALLOWED_EXTENSIONS = ['.zip', '.tar.gz', '.tgz', '.tar'] as const

/** Value for the `accept` attribute on `<input type="file">`. */
export const ARCHIVE_ACCEPT_ATTRIBUTE = ARCHIVE_ALLOWED_EXTENSIONS.join(',')

export type ArchiveValidationErrorKind = 'extension' | 'empty' | 'size'

export interface ArchiveValidationError {
  kind: ArchiveValidationErrorKind
  message: string
}

/**
 * Validate a user-selected file against the archive upload contract.
 *
 * Returns `null` on success, otherwise a structured error describing the
 * first failure encountered.
 *
 * Note: order matters. We check extension first because a non-archive file
 * is the most common user mistake; size is checked last so the message can
 * cite the exact size.
 */
export function validateArchiveFile(file: File): ArchiveValidationError | null {
  const lowerName = file.name.toLowerCase()
  const hasValidExtension = ARCHIVE_ALLOWED_EXTENSIONS.some((ext) => lowerName.endsWith(ext))
  if (!hasValidExtension) {
    return {
      kind: 'extension',
      message: `Unsupported archive type. Use ${ARCHIVE_ALLOWED_EXTENSIONS.join(', ')}.`,
    }
  }

  if (file.size === 0) {
    return { kind: 'empty', message: 'The selected archive is empty.' }
  }

  if (file.size > ARCHIVE_MAX_SIZE_BYTES) {
    const sizeMb = (file.size / (1024 * 1024)).toFixed(1)
    return {
      kind: 'size',
      message: `Archive is ${sizeMb} MB; the maximum is 100 MB.`,
    }
  }

  return null
}

/** Format a byte count as a short human-readable size (e.g. "12.4 MB"). */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
