<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from 'vue'
import { useAiStore } from '@/stores/ai'
import axios from '@/api'
import { ElMessage } from 'element-plus'

const props = defineProps<{ projectId?: string }>()
const aiStore = useAiStore()
const projects = ref<any[]>([])
const selectedProjectId = ref<number | null>(props.projectId ? Number(props.projectId) : null)
const inputMessage = ref('')
const messageContainer = ref<HTMLElement | null>(null)

const providers = [
  { label: 'DeepSeek', value: 'DEEPSEEK' },
  { label: '千问', value: 'QWEN' },
]

onMounted(async () => {
  try {
    const res = await axios.get('/projects', { params: { page: 1, size: 50 } }) as any
    projects.value = res.data.records || []
  } catch {}
  if (selectedProjectId.value) await loadHistory()
})

watch(selectedProjectId, async (n) => { if (n) await loadHistory() })

async function loadHistory() {
  if (!selectedProjectId.value) return
  await aiStore.loadConversation(selectedProjectId.value)
  scrollDown()
}

async function send() {
  if (!inputMessage.value.trim() || !selectedProjectId.value) {
    ElMessage.warning('请先选择项目并输入消息'); return
  }
  await aiStore.sendMessage(selectedProjectId.value, inputMessage.value, aiStore.currentProvider)
  inputMessage.value = ''
  scrollDown()
}

function scrollDown() {
  nextTick(() => {
    if (messageContainer.value) messageContainer.value.scrollTop = messageContainer.value.scrollHeight
  })
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title" style="margin-bottom:20px">🤖 AI 对话</h2>
    <div class="chat-layout">
      <!-- Side Panel -->
      <aside class="chat-sidebar">
        <div class="side-section">
          <label class="side-label">选择项目</label>
          <el-select v-model="selectedProjectId" placeholder="选择一个项目" style="width:100%" clearable>
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </div>
        <div class="side-section">
          <label class="side-label">AI 供应商</label>
          <div class="provider-list">
            <button v-for="p in providers" :key="p.value"
              :class="['provider-btn', { active: aiStore.currentProvider === p.value }]"
              @click="aiStore.currentProvider = p.value">
              {{ p.label }}
            </button>
          </div>
        </div>
        <button class="btn-danger-outline" @click="aiStore.clearMessages(); loadHistory()">清空对话</button>
      </aside>

      <!-- Chat Area -->
      <main class="chat-main">
        <div class="chat-body" ref="messageContainer">
          <div v-if="aiStore.messages.length === 0 && !selectedProjectId" class="empty-chat">
            <div class="empty-icon">💬</div>
            <div class="empty-title">开始 AI 对话</div>
            <div class="empty-text">先在左侧选择一个项目</div>
          </div>
          <div v-for="(msg, i) in aiStore.messages" :key="i"
            class="msg-row" :class="{ 'msg-user': msg.role === 'user' }">
            <div class="msg-bubble">
              <div class="msg-meta">
                <span>{{ msg.role === 'user' ? '我' : msg.provider }}</span>
                <span>{{ new Date(msg.createdAt).toLocaleTimeString('zh-CN', { hour:'2-digit', minute:'2-digit' }) }}</span>
              </div>
              <div class="msg-text">{{ msg.content }}</div>
            </div>
          </div>
        </div>
        <div class="chat-input">
          <input v-model="inputMessage" placeholder="输入消息…" :disabled="!selectedProjectId"
            @keyup.enter="send" class="chat-input-field" />
          <button class="btn-primary" :disabled="aiStore.isLoading || !selectedProjectId" @click="send" aria-label="发送">
            <svg v-if="!aiStore.isLoading" width="16" height="16" viewBox="0 0 16 16"><path d="M14 2L6 10M14 2l-4 12-3-6-3-3 10-3z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" fill="none"/></svg>
            <span v-else class="spinner"></span>
          </button>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.chat-layout { display:flex; gap:20px; height: calc(100vh - 160px); min-height: 500px }

/* --- Sidebar --- */
.chat-sidebar {
  width: 220px; flex-shrink:0;
  display:flex; flex-direction:column; gap:16px;
  padding:20px; background:#fff; border:1px solid #e5e5e5; border-radius:12px;
}
.side-section { display:flex; flex-direction:column; gap:6px }
.side-label { font-size:12px; font-weight:600; color:#8a8a8a; text-transform:uppercase; letter-spacing:0.04em }
.provider-list { display:flex; flex-direction:column; gap:4px }
.provider-btn {
  padding:8px 12px; border:1px solid #e5e5e5; border-radius:8px;
  background:#fff; color:#4a4a4a; font-size:13px; font-weight:500;
  font-family:inherit; cursor:pointer; text-align:left;
  transition: all 120ms ease;
  &:hover { border-color:#aaa }
  &.active { border-color:#0070f3; color:#0070f3; background:#f0f7ff }
}

/* --- Chat Main --- */
.chat-main {
  flex:1; display:flex; flex-direction:column;
  background:#fff; border:1px solid #e5e5e5; border-radius:12px;
  overflow:hidden;
}
.chat-body { flex:1; overflow-y:auto; padding:20px; display:flex; flex-direction:column; gap:16px }

.msg-row { display:flex }
.msg-row.msg-user { justify-content:flex-end }
.msg-bubble {
  max-width:75%; padding:10px 16px; border-radius:12px;
  font-size:14px; line-height:1.6;
}
.msg-user .msg-bubble { background:#171717; color:#fff; border-bottom-right-radius:4px }
.msg-row:not(.msg-user) .msg-bubble { background:#f5f5f5; color:#171717; border-bottom-left-radius:4px }
.msg-meta { display:flex; gap:8px; font-size:11px; opacity:0.5; margin-bottom:4px }
.msg-text { white-space:pre-wrap; word-break:break-word }

/* --- Input --- */
.chat-input {
  display:flex; gap:8px; padding:16px; border-top:1px solid #e5e5e5;
  background:#fafafa;
}
.chat-input-field {
  flex:1; height:44px; padding:0 16px; font-size:14px; font-family:inherit;
  border:1px solid #e5e5e5; border-radius:10px; outline:none; color:#171717;
  transition: all 150ms ease;
  &:hover { border-color:#d4d4d4 }
  &:focus { border-color:#0070f3; box-shadow:0 0 0 3px rgba(0,112,243,0.1) }
  &::placeholder { color:#b0b0b0 }
  &:disabled { background:#f5f5f5; cursor:not-allowed }
}

.btn-primary {
  display:flex; align-items:center; justify-content:center;
  width:44px; height:44px; border:none; border-radius:10px;
  background:#171717; color:#fff; font-family:inherit; cursor:pointer;
  flex-shrink:0; transition: all 150ms ease;
  &:hover:not(:disabled) { background:#2a2a2a; transform: translateY(-0.5px) }
  &:disabled { opacity:0.4; cursor:not-allowed }
}
.btn-danger-outline {
  margin-top:auto; padding:8px; border:1px solid #f5c6cb; border-radius:8px;
  background:#fff; color:#e5484d; font-size:12px; font-weight:500;
  font-family:inherit; cursor:pointer; transition: all 120ms ease;
  &:hover { background:#fef3f3 }
}

.spinner { width:16px; height:16px; border:2px solid rgba(255,255,255,0.3); border-top-color:#fff; border-radius:50%; animation: spin 600ms linear infinite }
@keyframes spin { to { transform: rotate(360deg) } }

.empty-chat { display:flex; flex-direction:column; align-items:center; justify-content:center; height:100%; gap:8px }
.empty-icon { font-size:48px } .empty-title { font-size:16px; font-weight:600; color:#171717 } .empty-text { font-size:13px; color:#8a8a8a }
</style>
