/**
 * Auth data types for VibeGraph frontend.
 *
 * Mirrors backend DTOs: auth/dto/{RegisterRequest, LoginRequest, AuthResponse}.java
 * These are Phase 1 contracts — the backend will issue JWT tokens; the FE stores
 * and attaches them to every subsequent request.
 *
 * IMPORTANT: This file is the FE half of the BE/FE auth contract.
 */

/** User profile as returned by `GET /api/auth/me` and inside `AuthResponse`. */
export interface User {
  id: string
  email: string
  displayName: string
  role: 'USER' | 'ADMIN'
  avatarUrl?: string | null
}

/** Response from `POST /api/auth/login` and `POST /api/auth/register`. */
export interface AuthResponse {
  token?: string | null
  user: User
}

/** Body for `POST /api/auth/login`. */
export interface LoginRequest {
  email: string
  password: string
}

/** Body for `POST /api/auth/register`. */
export interface RegisterRequest {
  email: string
  password: string
  displayName: string
}
