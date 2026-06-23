export interface Project {
  id: number; name: string; description: string
  status: string; configJson: string; createdAt: string
}
export interface UploadedFile {
  id: number; projectId: number; originalName: string
  storedName: string; fileSize: number; fileType: string
  language: string | null; classifiedRole: string | null; createdAt: string
}
