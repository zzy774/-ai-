<script setup lang="ts">
import { ref, onMounted } from 'vue'
import axios from '@/api'
import { ElMessage } from 'element-plus'

const providers = [
  { name: 'DeepSeek', key: 'ai.deepseek.api_key', desc: '从 platform.deepseek.com 获取 API Key' },
  { name: '千问 (Qwen)', key: 'ai.qwen.api_key', desc: '从 dashscope.aliyun.com 获取 API Key' },
]
const apiKeys = ref<Record<string, string>>({})
const defaultProvider = ref('DEEPSEEK')
const saving = ref(false)
const saveState = ref<'idle' | 'saving' | 'saved'>('idle')

onMounted(async () => {
  try { await axios.get('/ai/providers') } catch {}
})

async function save() {
  saving.value = true; saveState.value = 'saving'
  try {
    for (const p of providers) {
      if (apiKeys.value[p.key] && apiKeys.value[p.key] !== '••••••') {
        await axios.put('/projects/1', { configJson: JSON.stringify({ [p.key]: apiKeys.value[p.key] }) })
      }
    }
    saveState.value = 'saved'
    setTimeout(() => { saveState.value = 'idle' }, 2000)
    ElMessage.success('设置已保存')
  } catch {} finally { saving.value = false }
}

function getSaveLabel() {
  if (saveState.value === 'saving') return '保存中…'
  if (saveState.value === 'saved') return '✓ 已保存'
  return '保存设置'
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title" style="margin-bottom:20px">⚙ 系统设置</h2>

    <!-- Provider Cards -->
    <div class="setting-section">
      <h3 class="section-title">AI 供应商配置</h3>
      <div class="alert-info" role="alert">
        <svg width="16" height="16" viewBox="0 0 16 16"><circle cx="8" cy="8" r="7" stroke="currentColor" stroke-width="1.5" fill="none"/><line x1="8" y1="5" x2="8" y2="9" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><circle cx="8" cy="11" r="0.8" fill="currentColor"/></svg>
        <span>API Key 仅存储在本机数据库中，不会上传到任何远程服务</span>
      </div>

      <div class="provider-grid">
        <div v-for="p in providers" :key="p.key" class="provider-card">
          <div class="provider-header">{{ p.name }}</div>
          <p class="provider-desc">{{ p.desc }}</p>
          <div class="input-wrap">
            <input :id="p.key" v-model="apiKeys[p.key]" type="password" placeholder="输入 API Key" class="field-input" />
          </div>
        </div>
      </div>
    </div>

    <!-- Default Provider -->
    <div class="setting-section">
      <h3 class="section-title">默认 AI 供应商</h3>
      <div class="radio-group">
        <label v-for="opt in [{v:'DEEPSEEK',l:'DeepSeek'},{v:'QWEN',l:'千问'}]" :key="opt.v" class="radio-item">
          <input type="radio" v-model="defaultProvider" :value="opt.v" />
          <span class="radio-mark"></span>
          <span>{{ opt.l }}</span>
        </label>
      </div>
    </div>

    <button class="btn-save" :disabled="saving" @click="save">
      {{ getSaveLabel() }}
    </button>
  </div>
</template>

<style scoped>
.setting-section {
  background:#fff; border:1px solid #e5e5e5; border-radius:12px;
  padding:24px; margin-bottom:20px;
}
.section-title { font-size:15px; font-weight:700; color:#171717; letter-spacing:-0.01em; margin-bottom:16px }

.alert-info {
  display:flex; align-items:center; gap:8px; padding:10px 14px;
  background:#f0f7ff; border:1px solid #cce0ff; border-radius:8px;
  font-size:13px; color:#0070f3; margin-bottom:20px
}

.provider-grid { display:grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap:16px }
.provider-card {
  padding:20px; border:1px solid #e5e5e5; border-radius:10px;
  transition: box-shadow 150ms ease;
  &:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.06) }
}
.provider-header { font-size:14px; font-weight:700; color:#171717; margin-bottom:6px }
.provider-desc { font-size:12px; color:#8a8a8a; margin-bottom:12px; line-height:1.5 }
.field-input {
  width:100%; height:40px; padding:0 12px; font-size:13px; font-family:inherit;
  border:1px solid #e5e5e5; border-radius:8px; outline:none; color:#171717;
  transition: all 150ms ease;
  &:hover { border-color:#d4d4d4 }
  &:focus { border-color:#0070f3; box-shadow:0 0 0 3px rgba(0,112,243,0.1) }
  &::placeholder { color:#b0b0b0 }
}

/* --- Radio --- */
.radio-group { display:flex; gap:16px }
.radio-item {
  display:flex; align-items:center; gap:8px; cursor:pointer;
  font-size:14px; color:#4a4a4a; font-weight:500;
  input[type="radio"] { display:none }
  .radio-mark {
    width:18px; height:18px; border:2px solid #d4d4d4; border-radius:50%;
    display:flex; align-items:center; justify-content:center;
    transition: all 150ms ease;
    &::after { content:''; width:8px; height:8px; border-radius:50%; background:transparent; transition: background 150ms ease }
  }
  input:checked + .radio-mark { border-color:#0070f3; &::after { background:#0070f3 } }
  &:hover .radio-mark { border-color:#aaa }
}

/* --- Save Btn --- */
.btn-save {
  display:inline-flex; align-items:center; gap:8px;
  padding:10px 28px; border:none; border-radius:10px;
  background:#171717; color:#fff; font-size:14px; font-weight:600;
  font-family:inherit; cursor:pointer;
  transition: all 150ms ease;
  &:hover:not(:disabled) { background:#2a2a2a; transform:translateY(-0.5px); box-shadow:0 2px 8px rgba(0,0,0,0.15) }
  &:disabled { opacity:0.5; cursor:not-allowed }
  &:focus-visible { outline:2px solid #0070f3; outline-offset:2px }
}
</style>
