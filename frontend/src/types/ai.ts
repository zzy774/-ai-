export interface AiMessage {
  role: 'user' | 'assistant' | 'system'
  content: string; provider: string; createdAt: string
}
