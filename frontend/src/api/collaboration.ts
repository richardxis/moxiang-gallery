import request from '@/request'
export type SaveRequest = {
  id: string | number; expectedRevision: string; requestId: string; connectionId: string
  lockToken: string; epoch: string; draftVersion: string
  cropX?: number; cropY?: number; cropWidth?: number; cropHeight?: number
}
export async function loadPictureSource(id: string | number) {
  const response = await request<Blob>('/api/picture/content', { params: { id }, responseType: 'blob' })
  if (String(response.headers['content-type'] ?? '').includes('application/json')) {
    const error = JSON.parse(await response.data.text())
    throw new Error(error.message || '读取图片失败')
  }
  return { blob: response.data, revision: String(response.headers['x-picture-revision'] ?? '') }
}
export async function saveCollaboration(file: Blob, body: SaveRequest) {
  const form = new FormData()
  form.append('file', file, 'edited.png')
  for (const [key, value] of Object.entries(body)) if (value !== undefined) form.append(key, String(value))
  return request<API.BaseResponsePictureVO_>('/api/picture/collaboration/save', { method: 'POST', data: form })
}
export const getCollaborationResult = (requestId: string) => request<API.BaseResponsePictureVO_>('/api/picture/collaboration/result', { params: { requestId } })
