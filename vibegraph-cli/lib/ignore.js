/**
 * Ignore rules engine for vibegraph-cli.
 * Implements .vibegraphignore + hardcoded deny-list + binary detection.
 * No external dependencies — uses simple glob/prefix matching.
 */

import { readFile, open } from "node:fs/promises";
import path from "node:path";

/** Default deny-list — always applied regardless of .vibegraphignore */
const DEFAULT_DENY = [
  // Directories
  ".git/**",
  "node_modules/**",
  "dist/**",
  "build/**",
  "target/**",
  "out/**",
  "bin/**",
  // Secrets
  ".env",
  ".env.*",
  "*.pem",
  "*.key",
  "id_rsa",
  "id_dsa",
  "id_ed25519",
  // Archives
  "*.zip",
  "*.tar",
  "*.tgz",
  "*.gz",
  "*.rar",
  "*.7z",
];

/** Default max file size: 1 MB */
const DEFAULT_MAX_FILE_SIZE = 1 * 1024 * 1024;

/** Default max files per push */
const DEFAULT_MAX_FILES = 200;

/**
 * Load ignore rules from .vibegraphignore file + default deny-list.
 * @param {string} rootDir - Project root directory (absolute path)
 * @returns {Promise<string[]>} Merged list of ignore patterns
 */
export async function loadIgnoreRules(rootDir) {
  const rules = [...DEFAULT_DENY];

  const ignoreFile = path.join(rootDir, ".vibegraphignore");
  try {
    const content = await readFile(ignoreFile, "utf8");
    for (const line of content.split(/\r?\n/)) {
      const trimmed = line.trim();
      if (trimmed && !trimmed.startsWith("#")) {
        rules.push(trimmed);
      }
    }
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
    // No .vibegraphignore — use defaults only
  }

  return rules;
}

/**
 * Check if a relative POSIX path should be ignored.
 * @param {string} relativePath - Relative POSIX path (forward slashes)
 * @param {string[]} rules - List of ignore patterns
 * @returns {{ ignored: boolean, reason?: string }}
 */
export function shouldIgnore(relativePath, rules) {
  for (const pattern of rules) {
    if (matchPattern(relativePath, pattern)) {
      return { ignored: true, reason: categorizeReason(pattern) };
    }
  }
  return { ignored: false };
}

/**
 * Detect if a file is binary by reading the first 8KB and checking for NUL bytes.
 * @param {string} absolutePath - Full file path
 * @returns {Promise<boolean>}
 */
export async function isBinaryFile(absolutePath) {
  let fh;
  try {
    fh = await open(absolutePath, "r");
    const buf = Buffer.alloc(8192);
    const { bytesRead } = await fh.read(buf, 0, 8192, 0);
    for (let i = 0; i < bytesRead; i++) {
      if (buf[i] === 0) return true;
    }
    return false;
  } catch {
    return false; // If we can't read, let the scanner handle it
  } finally {
    if (fh) await fh.close();
  }
}

/**
 * Get the max file size from env or default.
 * @returns {number} Max file size in bytes
 */
export function getMaxFileSize() {
  const envVal = process.env.VIBEGRAPH_MAX_FILE_SIZE;
  if (envVal) {
    const parsed = parseInt(envVal, 10);
    if (!isNaN(parsed) && parsed > 0) return parsed;
  }
  return DEFAULT_MAX_FILE_SIZE;
}

/**
 * Get the max files per push from env or default.
 * @returns {number}
 */
export function getMaxFiles() {
  const envVal = process.env.VIBEGRAPH_MAX_FILES;
  if (envVal) {
    const parsed = parseInt(envVal, 10);
    if (!isNaN(parsed) && parsed > 0) return parsed;
  }
  return DEFAULT_MAX_FILES;
}

/**
 * Generate default .vibegraphignore content.
 * @returns {string}
 */
export function generateDefaultIgnoreContent() {
  return `# VibeGraph ignore rules
# Lines starting with # are comments.
# Patterns use simple glob matching (*, **, ?).

# Directories
.git/**
node_modules/**
dist/**
build/**
target/**
out/**
bin/**

# IDE / Editor
.idea/**
.vscode/**
*.iml

# Secrets & credentials
.env
.env.*
*.pem
*.key
id_rsa
id_dsa
id_ed25519

# Archives
*.zip
*.tar
*.tgz
*.gz
*.rar
*.7z

# Logs
*.log

# OS files
.DS_Store
Thumbs.db
`;
}

// --- Internal matching helpers ---

/**
 * Simple glob pattern matcher.
 * Supports: ** (any path segments), * (any chars in one segment), ? (single char)
 * Also matches plain prefix patterns like "node_modules/**".
 */
function matchPattern(filePath, pattern) {
  // Handle negation (!) — not used in default deny but supported
  if (pattern.startsWith("!")) return false;

  // Normalize: remove trailing slashes from pattern
  const normalizedPattern = pattern.replace(/\/+$/, "");

  // Direct exact match
  if (filePath === normalizedPattern) return true;

  // If pattern has no glob chars, treat as prefix/exact
  if (!normalizedPattern.includes("*") && !normalizedPattern.includes("?")) {
    // Exact filename match (basename)
    const basename = filePath.split("/").pop();
    if (basename === normalizedPattern) return true;
    // Prefix directory match
    if (filePath.startsWith(normalizedPattern + "/")) return true;
    return false;
  }

  // Convert glob pattern to regex
  const regex = globToRegex(normalizedPattern);
  if (regex.test(filePath)) return true;

  // For patterns without a slash (e.g. "*.pem", "*.zip", ".env.*"),
  // also test against the basename so nested files are caught at any depth.
  if (!normalizedPattern.includes("/")) {
    const basename = filePath.split("/").pop();
    if (regex.test(basename)) return true;
  }

  return false;
}

function globToRegex(pattern) {
  let regexStr = "^";
  let i = 0;

  while (i < pattern.length) {
    const char = pattern[i];

    if (char === "*") {
      if (pattern[i + 1] === "*") {
        // ** matches any number of path segments
        if (pattern[i + 2] === "/") {
          regexStr += "(?:.*/)?";
          i += 3;
        } else {
          regexStr += ".*";
          i += 2;
        }
      } else {
        // * matches anything except /
        regexStr += "[^/]*";
        i += 1;
      }
    } else if (char === "?") {
      regexStr += "[^/]";
      i += 1;
    } else if (char === ".") {
      regexStr += "\\.";
      i += 1;
    } else {
      regexStr += char;
      i += 1;
    }
  }

  regexStr += "$";
  return new RegExp(regexStr);
}

function categorizeReason(pattern) {
  if (pattern.includes(".env")) return "secret pattern";
  if (pattern.includes(".pem") || pattern.includes(".key") ||
      pattern.includes("id_rsa") || pattern.includes("id_dsa") || pattern.includes("id_ed25519")) {
    return "secret pattern";
  }
  if (pattern.includes(".git")) return "VCS directory";
  if (pattern.includes("node_modules")) return "dependency directory";
  if (["*.zip", "*.tar", "*.tgz", "*.gz", "*.rar", "*.7z"].includes(pattern)) return "archive file";
  if (["dist/**", "build/**", "target/**", "out/**", "bin/**"].includes(pattern)) return "build output";
  return "ignore rule";
}
