<script setup lang="ts">
import { ref, watch } from 'vue'
import axios from '@/api'
import { ElMessage } from 'element-plus'

const plantUmlDsl = ref(`@startuml
skinparam backgroundColor #FEFEFE
skinparam classBorderColor #0070f3
skinparam classFontColor #171717
skinparam classFontName Inter

class Student {
  - id: Long
  - name: String
  + getId(): Long
}

class Course {
  - courseId: String
  - courseName: String
}

Student "n" -- "m" Course : enrolls >
@enduml`)

const imageUrl = ref('')
const loading = ref(false)
let debounceTimer: number | null = null

async function renderUml() {
  loading.value = true
  try {
    const res = await axios.post('/uml/generate', { plantUmlDsl: plantUmlDsl.value }) as any
    imageUrl.value = res.data.imageUrl
  } catch (e: any) { ElMessage.error('渲染失败: ' + (e?.response?.data?.message || e?.message)) }
  finally { loading.value = false }
}

watch(plantUmlDsl, () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = window.setTimeout(renderUml, 1200)
})

renderUml()

async function downloadImage() {
  if (!imageUrl.value) return
  const a = document.createElement('a')
  a.href = imageUrl.value; a.download = 'uml-diagram.png'; a.click()
}
</script>

<template>
  <div class="page-container">
    <div class="topbar">
      <h2 class="page-title">📐 UML 编辑器</h2>
      <div class="topbar-actions">
        <button class="btn-outline" :disabled="!imageUrl" @click="downloadImage">💾 下载 PNG</button>
        <button class="btn-primary" :disabled="loading" @click="renderUml">
          {{ loading ? '渲染中…' : '▶ 手动渲染' }}
        </button>
      </div>
    </div>
    <el-row :gutter="20" class="editor-row">
      <el-col :span="12">
        <div class="editor-panel">
          <div class="panel-header">PlantUML DSL</div>
          <textarea class="code-editor" v-model="plantUmlDsl" spellcheck="false" placeholder="@startuml … @enduml"></textarea>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="editor-panel preview-panel">
          <div class="panel-header">预览</div>
          <div class="preview-body" :class="{ loading }">
            <img v-if="imageUrl" :src="imageUrl" alt="UML Diagram" class="preview-img" />
            <div v-else class="empty-state">编写 DSL 代码，预览自动刷新</div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.topbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:20px; flex-wrap:wrap; gap:12px }
.topbar-actions { display:flex; gap:8px }

/* --- Button Styles (shared) --- */
.btn-primary {
  display: inline-flex; align-items: center; gap:6px; padding:8px 20px;
  border:none; border-radius:8px; background:#171717; color:#fff;
  font-size:14px; font-weight:600; font-family:inherit; cursor:pointer;
  transition: all 150ms ease;
  &:hover:not(:disabled) { background:#2a2a2a; transform: translateY(-0.5px); box-shadow: 0 2px 8px rgba(0,0,0,0.15) }
  &:active:not(:disabled) { transform: translateY(0.5px) }
  &:disabled { opacity:0.5; cursor:not-allowed }
  &:focus-visible { outline: 2px solid #0070f3; outline-offset: 2px }
}
.btn-outline {
  display: inline-flex; align-items:center; gap:6px; padding:8px 16px;
  border:1px solid #d4d4d4; border-radius:8px; background:#fff; color:#4a4a4a;
  font-size:13px; font-weight:500; font-family:inherit; cursor:pointer;
  transition: all 150ms ease;
  &:hover:not(:disabled) { border-color:#aaa; background:#fafafa }
  &:disabled { opacity:0.4; cursor:not-allowed }
  &:focus-visible { outline: 2px solid #0070f3; outline-offset: 2px }
}

/* --- Editor Panels --- */
.editor-row { height: calc(100vh - 160px); min-height: 500px }
.editor-panel {
  height: 100%; display:flex; flex-direction:column;
  background:#fff; border:1px solid #e5e5e5; border-radius:12px;
  overflow:hidden; box-shadow: 0 1px 2px rgba(0,0,0,0.03)
}
.panel-header {
  padding:12px 16px; font-size:13px; font-weight:600; color:#4a4a4a;
  background:#f8f8f8; border-bottom:1px solid #e5e5e5; letter-spacing: 0.02em;
}
.code-editor {
  flex:1; padding:16px; border:none; outline:none; resize:none;
  font-family: 'Geist Mono', Consolas, monospace; font-size:13px;
  line-height:1.7; color:#171717; background:#fafafa;
  &::placeholder { color:#b0b0b0 }
  &:focus { background:#fff }
}

.preview-panel { background:#f8f8f8 }
.preview-body {
  flex:1; display:flex; align-items:center; justify-content:center;
  padding:20px; overflow:auto;
  &.loading { opacity:0.6 }
}
.preview-img { max-width:100%; max-height:100%; border-radius:4px }
.empty-state { color:#8a8a8a; font-size:14px; text-align:center }
</style>
