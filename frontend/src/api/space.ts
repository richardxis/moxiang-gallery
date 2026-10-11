import request from '@/request'
export type Space = {
  id: string | number; spaceName: string; userId: string | number; spaceType: number; spaceLevel: number
  maxSize: string | number; maxCount: string | number; totalSize: string | number; totalCount: string | number
}
type Response<T> = { code: number; message?: string; data?: T }
export const getMySpace = () => request<Response<Space | null>>('/api/space/my', { method: 'GET' })
export const getSpaceById = (id: string | number) => request<Response<Space>>('/api/space/get/vo', { method: 'GET', params: { id } })
export const createPersonalSpace = (spaceName: string) => request<Response<Space>>('/api/space/add', { method: 'POST', data: { spaceName } })
export type Page<T> = { records: T[]; total: number | string }
export type SpaceMember = { id: string | number; userId: string | number; spaceRole: string }
export const getSpacePermissions = (id: string | number) => request<Response<string[]>>('/api/space/permissions', { params: { id } })
export const createTeamSpace = (spaceName: string) => request<Response<Space>>('/api/space/add', { method: 'POST', data: { spaceName, spaceType: 1 } })
export const listTeamSpaces = (current = 1) => request<Response<Page<Space>>>('/api/space/team/list', { params: { current, pageSize: 20 } })
export const listSpaceMembers = (spaceId: string | number, current = 1) => request<Response<Page<SpaceMember>>>('/api/space_user/list', { params: { spaceId, current, pageSize: 20 } })
export const mutateSpaceMember = (action: 'add' | 'edit' | 'delete', spaceId: string | number, userId: string | number, spaceRole: string) => request<Response<boolean>>('/api/space_user/' + action, { method: 'POST', data: { spaceId, userId, spaceRole } })
