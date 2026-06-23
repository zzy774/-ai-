import { defineStore } from 'pinia'
import axios from '@/api'
import type { LoginResponse } from '@/types/auth'
import { ElMessage } from 'element-plus'
import router from '@/router'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: '',
    username: '',
    displayName: '',
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
  },
  actions: {
    async login(username: string, password: string) {
      const res = await axios.post('/auth/login', { username, password }) as any
      const data = res.data as LoginResponse
      this.token = data.token
      this.username = data.username
      this.displayName = data.displayName
      localStorage.setItem('token', data.token)
      ElMessage.success('登录成功')
      router.push({ name: 'projects' })
    },
    async logout() {
      try { await axios.post('/auth/logout') } catch {}
      this.token = ''
      this.username = ''
      this.displayName = ''
      localStorage.removeItem('token')
      router.push({ name: 'login' })
    },
  },
  persist: {
    key: 'auth-store',
    storage: localStorage,
    pick: ['token', 'username', 'displayName'],
  },
})
