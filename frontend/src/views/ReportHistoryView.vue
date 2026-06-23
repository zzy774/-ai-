<script setup lang="ts">
import { ref } from 'vue'
import axios from '@/api'
import { ElMessage } from 'element-plus'

const reports = ref<any[]>([])
const loading = ref(false)
const projectId = ref<number | null>(null)

async function load() {
  if (!projectId.value) { ElMessage.warning('请选择一个项目'); return }
  loading.value = true
  try {
    const res = await axios.get(`/reports/history?projectId=${projectId.value}`) as any
    reports.value = res.data || []
  } catch {} finally { loading.value = false }
}

function download(id: number) { window.open(`/api/reports/${id}/download`, '_blank') }

async function pollStatus(taskId: string) {
  const timer = setInterval(async () => {
    try {
      const res = await axios.get(`/reports/status/${taskId}`) as any
      const idx = reports.value.findIndex((r: any) => r.taskId === taskId)
      if (idx >= 0) reports.value[idx] = res.data
      if (res.data?.status === 'COMPLETED' || res.data?.status === 'FAILED') {
        clearInterval(timer)
        ElMessage.success(res.data.status === 'COMPLETED' ? '报告已完成' : '报告生成失败')
      }
    } catch { clearInterval(timer) }
  }, 3000)
}

function statusLabel(s: string) {
  const m: Record<string,string> = { PENDING:'待处理', RUNNING:'生成中', COMPLETED:'已完成', FAILED:'失败' }
  return m[s] || s
}
function statusType(s: string) {
  const m: Record<string,string> = { PENDING:'info', RUNNING:'warning', COMPLETED:'success', FAILED:'danger' }
  return m[s] || 'info'
}
</script>

<template>
  <div class="page-container">
    <div class="topbar">
      <h2 class="page-title">📄 报告历史</h2>
      <div class="topbar-right">
        <el-select v-model="projectId" placeholder="选择项目" style="width:260px">
          <el-option label="所有" :value="null" />
        </el-select>
        <button class="btn-primary" :disabled="loading" @click="load">
          {{ loading ? '查询中…' : '查询' }}
        </button>
      </div>
    </div>
    <div class="table-wrap">
      <el-table :data="reports" v-loading="loading" style="width:100%" empty-text="选择项目后点击「查询」">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="任务 ID" show-overflow-tooltip>
          <template #default="{ row }"><code class="code">{{ row.taskId }}</code></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="进度" width="80">
          <template #default="{ row }">
            <span v-if="row.status === 'RUNNING' || row.status === 'COMPLETED'" class="text-secondary">{{ row.progressPhase }}/7</span>
            <span v-else class="text-tertiary">—</span>
          </template>
        </el-table-column>
        <el-table-column label="文件大小" width="100">
          <template #default="{ row }">{{ row.outputFileSize ? (row.outputFileSize / 1024).toFixed(1) + ' KB' : '—' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <button v-if="row.status === 'COMPLETED'" class="btn-sm btn-success" @click="download(row.id)">💾 下载</button>
            <button v-if="row.status === 'RUNNING'" class="btn-sm" @click="pollStatus(row.taskId)">🔄 刷新</button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.topbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:24px; flex-wrap:wrap; gap:12px }
.topbar-right { display:flex; gap:8px; align-items:center }

.btn-primary {
  display:inline-flex; align-items:center; gap:6px; padding:8px 20px;
  border:none; border-radius:8px; background:#171717; color:#fff;
  font-size:14px; font-weight:600; font-family:inherit; cursor:pointer;
  transition: all 150ms ease;
  &:hover:not(:disabled) { background:#2a2a2a; transform:translateY(-0.5px) }
  &:disabled { opacity:0.5; cursor:not-allowed }
  &:focus-visible { outline:2px solid #0070f3; outline-offset:2px }
}
.btn-sm {
  display:inline-flex; align-items:center; padding:4px 12px;
  border:1px solid #d4d4d4; border-radius:6px; background:#fff; color:#4a4a4a;
  font-size:12px; font-weight:500; font-family:inherit; cursor:pointer;
  transition: all 120ms ease; margin-right:4px;
  &:hover { border-color:#aaa; background:#f7f7f7 }
  &:focus-visible { outline:2px solid #0070f3; outline-offset:1px }
}
.btn-sm.btn-success { color:#0d9b54; border-color:#b7e4cf; &:hover { background:#f0faf4 } }

.table-wrap { background:#fff; border:1px solid #e5e5e5; border-radius:10px; overflow:hidden }
.code { font-family: 'Geist Mono', Consolas, monospace; font-size:11px; background:#f7f7f7; padding:2px 6px; border-radius:4px }
</style>
