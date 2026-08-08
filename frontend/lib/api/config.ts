const configuredApiBaseUrl =
  process.env.NEXT_PUBLIC_API_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL

// API clients append /api/v1 themselves, so accept legacy values ending in /api.
export const API_BASE_URL =
  configuredApiBaseUrl?.replace(/\/+$/, "").replace(/\/api$/i, "") ??
  "http://localhost:8080"
