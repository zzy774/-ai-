export interface LoginResponse {
  token: string; tokenType: string; expiresIn: number
  username: string; displayName: string
}
