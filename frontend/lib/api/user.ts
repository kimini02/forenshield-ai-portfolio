import { apiRequest } from "@/lib/api/client"
import { features } from "@/lib/features"
import { mockFetchMyProfile, mockUpdateMyProfile } from "@/lib/mock/forensic-api"

export type UserProfile = {
  userId: number
  loginId: string
  email: string
  name: string
  department: string
  role: string
  status: string
  darkMode: boolean
  createdAt: string
}

export type UpdateUserProfilePayload = {
  loginId: string
  currentPassword: string
  newPassword?: string
}

export async function fetchMyProfile(): Promise<UserProfile> {
  if (features.mockApi) {
    return mockFetchMyProfile()
  }

  return apiRequest<UserProfile>("/api/v1/users/me")
}

export async function updateMyProfile(payload: UpdateUserProfilePayload): Promise<UserProfile> {
  if (features.mockApi) {
    return mockUpdateMyProfile(payload)
  }

  return apiRequest<UserProfile>("/api/v1/users/me", {
    method: "PATCH",
    body: payload,
  })
}
