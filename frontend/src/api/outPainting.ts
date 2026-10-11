import request from '@/request'

export type OutPaintingParameters = { angle?: number; outputRatio?: string; xScale?: number; yScale?: number }
export type OutPaintingTask = { taskId: string; taskStatus: string; outputImageUrl?: string }
type TaskResponse = { code: number; message?: string; data?: OutPaintingTask }
export function createOutPaintingTask(pictureId: string | number, parameters: OutPaintingParameters) {
  return request<TaskResponse>('/api/picture/out_painting/create_task', {
    method: 'POST', data: { pictureId, parameters }, timeout: 15000,
  })
}
export function queryOutPaintingTask(taskId: string, signal?: AbortSignal) {
  return request<TaskResponse>('/api/picture/out_painting/get_task', {
    method: 'GET', params: { taskId }, signal, timeout: 15000,
  })
}
