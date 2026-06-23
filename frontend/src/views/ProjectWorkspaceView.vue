<script setup lang="ts">
import { ref, onMounted, computed, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useProjectStore, cancelUpload } from '@/stores/project'
import type { UploadProgress } from '@/stores/project'
import axios from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps<{ id: string }>()
const router = useRouter()
const store = useProjectStore()
const projectId = Number(props.id)

const activeTab = ref('files')
const project = ref<any>(null)
const uploading = ref(false)

// 上传进度（来自 store）
const progress = ref<UploadProgress>({
  total: 0, uploaded: 0, failed: 0, currentBatch: 0, totalBatches: 0,
  failedFiles: [], status: 'idle'
})

// UML
const umlImages = ref<string[]>([])
const umlGenerating = ref(false)
const umlPreviewUrl = ref('')
const umlDsl = ref(`@startuml
skinparam classBorderColor #2196F3
class Student { - id: Long; - name: String; + getId(): Long }
class Course { - courseId: String; - courseName: String }
Student "n" -- "m" Course
@enduml`)

// Template（仅 TEMPLATE 类型）
const hasTemplate = computed(() => store.files.some((f: any) =>
  f.fileType === 'TEMPLATE'))

// AI Preview
const aiPreview = ref<any>(null)
const previewing = ref(false)
const generating = ref(false)
const previewError = ref('')

// Latest report
const latestReport = ref<any>(null)

onMounted(async () => {
  try { const res = await axios.get(`/projects/${projectId}`) as any; project.value = res.data } catch {}
  await store.fetchProjectFiles(projectId)
  await loadLatestReport()
})

let reportPollTimer: any = null

async function loadLatestReport() {
  try {
    const res = await axios.get(`/reports/history?projectId=${projectId}`) as any
    if (res.data?.length) {
      latestReport.value = res.data[0]
      // If still running, poll every 5s
      if (latestReport.value.status === 'RUNNING' || latestReport.value.status === 'PENDING') {
        if (!reportPollTimer) {
          reportPollTimer = setInterval(async () => {
            try {
              const r = await axios.get(`/reports/status/${latestReport.value.taskId}`) as any
              latestReport.value = r.data
              if (r.data.status === 'COMPLETED' || r.data.status === 'FAILED') {
                clearInterval(reportPollTimer)
                reportPollTimer = null
                ElMessage.success(r.data.status === 'COMPLETED' ? '报告生成完成' : '报告生成失败')
              }
            } catch { clearInterval(reportPollTimer); reportPollTimer = null }
          }, 5000)
        }
      } else {
        if (reportPollTimer) { clearInterval(reportPollTimer); reportPollTimer = null }
      }
    }
  } catch {}
}

onBeforeUnmount(() => {
  if (reportPollTimer) clearInterval(reportPollTimer)
})

// 当前正在上传的类型（用于进度条标题）
const uploadType = ref<'code'|'template'|'image'|'sample'|'folder'|null>(null)

const templateFiles = computed(() => store.files.filter((f: any) => f.fileType === 'TEMPLATE'))
const codeFiles = computed(() => store.files.filter((f: any) => f.fileType === 'SOURCE_CODE'))
const imageFiles = computed(() => store.files.filter((f: any) => f.fileType === 'IMAGE'))
const sampleFiles = computed(() => store.files.filter((f: any) => f.fileType === 'SAMPLE'))

// 把 fileType 映射到上传类型
const TYPE_MAP: Record<string, string> = { code: 'SOURCE_CODE', template: 'TEMPLATE', image: 'IMAGE', sample: 'SAMPLE' }

// ---- 代码上传 ----
function selectCodeFolder() {
  pickFiles({ webkitdirectory: true }, 'code')
}
// ---- 模板上传 ----
function selectTemplate() {
  pickFiles({ accept: '.docx,.doc,.pdf,.txt' }, 'template')
}
// ---- 图片上传 ----
function selectImages() {
  pickFiles({ accept: '.png,.jpg,.jpeg,.gif,.bmp,.svg,.webp', multiple: true }, 'image')
}
// ---- 参考样例上传 ----
function selectSample() {
  pickFiles({ accept: '.docx,.doc,.pdf' }, 'sample')
}

function pickFiles(attrs: Record<string,any>, type: 'code'|'template'|'image'|'sample') {
  const input = document.createElement('input')
  input.type = 'file'
  Object.entries(attrs).forEach(([k,v]) => input.setAttribute(k, String(v)))
  input.onchange = (e: any) => {
    const files = e.target.files; if (!files || files.length === 0) return
    const fileArr: File[] = []; const paths: string[] = []
    for (let i = 0; i < files.length; i++) {
      const f = files[i]; if (f.size === 0) continue
      fileArr.push(f)
      const relPath = (f as any).webkitRelativePath || f.name
      paths.push(relPath)
    }
    uploadType.value = type
    startUpload(fileArr, paths, TYPE_MAP[type])
  }
  input.click()
}

async function startUpload(files: File[], paths: string[], fileType?: string) {
  uploading.value = true
  progress.value = { total: files.length, uploaded: 0, failed: 0, currentBatch: 0, totalBatches: Math.ceil(files.length / 20), failedFiles: [], status: 'uploading' }
  ElMessage.info(`开始上传 ${files.length} 个文件，共 ${Math.ceil(files.length / 20)} 批`)

  await store.uploadFilesChunked(projectId, files, paths, (p) => {
    progress.value = p
  }, fileType)
  uploading.value = false
  uploadType.value = null
}

function handleCancel() { cancelUpload(); ElMessage.info('正在取消…') }

// 进度百分比
const progressPercent = computed(() => {
  if (progress.value.total === 0) return 0
  return Math.round((progress.value.uploaded + progress.value.failed) / progress.value.total * 100)
})

// ---- AI Preview ----
async function previewReport() {
  previewing.value = true; previewError.value = ''; aiPreview.value = null
  try { const res = await axios.post('/reports/preview', { projectId }) as any; aiPreview.value = res.data; activeTab.value = 'preview'; ElMessage.success('AI内容预览已加载') }
  catch (e: any) { previewError.value = e?.response?.data?.message || e?.message || '预览失败' }
  finally { previewing.value = false }
}

// ---- Generate ----
async function generateReport() {
  generating.value = true
  try {
    const body: any = { projectId }; if (umlImages.value.length > 0) body.umlImages = umlImages.value
    const res = await axios.post('/reports/generate', body) as any
    ElMessage.success('报告任务已提交: ' + res.data.taskId)
    await loadLatestReport(); ElMessage.success('报告生成任务已提交，请在「报告」标签页查看进度')
  } catch (e: any) { ElMessage.error('生成失败: ' + (e?.response?.data?.message || e?.message)) }
  finally { generating.value = false }
}

// ---- UML ----
async function generateUmlFromFile(fileId: number, language: string) { umlGenerating.value = true; try { const res = await axios.post('/uml/from-code', { fileId, language, provider: 'DEEPSEEK' }) as any; umlImages.value.push(res.data.imageFileName); umlPreviewUrl.value = res.data.imageUrl; ElMessage.success('UML类图已生成') } catch (e: any) { ElMessage.error('UML生成失败: ' + (e?.response?.data?.message || e?.message)) } finally { umlGenerating.value = false } }
async function generateUmlFromDsl(dsl: string) { umlGenerating.value = true; try { const res = await axios.post('/uml/generate', { plantUmlDsl: dsl }) as any; umlImages.value.push(res.data.imageFileName); umlPreviewUrl.value = res.data.imageUrl; ElMessage.success('UML图已生成') } catch (e: any) { ElMessage.error('渲染失败: ' + (e?.response?.data?.message || e?.message)) } finally { umlGenerating.value = false } }
function removeUmlImage(index: number) { umlImages.value.splice(index, 1); if (umlImages.value.length === 0) umlPreviewUrl.value = '' }
function downloadReport() { if (latestReport.value) window.open(`/api/reports/${latestReport.value.id}/download`, '_blank') }

function formatSize(bytes: any) { if (!bytes) return '—'; const n = Number(bytes); return (n / 1024).toFixed(1) + ' KB' }

function getDisplayName(file: any) { return file.folderPath || file.originalName }
function getLang(file: any) { const name = file.folderPath || file.originalName; const ext = name.split('.').pop()?.toLowerCase(); const m: Record<string,string> = { java:'Java', py:'Python', cpp:'C++', c:'C', h:'C/H', js:'JS', ts:'TS', html:'HTML', css:'CSS', sql:'SQL', xml:'XML', json:'JSON', go:'Go', rs:'Rust', docx:'DOCX', doc:'DOC', pdf:'PDF', md:'MD', txt:'TXT' }; return m[ext||''] || ext || '—' }
function getFileTypeLabel(file: any) {
  if (file.fileType === 'TEMPLATE') return '📄 模板'
  if (file.fileType === 'SOURCE_CODE') return '💻 代码'
  if (file.fileType === 'IMAGE') return '🖼 图片'
  if (file.fileType === 'SAMPLE') return '📋 样例'
  return '📎 其他'
}
</script>

<template>
  <div class="page-container">
    <!-- Top Bar -->
    <div class="workspace-topbar">
      <div class="topbar-left">
        <h2 class="page-title">{{ project?.name || '项目工作区' }}</h2>
        <span v-if="project" class="status-badge" :class="project.status">
          {{ (project.status === 'DRAFT' ? '草稿' : project.status === 'COMPLETED' ? '已完成' : project.status === 'GENERATING' ? '生成中' : '失败') }}
        </span>
      </div>
      <div class="topbar-right">
        <span v-if="umlImages.length" class="uml-count">📐 {{ umlImages.length }}图</span>
        <button class="btn-outline" :disabled="!hasTemplate" @click="previewReport" :title="hasTemplate ? '让AI分析模板和代码，预览将要生成的内容' : '请先上传DOCX模板和代码文件'">
          {{ previewing ? 'AI思考中…' : '👁 AI预览' }}
        </button>
        <button v-if="latestReport" class="btn-outline" @click="downloadReport">💾 下载报告</button>
        <button class="btn-primary btn-large" :disabled="generating" @click="generateReport">
          <span v-if="generating"><span class="spinner"></span>生成中…</span>
          <span v-else>🚀 生成报告</span>
        </button>
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <!-- Files Tab -->
      <el-tab-pane label="📂 文件" name="files">
        <!-- 上传进度条 -->
        <div v-if="uploading" class="upload-progress-wrap" style="margin-bottom:24px">
          <div class="upload-progress-info">
            <span class="prog-text">
              {{ progress.status === 'done' ? '✅ 上传完成' : progress.status === 'cancelled' ? '⏹ 已取消' : `⏳ 第 ${progress.currentBatch}/${progress.totalBatches} 批` }}
              <span v-if="uploadType" style="font-weight:400;color:#8a8a8a;font-size:12px;margin-left:8px">{{ uploadType === 'code' ? '代码' : uploadType === 'template' ? '模板' : uploadType === 'image' ? '图片' : '样例' }}</span>
            </span>
            <span class="prog-count">{{ progress.uploaded + progress.failed }} / {{ progress.total }} 文件</span>
          </div>
          <div class="prog-bar-track">
            <div class="prog-bar-fill" :style="{ width: progressPercent + '%' }"
              :class="{ done: progress.status === 'done', failed: progress.failed > 0 && progress.status === 'done' }"></div>
          </div>
          <div class="prog-sub">
            <span>成功 {{ progress.uploaded }}</span>
            <span v-if="progress.failed > 0" class="prog-fail">失败 {{ progress.failed }}</span>
            <span class="prog-pct">{{ progressPercent }}%</span>
          </div>
          <button v-if="progress.status === 'uploading'" class="btn-sm btn-danger" style="margin-top:8px" @click="handleCancel">✕ 取消</button>
          <div v-if="progress.status === 'done' && progress.failedFiles.length > 0" class="failed-list">
            <div class="failed-title">⚠ {{ progress.failedFiles.length }} 个文件失败：</div>
            <div v-for="(name, i) in progress.failedFiles.slice(0, 6)" :key="i" class="failed-item">{{ name }}</div>
          </div>
        </div>

        <!-- 三卡上传区 -->
        <div class="upload-cards">
          <!-- 卡1: 模板 -->
          <div class="upload-card card-template" @click="selectTemplate()" v-if="!uploading">
            <div class="card-icon">
              <svg width="36" height="36" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <rect x="3" y="2" width="18" height="20" rx="2" stroke="currentColor" stroke-width="1.5" fill="none"/>
                <line x1="7" y1="7" x2="17" y2="7" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                <line x1="7" y1="11" x2="15" y2="11" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                <line x1="7" y1="15" x2="13" y2="15" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
              </svg>
            </div>
            <div class="card-title">📄 DOCX 模板</div>
            <div class="card-desc">上传模板（.docx / .doc / .pdf / .txt）</div>
            <div class="card-count" v-if="templateFiles.length">{{ templateFiles.length }} 个模板</div>
            <div class="card-count empty" v-else>未上传</div>
          </div>

          <!-- 卡2: 代码 -->
          <div class="upload-card card-code" @click="selectCodeFolder()" v-if="!uploading">
            <div class="card-icon">
              <svg width="36" height="36" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <rect x="3" y="3" width="18" height="18" rx="3" stroke="currentColor" stroke-width="1.5" fill="none"/>
                <path d="M8 8l-2 4 2 4M16 8l2 4-2 4M13 7l-2 10" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </div>
            <div class="card-title">💻 代码文件</div>
            <div class="card-desc">上传整个代码工程文件夹</div>
            <div class="card-count" v-if="codeFiles.length">{{ codeFiles.length }} 个文件</div>
            <div class="card-count empty" v-else>未上传</div>
          </div>

          <!-- 卡3: 图片 -->
          <div class="upload-card card-image" @click="selectImages()" v-if="!uploading">
            <div class="card-icon">
              <svg width="36" height="36" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <rect x="3" y="3" width="18" height="18" rx="3" stroke="currentColor" stroke-width="1.5" fill="none"/>
                <circle cx="8.5" cy="8.5" r="1.5" stroke="currentColor" stroke-width="1.2"/>
                <path d="M3 15l5-5 4 4 3-3 6 6" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/>
              </svg>
            </div>
            <div class="card-title">🖼 截图 / 图片</div>
            <div class="card-desc">上传实验截图、运行结果等</div>
            <div class="card-count" v-if="imageFiles.length">{{ imageFiles.length }} 张图片</div>
            <div class="card-count empty" v-else>未上传</div>
          </div>

          <!-- 卡4: 参考样例（可选） -->
          <div class="upload-card card-sample" @click="selectSample()" v-if="!uploading">
            <div class="card-icon">
              <svg width="36" height="36" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <rect x="3" y="2" width="18" height="20" rx="2" stroke="currentColor" stroke-width="1.5" fill="none"/>
                <line x1="7" y1="7" x2="17" y2="7" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                <line x1="7" y1="11" x2="15" y2="11" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                <line x1="7" y1="15" x2="13" y2="15" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                <circle cx="17" cy="17" r="3" stroke="#8a8a8a" stroke-width="1" fill="none"/>
                <text x="17" y="18" text-anchor="middle" font-size="4" fill="#8a8a8a">?</text>
              </svg>
            </div>
            <div class="card-title">📋 参考样例</div>
            <div class="card-desc">可选：成品报告给AI参考风格</div>
            <div class="card-count" v-if="sampleFiles.length">{{ sampleFiles.length }} 个样例</div>
            <div class="card-count empty" v-else>未上传（可选）</div>
          </div>
        </div>

        <!-- 三列文件列表 -->
        <div v-if="store.files.length > 0 && !uploading" class="file-sections">
          <!-- 模板列表 -->
          <div class="file-section">
            <div class="section-header">
              <span>📄 模板 ({{ templateFiles.length }})</span>
              <span v-if="templateFiles.length === 0" class="section-upload-link" @click="selectTemplate()">+ 上传</span>
            </div>
            <div v-if="templateFiles.length === 0" class="section-empty">点击上方卡片上传 DOCX / PDF 模板</div>
            <div v-for="f in templateFiles" :key="f.id" class="section-item">
              <code class="file-path">{{ getDisplayName(f) }}</code>
              <button class="btn-sm btn-danger" @click="store.deleteFile(f.id)">删除</button>
            </div>
          </div>

          <!-- 代码列表 -->
          <div class="file-section">
            <div class="section-header">
              <span>💻 代码 ({{ codeFiles.length }})</span>
              <span v-if="codeFiles.length === 0" class="section-upload-link" @click="selectCodeFolder()">+ 上传</span>
            </div>
            <div v-if="codeFiles.length === 0" class="section-empty">点击上方卡片上传代码文件夹</div>
            <div v-for="f in codeFiles.slice(0, 20)" :key="f.id" class="section-item">
              <code class="file-path">{{ getDisplayName(f) }}</code>
              <div class="item-actions">
                <button v-if="f.language && ['java','python','cpp','c'].includes(f.language)" class="btn-sm btn-success" :disabled="umlGenerating" @click="generateUmlFromFile(f.id, f.language!)">UML</button>
                <button class="btn-sm btn-danger" @click="store.deleteFile(f.id)">删除</button>
              </div>
            </div>
            <div v-if="codeFiles.length > 20" class="section-more text-tertiary">…以及另外 {{ codeFiles.length - 20 }} 个文件</div>
          </div>

          <!-- 图片列表 -->
          <div class="file-section">
            <div class="section-header">
              <span>🖼 图片 ({{ imageFiles.length }})</span>
              <span v-if="imageFiles.length === 0" class="section-upload-link" @click="selectImages()">+ 上传</span>
            </div>
            <div v-if="imageFiles.length === 0" class="section-empty">点击上方卡片上传图片</div>
            <div v-for="f in imageFiles" :key="f.id" class="section-item">
              <code class="file-path">{{ getDisplayName(f) }}</code>
              <button class="btn-sm btn-danger" @click="store.deleteFile(f.id)">删除</button>
            </div>
          </div>

          <!-- 样例列表 -->
          <div class="file-section">
            <div class="section-header">
              <span>📋 参考样例 ({{ sampleFiles.length }})</span>
              <span v-if="sampleFiles.length === 0" class="section-upload-link" @click="selectSample()">+ 上传</span>
            </div>
            <div v-if="sampleFiles.length === 0" class="section-empty">可选：上传成品报告给AI参考风格</div>
            <div v-for="f in sampleFiles" :key="f.id" class="section-item">
              <code class="file-path">{{ getDisplayName(f) }}</code>
              <button class="btn-sm btn-danger" @click="store.deleteFile(f.id)">删除</button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- AI Preview Tab -->
      <el-tab-pane label="👁 AI预览" name="preview">
        <div v-if="!aiPreview && !previewing" class="empty-state" style="padding:60px 0">
          <div style="font-size:48px;margin-bottom:12px">👁</div>
          <div style="font-size:16px;font-weight:600;color:#171717;margin-bottom:4px">AI 内容预览</div>
          <p class="text-tertiary">上传 DOCX 模板和代码文件后，点击「AI预览」<br>AI 将严格按模板结构分析代码并生成每个章节的内容</p>
          <button class="btn-primary" style="margin-top:20px" :disabled="!hasTemplate || previewing" @click="previewReport">
            {{ previewing ? 'AI思考中…' : '开始预览' }}
          </button>
        </div>

        <div v-if="previewError" style="padding:20px; color:#e5484d; background:#fef3f3; border-radius:8px; margin-bottom:16px">
          {{ previewError }}
          <button class="btn-sm" style="margin-left:12px" @click="previewReport">重试</button>
        </div>

        <div v-if="aiPreview" style="display:flex; flex-direction:column; gap:16px">
          <!-- Template Info -->
          <div class="preview-banner">
            <div>
              <b>{{ aiPreview.hasTemplate ? '✓ 使用用户DOCX模板' : '⚠ 未检测到模板，使用默认结构' }}</b>
              <div class="text-tertiary" v-if="aiPreview.templateSections?.length">
                模板章节：{{ aiPreview.templateSections.join(' → ') }}
              </div>
            </div>
          </div>

          <!-- Sections -->
          <div v-for="(sec, i) in aiPreview.sections" :key="i" class="preview-section">
            <div class="preview-section-header">
              <span class="section-number">{{ Number(i) + 1 }}</span>
              <span class="section-title">{{ sec.title }}</span>
            </div>
            <div class="preview-section-body">{{ sec.content }}</div>
          </div>
        </div>
      </el-tab-pane>

      <!-- UML Tab -->
      <el-tab-pane label="📐 UML图" name="uml">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-card header="PlantUML DSL 编辑器">
              <el-input type="textarea" :rows="12" v-model="umlDsl" class="mono-input" />
              <button class="btn-primary" :disabled="umlGenerating" @click="generateUmlFromDsl(umlDsl)" style="margin-top:12px">生成UML图</button>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card header="已生成的UML（将嵌入报告）">
              <div v-if="umlImages.length===0" class="empty-state">上传代码后点<b>UML</b>自动生成</div>
              <div v-for="(img,i) in umlImages" :key="img" class="uml-item"><span>📐 {{ img }}</span><button class="btn-sm btn-danger" @click="removeUmlImage(i)">移除</button></div>
              <img v-if="umlPreviewUrl" :src="umlPreviewUrl" class="uml-preview" />
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- Report Tab -->
      <el-tab-pane label="📄 报告" name="report">
        <el-card>
          <div v-if="!latestReport" class="empty-state" style="padding:48px">
            <div style="font-size:48px;margin-bottom:12px">📝</div>
            <p style="font-size:15px;font-weight:600;color:#171717">暂无生成的报告</p>
            <p style="margin-top:4px">上传模板和代码 → AI预览 → 生成报告</p>
          </div>
          <el-descriptions v-else :column="2" border>
            <el-descriptions-item label="状态"><el-tag :type="latestReport.status==='COMPLETED'?'success':'warning'">{{ latestReport.status }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="生成时间">{{ latestReport.generatedAt||'—' }}</el-descriptions-item>
            <el-descriptions-item label="大小">{{ formatSize(latestReport.outputFileSize) }}</el-descriptions-item>
            <el-descriptions-item label="路径" :span="2"><code class="file-path">{{ latestReport.outputFilePath||'—' }}</code></el-descriptions-item>
          </el-descriptions>
          <button v-if="latestReport?.status==='COMPLETED'" class="btn-primary btn-large" @click="downloadReport" style="margin-top:16px">💾 下载报告 (DOCX)</button>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.workspace-topbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:24px; flex-wrap:wrap; gap:12px }
.topbar-left { display:flex; align-items:center; gap:12px }
.topbar-right { display:flex; align-items:center; gap:10px }
.status-badge { font-size:11px; font-weight:600; padding:2px 10px; border-radius:100px; text-transform:uppercase; letter-spacing:0.04em; background:#f5f5f5; color:#8a8a8a }
.status-badge.COMPLETED { background:#e6f7ed; color:#0d9b54 }
.status-badge.GENERATING { background:#fff8e6; color:#f19d38 }
.status-badge.FAILED { background:#fef3f3; color:#e5484d }
.uml-count { font-size:13px; color:#0070f3; font-weight:600 }

/* Instructions */
.file-instructions { display:flex; align-items:center; gap:12px; margin-bottom:20px; flex-wrap:wrap; justify-content:center }
.instruction-card { display:flex; align-items:center; gap:12px; padding:16px 20px; background:#fff; border:1px solid #e5e5e5; border-radius:10px; min-width:180px }
.ic-num { width:28px; height:28px; border-radius:50%; background:#171717; color:#fff; display:flex; align-items:center; justify-content:center; font-size:14px; font-weight:700; flex-shrink:0 }
.instruction-arrow { font-size:20px; color:#d4d4d4; font-weight:300 }

/* Buttons */
.btn-primary { display:inline-flex; align-items:center; gap:6px; padding:8px 20px; border:none; border-radius:8px; background:#171717; color:#fff; font-size:14px; font-weight:600; font-family:inherit; cursor:pointer; transition:all 150ms ease }
.btn-primary:hover:not(:disabled) { background:#2a2a2a; transform:translateY(-0.5px); box-shadow:0 2px 8px rgba(0,0,0,0.15) }
.btn-primary:active:not(:disabled) { transform:translateY(0.5px) }
.btn-primary:disabled { opacity:0.5; cursor:not-allowed }
.btn-large { height:44px; padding:0 24px; font-size:15px; border-radius:10px }
.btn-outline { display:inline-flex; align-items:center; gap:6px; padding:8px 16px; border:1px solid #d4d4d4; border-radius:8px; background:#fff; color:#4a4a4a; font-size:13px; font-weight:500; font-family:inherit; cursor:pointer; transition:all 150ms ease }
.btn-outline:hover:not(:disabled) { border-color:#aaa; background:#fafafa }
.btn-outline:disabled { opacity:0.4; cursor:not-allowed }
.btn-sm { display:inline-flex; align-items:center; padding:4px 12px; border:1px solid #d4d4d4; border-radius:6px; background:#fff; color:#4a4a4a; font-size:12px; font-weight:500; font-family:inherit; cursor:pointer; transition:all 120ms ease; margin-right:4px }
.btn-sm:hover { border-color:#aaa; background:#f7f7f7 }
.btn-sm.btn-success { color:#0d9b54; border-color:#b7e4cf }
.btn-sm.btn-success:hover { background:#f0faf4 }
.btn-sm.btn-danger { color:#e5484d; border-color:#f5c6cb }
.btn-sm.btn-danger:hover { background:#fef3f3 }
.spinner { width:16px; height:16px; border:2px solid rgba(255,255,255,0.3); border-top-color:#fff; border-radius:50%; animation:spin 600ms linear infinite; display:inline-block }
@keyframes spin { to { transform:rotate(360deg) } }

/* Drop Zone */
/* --- 三卡上传区 --- */
.upload-cards { display:grid; grid-template-columns: repeat(4, 1fr); gap:16px; margin-bottom:24px }
.upload-card {
  padding:28px 20px; border-radius:12px; text-align:center; cursor:pointer;
  border:2px dashed #d4d4d4; background:#fff; transition:all 200ms ease;
  &:hover { transform:translateY(-2px) }
}
.upload-card.card-template:hover { border-color:#0d9b54; background:#f0faf4 }
.upload-card.card-code:hover { border-color:#0070f3; background:#f0f7ff }
.upload-card.card-image:hover { border-color:#f19d38; background:#fef9f0 }
.upload-card.card-sample:hover { border-color:#9c27b0; background:#faf5fc }
.card-icon { margin-bottom:12px; opacity:0.6 }
.card-title { font-size:15px; font-weight:700; color:#171717; margin-bottom:4px }
.card-desc { font-size:12px; color:#8a8a8a; margin-bottom:10px }
.card-count { display:inline-block; padding:3px 12px; border-radius:100px; font-size:12px; font-weight:600; background:#f0f7ff; color:#0070f3 }
.card-count.empty { background:#f5f5f5; color:#b0b0b0 }

/* --- 三列文件列表 --- */
.file-sections { display:grid; grid-template-columns: repeat(4, 1fr); gap:16px }
.file-section { background:#fff; border:1px solid #e5e5e5; border-radius:10px; overflow:hidden }
.section-header { display:flex; justify-content:space-between; align-items:center; padding:10px 14px; background:#f8f8f8; border-bottom:1px solid #e5e5e5; font-size:13px; font-weight:600; color:#4a4a4a }
.section-upload-link { font-size:11px; color:#0070f3; cursor:pointer; font-weight:500; &:hover { text-decoration:underline } }
.section-empty { padding:24px; text-align:center; font-size:13px; color:#b0b0b0; min-height:60px; display:flex; align-items:center; justify-content:center }
.section-item { display:flex; align-items:center; justify-content:space-between; padding:6px 14px; border-bottom:1px solid #f5f5f5; gap:8px }
.section-item .file-path { flex:1; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:11px }
.section-item .item-actions { display:flex; gap:4px; flex-shrink:0 }
.section-more { padding:8px 14px; text-align:center; font-size:12px }

/* Progress */
.upload-progress-wrap { max-width:600px; margin:0 auto 24px; background:#fff; border:1px solid #e5e5e5; border-radius:12px; padding:24px }
.upload-progress-info { display:flex; justify-content:space-between; align-items:center; margin-bottom:10px }
.prog-text { font-size:14px; font-weight:600; color:#171717 }
.prog-count { font-size:13px; color:#8a8a8a }
.prog-bar-track { height:8px; background:#e5e5e5; border-radius:100px; overflow:hidden }
.prog-bar-fill { height:100%; background:#0070f3; border-radius:100px; transition:width 300ms ease }
.prog-bar-fill.done { background:#0d9b54 }
.prog-bar-fill.failed { background:#f19d38 }
.prog-sub { display:flex; gap:12px; margin-top:8px; font-size:12px; color:#8a8a8a; justify-content:center }
.prog-fail { color:#e5484d }
.prog-pct { font-weight:600; color:#171717 }
.failed-list { margin-top:10px; text-align:left; background:#fef3f3; border:1px solid #f5c6cb; border-radius:8px; padding:10px 14px }
.failed-title { font-size:12px; font-weight:600; color:#e5484d; margin-bottom:4px }
.failed-item { font-size:11px; color:#8a8a8a; font-family:Consolas,monospace; padding:1px 0 }

.upload-err { color:#e5484d; font-size:12px; margin-top:8px }

.table-wrap { background:#fff; border:1px solid #e5e5e5; border-radius:10px; overflow:hidden }
.table-toolbar { display:flex; justify-content:space-between; align-items:center; padding:10px 16px; border-bottom:1px solid #f0f0f0 }
.file-path { font-family:'Geist Mono',Consolas,monospace; font-size:12px; background:#f7f7f7; padding:2px 6px; border-radius:4px }

/* Preview */
.preview-banner { background:#f0f7ff; border:1px solid #cce0ff; border-radius:8px; padding:14px 18px }
.preview-section { background:#fff; border:1px solid #e5e5e5; border-radius:10px; overflow:hidden; transition:box-shadow 150ms ease }
.preview-section:hover { box-shadow:0 2px 8px rgba(0,0,0,0.06) }
.preview-section-header { display:flex; align-items:center; gap:12px; padding:14px 18px; background:#f8f8f8; border-bottom:1px solid #e5e5e5 }
.section-number { width:26px; height:26px; border-radius:50%; background:#171717; color:#fff; display:flex; align-items:center; justify-content:center; font-size:12px; font-weight:700; flex-shrink:0 }
.section-title { font-weight:600; font-size:15px; color:#171717 }
.preview-section-body { padding:18px; font-size:14px; line-height:1.8; color:#4a4a4a; white-space:pre-wrap }

/* UML */
.mono-input textarea { font-family:Consolas,monospace; font-size:13px }
.uml-item { display:flex; align-items:center; justify-content:space-between; padding:6px 0; border-bottom:1px solid #f0f0f0; font-size:12px }
.uml-preview { max-width:100%; margin-top:10px; border:1px solid #e5e5e5; border-radius:8px }
.empty-state { color:#8a8a8a; text-align:center; padding:32px 0; font-size:14px }
</style>
