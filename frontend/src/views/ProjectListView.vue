<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { ElMessageBox } from 'element-plus'

const router = useRouter()
const store = useProjectStore()
const showCreate = ref(false)
const newProject = ref({ name: '', description: '' })

const currentPage = ref(1)
const pageSize = ref(12)

onMounted(() => store.fetchProjects(1, 200))

const pagedProjects = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return store.projects.slice(start, start + pageSize.value)
})

async function create() {
  const p = await store.createProject(newProject.value.name, newProject.value.description)
  showCreate.value = false; newProject.value = { name: '', description: '' }
  router.push({ name: 'project-workspace', params: { id: p.id } })
}
function enter(id: number) { router.push({ name: 'project-workspace', params: { id } }) }
async function remove(id: number) {
  await ElMessageBox.confirm('确定要删除这个项目吗？', '删除确认', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
  await store.deleteProject(id)
}
function onPageChange(page: number) { currentPage.value = page }
function statusLabel(s: string) {
  const m: Record<string,string> = { DRAFT:'草稿', COMPLETED:'已完成', GENERATING:'生成中', FAILED:'失败' }
  return m[s] || s
}
function statusType(s: string) {
  const m: Record<string,string> = { DRAFT:'info', COMPLETED:'success', GENERATING:'warning', FAILED:'danger' }
  return m[s] || 'info'
}
</script>

<template>
  <div class="page-container">
    <div class="topbar">
      <h2 class="page-title">📁 项目管理</h2>
      <button class="btn-primary" @click="showCreate = true">+ 新建项目</button>
    </div>

    <!-- Create Dialog -->
    <el-dialog v-model="showCreate" title="新建实验报告项目" width="480px" :close-on-click-modal="false">
      <el-form :model="newProject" label-position="top">
        <el-form-item label="项目名称"><el-input v-model="newProject.name" placeholder="例如：数据结构实验一" /></el-form-item>
        <el-form-item label="描述（可选）"><el-input v-model="newProject.description" type="textarea" :rows="2" placeholder="简要描述项目内容" /></el-form-item>
      </el-form>
      <template #footer>
        <button class="btn-outline" @click="showCreate = false">取消</button>
        <button class="btn-primary" @click="create">创建项目</button>
      </template>
    </el-dialog>

    <!-- Table -->
    <div class="table-wrap">
      <el-table :data="pagedProjects" v-loading="store.loading" style="width:100%"
        max-height="calc(100vh - 220px)" empty-text="暂无项目，点击「新建项目」开始">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="项目名称" show-overflow-tooltip />
        <el-table-column label="描述" show-overflow-tooltip>
          <template #default="{ row }"><span class="text-tertiary">{{ row.description || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <button class="btn-sm" @click="enter(row.id)">进入</button>
            <button class="btn-sm btn-danger" @click="remove(row.id)">删除</button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Pagination -->
    <div style="display:flex; justify-content:center; margin-top:16px">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="store.projects.length"
        layout="prev, pager, next, total"
        @current-change="onPageChange"
        background
      />
    </div>
  </div>
</template>

<style scoped>
.topbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:24px; flex-wrap:wrap; gap:12px }

.btn-primary {
  display:inline-flex; align-items:center; gap:6px; padding:10px 24px;
  border:none; border-radius:10px; background:#171717; color:#fff;
  font-size:14px; font-weight:600; font-family:inherit; cursor:pointer;
  transition: all 150ms ease;
  &:hover { background:#2a2a2a; transform:translateY(-0.5px); box-shadow:0 2px 8px rgba(0,0,0,0.15) }
  &:active { transform:translateY(0.5px) }
  &:focus-visible { outline:2px solid #0070f3; outline-offset:2px }
}
.btn-outline {
  display:inline-flex; align-items:center; gap:6px; padding:8px 20px;
  border:1px solid #d4d4d4; border-radius:8px; background:#fff; color:#4a4a4a;
  font-size:13px; font-weight:500; font-family:inherit; cursor:pointer;
  transition: all 150ms ease;
  &:hover { border-color:#aaa; background:#fafafa }
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
.btn-sm.btn-danger { color:#e5484d; border-color:#f5c6cb; &:hover { background:#fef3f3 } }

.table-wrap { background:#fff; border:1px solid #e5e5e5; border-radius:10px; overflow:hidden }
</style>
