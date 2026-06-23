<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const form = reactive({ username: 'admin', password: 'admin123' })
const loading = ref(false)

async function handleLogin() {
  loading.value = true
  try { await delay(200); await auth.login(form.username, form.password) }
  catch {} finally { loading.value = false }
}

function delay(ms: number) { return new Promise(resolve => setTimeout(resolve, ms)) }
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <!-- Brand -->
      <div class="login-brand">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="4" y="3" width="16" height="18" rx="2" stroke="#171717" stroke-width="1.8" fill="none"/>
          <line x1="8" y1="8" x2="16" y2="8" stroke="#171717" stroke-width="1.5" stroke-linecap="round"/>
          <line x1="8" y1="12" x2="14" y2="12" stroke="#171717" stroke-width="1.5" stroke-linecap="round"/>
          <line x1="8" y1="16" x2="12" y2="16" stroke="#171717" stroke-width="1.5" stroke-linecap="round"/>
          <circle cx="17" cy="5" r="2.5" fill="#0070f3" stroke="#fff" stroke-width="1"/>
        </svg>
      </div>
      <h1 class="login-title">实验报告自动编写系统</h1>
      <p class="login-desc">Lab Report Auto-Generation System</p>

      <form class="login-form" @submit.prevent="handleLogin">
        <div class="field">
          <label for="username">用户名</label>
          <div class="input-wrap">
            <svg class="input-icon" width="16" height="16" viewBox="0 0 16 16"><circle cx="8" cy="5" r="3" stroke="#8a8a8a" stroke-width="1.5" fill="none"/><path d="M2 14c0-3.3 2.7-6 6-6s6 2.7 6 6" stroke="#8a8a8a" stroke-width="1.5" stroke-linecap="round" fill="none"/></svg>
            <input id="username" v-model="form.username" type="text" placeholder="输入用户名" autocomplete="username" />
          </div>
        </div>
        <div class="field">
          <label for="password">密码</label>
          <div class="input-wrap">
            <svg class="input-icon" width="16" height="16" viewBox="0 0 16 16"><rect x="3" y="7" width="10" height="7" rx="1.5" stroke="#8a8a8a" stroke-width="1.5" fill="none"/><circle cx="8" cy="10.5" r="1" stroke="#8a8a8a" stroke-width="1.5" fill="none"/><line x1="8" y1="3" x2="8" y2="7" stroke="#8a8a8a" stroke-width="1.5" stroke-linecap="round"/></svg>
            <input id="password" v-model="form.password" type="password" placeholder="输入密码" autocomplete="current-password" />
          </div>
        </div>
        <button type="submit" class="btn-login" :class="{ loading }" :disabled="loading">
          <span v-if="!loading" class="btn-text">登 录</span>
          <span v-else class="btn-loading">
            <span class="spinner"></span>
            登录中…
          </span>
        </button>
      </form>

      <p class="login-hint">默认账户 admin / admin123</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
  padding: 24px;
}

.login-card {
  width: 400px;
  padding: 40px 36px;
  background: #fff;
  border: 1px solid #e5e5e5;
  border-radius: 16px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.02), 0 8px 24px rgba(0,0,0,0.06);
}

.login-brand {
  text-align: center;
  margin-bottom: 20px;
}

.login-title {
  font-size: 20px;
  font-weight: 700;
  color: #171717;
  text-align: center;
  letter-spacing: -0.025em;
  text-wrap: balance;
}

.login-desc {
  font-size: 13px;
  color: #8a8a8a;
  text-align: center;
  margin-top: 4px;
  margin-bottom: 32px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field label {
  font-size: 13px;
  font-weight: 600;
  color: #4a4a4a;
  letter-spacing: -0.01em;
}

.input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 12px;
  pointer-events: none;
  flex-shrink: 0;
}

.input-wrap input {
  width: 100%;
  height: 44px;
  padding: 0 12px 0 38px;
  font-size: 14px;
  font-family: inherit;
  color: #171717;
  background: #fff;
  border: 1px solid #e5e5e5;
  border-radius: 8px;
  transition: all 150ms ease;
  outline: none;
  &:hover { border-color: #d4d4d4; }
  &:focus { border-color: #0070f3; box-shadow: 0 0 0 3px rgba(0,112,243,0.12); }
  &::placeholder { color: #b0b0b0; }
}

.btn-login {
  width: 100%;
  height: 46px;
  background: #171717;
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  letter-spacing: 0.02em;
  transition: all 150ms ease;
  margin-top: 4px;
  &:hover:not(:disabled) {
    background: #2a2a2a;
    transform: translateY(-0.5px);
    box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  }
  &:active:not(:disabled) { transform: translateY(0.5px); }
  &:focus-visible { outline: 2px solid #0070f3; outline-offset: 2px; }
  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
  }
  &.loading {
    background: #2a2a2a;
  }
}

.btn-text { display: block; }

.btn-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.spinner {
  width: 16px; height: 16px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 600ms linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.login-hint {
  text-align: center;
  font-size: 12px;
  color: #b0b0b0;
  margin-top: 24px;
}
</style>
