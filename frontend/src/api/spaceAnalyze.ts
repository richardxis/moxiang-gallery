import request from '@/request'

export type AnalysisScope = { spaceId?: string; queryPublic?: boolean; queryAll?: boolean }
export type AnalysisRequest = AnalysisScope & {
  topN?: number; timeDimension?: 'day' | 'week' | 'month'; userId?: string
  startDate?: string; endDate?: string
}
export type Usage = {
  usedSize: string | number; usedCount: string | number
  maxSize?: string | number | null; maxCount?: string | number | null
  sizeUsageRatio?: number | null; countUsageRatio?: number | null
}
export type Category = { category: string; count: string | number; totalSize: string | number }
export type Tag = { tag: string; count: string | number }
export type SizeBucket = { sizeRange: string; count: string | number }
export type UserActivity = { period: string; count: string | number }
export type SpaceRank = { id: string; spaceName: string; userId: string; totalSize: string | number; totalCount: string | number }
type Response<T> = { code: number; message?: string; data?: T }
export function analyzeSpace<T>(kind: 'usage' | 'category' | 'tag' | 'size' | 'user' | 'rank', body: AnalysisRequest) {
  return request<Response<T>>('/api/space/analyze/' + kind, { method: 'POST', data: body })
}
