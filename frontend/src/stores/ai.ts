import { defineStore } from 'pinia'
import axios from '@/api'
import type { AiMessage } from '@/types/ai'

export const useAiStore = defineStore('ai', {
  state: () => ({
    messages: [] as AiMessage[],
    currentProvider: 'DEEPSEEK',
    isLoading: false,
  }),
  actions: {
    async sendMessage(projectId: number, message: string, provider?: string) {
      this.isLoading = true
      this.messages.push({ role: 'user', content: message, provider: provider || this.currentProvider, createdAt: new Date().toISOString() })
      try {
        const res = await axios.post('/ai/chat', {
          projectId, message,
          provider: provider || this.currentProvider,
          includeContext: true
        }) as any
        this.messages.push({
          role: 'assistant',
          content: res.data.message,
          provider: res.data.provider,
          createdAt: new Date().toISOString()
        })
      } catch (e: any) {
        this.messages.push({
          role: 'assistant',
          content: '错误: ' + (e.response?.data?.message || e.message),
          provider: this.currentProvider,
          createdAt: new Date().toISOString()
        })
      } finally { this.isLoading = false }
    },
    async loadConversation(projectId: number) {
      try {
        const res = await axios.get(`/ai/conversations/${projectId}`) as any
        this.messages = (res.data || []).map((m: any) => ({
          role: m.role,
          content: m.content,
          provider: m.provider,
          createdAt: m.createdAt
        }))
      } catch { this.messages = [] }
    },
    clearMessages() { this.messages = [] },
  },
})
