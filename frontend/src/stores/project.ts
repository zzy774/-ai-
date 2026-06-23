import { defineStore } from 'pinia'
import axios from '@/api'
import type { Project, UploadedFile } from '@/types/project'
import { ElMessage } from 'element-plus'

const BATCH_SIZE = 20        // 每批最多20个文件
const MAX_RETRIES = 3        // 失败重试3次
const RETRY_DELAY_MS = 800   // 重试间隔

export interface UploadProgress {
  total: number
  uploaded: number
  failed: number
  currentBatch: number
  totalBatches: number
  failedFiles: string[]
  status: 'idle' | 'uploading' | 'done' | 'cancelled'
}

let cancelFlag = false

export function cancelUpload() { cancelFlag = true }

export const useProjectStore = defineStore('project', {
  state: () => ({
    projects: [] as Project[],
    currentProject: null as Project | null,
    files: [] as UploadedFile[],
    loading: false,
    uploadProgress: null as UploadProgress | null,
  }),
  actions: {
    async fetchProjects(page = 1, size = 10) {
      this.loading = true
      try {
        const res = await axios.get('/projects', { params: { page, size } }) as any
        this.projects = res.data.records || []
      } finally { this.loading = false }
    },
    async fetchProject(id: number) {
      const res = await axios.get(`/projects/${id}`) as any
      this.currentProject = res.data
    },
    async createProject(name: string, description = '') {
      const res = await axios.post('/projects', { name, description }) as any
      ElMessage.success('项目创建成功')
      return res.data
    },
    async updateProject(id: number, updates: any) {
      await axios.put(`/projects/${id}`, updates)
      ElMessage.success('项目更新成功')
    },
    async deleteProject(id: number) {
      await axios.delete(`/projects/${id}`)
      ElMessage.success('项目已删除')
      await this.fetchProjects()
    },

    // ---- 单次上传（小文件/少量文件） ----
    async uploadFiles(projectId: number, files: File[], relativePaths: string[] = [], fileType?: string) {
      const formData = new FormData()
      files.forEach(f => formData.append('files', f))
      formData.append('projectId', String(projectId))
      if (relativePaths.length > 0) formData.append('paths', JSON.stringify(relativePaths))
      if (fileType) formData.append('fileType', fileType)

      const res = await axios.post('/files/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      }) as any
      return res.data
    },

    // ---- 分批上传（大量文件，支持进度和取消） ----
    async uploadFilesChunked(projectId: number, allFiles: File[], allPaths: string[],
        onProgress?: (p: UploadProgress) => void, fileType?: string) {
      cancelFlag = false
      const totalBatches = Math.ceil(allFiles.length / BATCH_SIZE)
      const progress: UploadProgress = {
        total: allFiles.length,
        uploaded: 0,
        failed: 0,
        currentBatch: 0,
        totalBatches,
        failedFiles: [],
        status: 'uploading',
      }
      this.uploadProgress = progress
      const update = () => { if (onProgress) onProgress({ ...progress }) }

      update()

      for (let b = 0; b < totalBatches; b++) {
        if (cancelFlag) {
          progress.status = 'cancelled'
          update()
          ElMessage.warning('上传已取消')
          return
        }

        progress.currentBatch = b + 1
        update()

        const start = b * BATCH_SIZE
        const end = Math.min(start + BATCH_SIZE, allFiles.length)
        const batchFiles = allFiles.slice(start, end)
        const batchPaths = allPaths.slice(start, end)

        // 带重试的上传
        let success = false
        for (let retry = 0; retry <= MAX_RETRIES && !success; retry++) {
          if (cancelFlag) break
          try {
            if (retry > 0) {
              await new Promise(r => setTimeout(r, RETRY_DELAY_MS))
            }
            await this.uploadFiles(projectId, batchFiles, batchPaths, fileType)
            success = true
          } catch (e: any) {
            if (retry === MAX_RETRIES) {
              // 最终失败
              batchFiles.forEach(f => progress.failedFiles.push(f.name))
              progress.failed += batchFiles.length
              update()
            }
          }
        }

        if (success) {
          progress.uploaded += batchFiles.length
          update()
        }

        // 批次间小延迟，减轻后端压力
        if (b < totalBatches - 1) {
          await new Promise(r => setTimeout(r, 150))
        }
      }

      // 完成
      if (cancelFlag) {
        progress.status = 'cancelled'
      } else {
        progress.status = 'done'
        await this.fetchProjectFiles(projectId)

        if (progress.failed === 0) {
          ElMessage.success(`全部 ${progress.uploaded} 个文件上传成功`)
        } else {
          ElMessage.warning(`${progress.uploaded} 个成功, ${progress.failed} 个失败`)
          if (progress.failedFiles.length > 0) {
            const names = progress.failedFiles.slice(0, 5).join(', ')
            const more = progress.failedFiles.length > 5 ? ` 等${progress.failedFiles.length}个` : ''
            ElMessage.error('失败: ' + names + more)
          }
        }
      }
      update()
    },

    async fetchProjectFiles(projectId: number) {
      const res = await axios.get(`/files/project/${projectId}`) as any
      this.files = res.data || []
    },
    async deleteFile(fileId: number) {
      await axios.delete(`/files/${fileId}`)
      this.files = this.files.filter((f: any) => f.id !== fileId)
      ElMessage.success('文件已删除')
    },
  },
})
