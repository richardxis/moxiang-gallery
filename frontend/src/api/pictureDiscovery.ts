import request from '@/request'

type Response<T> = { code: number; message?: string; data?: T }
export type ExternalImage = { thumbUrl: string; fromUrl: string }
export type ExternalSearch = { provider: string; status: 'COMPLETE' | 'OPEN_PROVIDER'; pageUrl: string; message?: string; results: ExternalImage[] }
export type ColorMatch = API.PictureVO & { similarity: number }
export type BatchPicture = Pick<API.PictureVO, 'id' | 'revision' | 'name'>
export type BatchEdit = {
  pictureIdList: (string | number)[]; spaceId?: string | number; category?: string; tags?: string[]; nameRule?: string
  expectedRevisions: Record<string, string | number>
}
export const searchByPicture = (pictureId: string | number) => request<Response<ExternalSearch>>('/api/picture/search/picture', { method: 'POST', data: { pictureId, topN: 30 } })
export const searchByColor = (body: { spaceId?: string | number; picColor: string; topN: number; minSimilarity: number }) => request<Response<ColorMatch[]>>('/api/picture/search/color', { method: 'POST', data: body })
export const editPicturesByBatch = (body: BatchEdit) => request<Response<{ updatedCount: number; revisions: Record<string, string> }>>('/api/picture/edit/batch', { method: 'POST', data: body })
