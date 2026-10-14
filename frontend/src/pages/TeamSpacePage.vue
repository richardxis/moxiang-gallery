<template>
  <div>
    <h2>团队空间</h2>
    <a-alert type="warning" show-icon message="支持团队角色权限、单实例旋转/缩放及矩形裁剪保存；尚未支持跨实例协作。COS 未接入签名访问，请勿上传敏感图片。" />
    <a-space style="margin: 16px 0" wrap>
      <a-input v-model:value="name" :maxlength="128" placeholder="团队名称" />
      <a-button :loading="creating" @click="create">创建普通版团队空间</a-button>
      <a-button @click="loadTeams">刷新团队列表</a-button>
    </a-space>
    <a-list :data-source="teams">
      <template #renderItem="{ item }"><a-list-item><a-button @click="select(item)">{{ item.spaceName }}</a-button></a-list-item></template>
    </a-list>
    <a-pagination v-model:current="teamPage" :page-size="20" :total="teamTotal" @change="loadTeams" />
    <a-card v-if="space" :title="space.spaceName" style="margin-top: 16px">
      <p>图片 {{ space.totalCount }} / {{ space.maxCount }}；{{ formatSize(Number(space.totalSize)) }} / {{ formatSize(Number(space.maxSize)) }}</p>
      <a-space wrap>
        <a-button v-if="permissions.includes('picture:upload')" type="primary" @click="upload">上传图片</a-button>
        <ColorSearchPanel :space-id="space.id" />
        <BatchEditPictureModal v-if="permissions.includes('picture:edit')" :pictures="selectedPictures" :space-id="space.id" @saved="loadPictures" />
        <a-button v-if="permissions.includes('spaceUser:manage')" @click="router.push({ path: '/space_analyze', query: { spaceId: String(space.id) } })">空间分析</a-button>
        <a-input-search v-model:value="searchText" placeholder="搜索团队图片" @search="search" />
        <a-button @click="refresh">刷新权限与图片</a-button>
      </a-space>
      <a-list :grid="{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 4 }" :data-source="pictures" style="margin-top: 16px">
        <template #renderItem="{ item }"><a-list-item><a-checkbox v-if="permissions.includes('picture:edit')" :checked="selectedIds.includes(String(item.id))" @change="toggle(String(item.id))">选择</a-checkbox><a-card hoverable @click="router.push('/picture/' + item.id)">
          <template #cover><img :src="item.thumbnailUrl || item.url" :alt="item.name" loading="lazy" style="height: 160px; object-fit: cover" /></template>
          <a-card-meta :title="item.name" />
        </a-card></a-list-item></template>
      </a-list>
      <a-pagination v-model:current="picturePage" :page-size="12" :total="pictureTotal" @change="loadPictures" />
      <div v-if="permissions.includes('spaceUser:manage')" style="margin-top: 24px">
        <h3>成员管理</h3>
        <p>填写已注册的用户 ID；空间创建者不能被移除或降级。只有创建者或系统管理员可授予、变更管理员身份。</p>
        <a-space wrap>
          <a-input v-model:value="memberId" placeholder="已注册用户 ID" />
          <a-select v-model:value="role" style="width: 120px" :options="roles" />
          <a-button :loading="mutating" @click="mutate('add')">添加</a-button>
          <a-button :loading="mutating" @click="mutate('edit')">修改角色</a-button>
          <a-popconfirm title="确定移除此用户？" @confirm="mutate('delete')"><a-button danger :loading="mutating">移除</a-button></a-popconfirm>
        </a-space>
        <a-list :data-source="members">
          <template #renderItem="{ item }"><a-list-item>用户 {{ item.userId }}：{{ item.spaceRole }}<a-button @click="memberId = String(item.userId); role = item.spaceRole">选中</a-button></a-list-item></template>
        </a-list>
        <a-pagination v-model:current="memberPage" :page-size="20" :total="memberTotal" @change="loadMembers" />
      </div>
    </a-card>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import ColorSearchPanel from '@/components/ColorSearchPanel.vue'
import BatchEditPictureModal from '@/components/BatchEditPictureModal.vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { createTeamSpace, getSpaceById, getSpacePermissions, listTeamSpaces, listSpaceMembers, mutateSpaceMember, type Space, type SpaceMember } from '@/api/space'
import { listPictureVoByPageUsingPost } from '@/api/pictureController'
import { formatSize } from '@/utils'
const router = useRouter()
const teams = ref<Space[]>([]), teamPage = ref(1), teamTotal = ref(0), name = ref('墨象团队'), creating = ref(false)
const space = ref<Space>(), permissions = ref<string[]>([]), pictures = ref<API.PictureVO[]>([])
const selectedIds = ref<string[]>([])
const selectedPictures = computed(() => pictures.value.filter(picture => selectedIds.value.includes(String(picture.id))))
function toggle(id: string) { selectedIds.value = selectedIds.value.includes(id) ? selectedIds.value.filter(value => value !== id) : [...selectedIds.value, id] }
const picturePage = ref(1), pictureTotal = ref(0), searchText = ref('')
const members = ref<SpaceMember[]>([]), memberPage = ref(1), memberTotal = ref(0), memberId = ref(''), role = ref('viewer'), mutating = ref(false)
const roles = ['viewer', 'editor', 'admin'].map(value => ({ label: value, value }))
let scopeVersion = 0, pictureVersion = 0, memberVersion = 0, teamVersion = 0
function clearScope() { scopeVersion++; pictureVersion++; memberVersion++; selectedIds.value = []; space.value = undefined; permissions.value = []; pictures.value = []; members.value = [] }
async function loadTeams() {
  const version = ++teamVersion
  try { const res = await listTeamSpaces(teamPage.value); if (version !== teamVersion) return
    if (res.data.code !== 0) throw new Error(res.data.message || '加载失败')
    teams.value = res.data.data?.records || []; teamTotal.value = Number(res.data.data?.total || 0)
  } catch { if (version === teamVersion) { teams.value = []; message.error('团队列表加载失败') } }
}
async function select(item: Space) {
  clearScope(); const version = scopeVersion
  picturePage.value = 1; memberPage.value = 1; searchText.value = ''; memberId.value = ''
  try {
    const [info, grants] = await Promise.all([getSpaceById(item.id), getSpacePermissions(item.id)])
    if (version !== scopeVersion) return
    if (info.data.code !== 0 || grants.data.code !== 0 || !info.data.data) throw new Error('无权限')
    space.value = info.data.data; permissions.value = grants.data.data || []
    await Promise.all([loadPictures(), loadMembers()])
  } catch { if (version === scopeVersion) { clearScope(); message.error('空间加载失败或权限已撤销') } }
}
async function loadPictures() {
  if (!space.value) return
  const version = ++pictureVersion
  selectedIds.value = []
  try { const res = await listPictureVoByPageUsingPost({ spaceId: space.value.id, current: picturePage.value, pageSize: 12, searchText: searchText.value })
    if (version !== pictureVersion) return
    if (res.data.code !== 0) throw new Error('查询失败')
    pictures.value = res.data.data?.records || []; pictureTotal.value = Number(res.data.data?.total || 0)
  } catch { if (version === pictureVersion) { clearScope(); message.error('图片查询失败或权限已撤销') } }
}
async function loadMembers() {
  if (!space.value || !permissions.value.includes('spaceUser:manage')) return
  const version = ++memberVersion
  try { const res = await listSpaceMembers(space.value.id, memberPage.value)
    if (version !== memberVersion) return
    if (res.data.code !== 0) throw new Error('查询失败')
    members.value = res.data.data?.records || []; memberTotal.value = Number(res.data.data?.total || 0)
  } catch { if (version === memberVersion) { members.value = []; permissions.value = permissions.value.filter(p => p !== 'spaceUser:manage'); message.error('成员加载失败，请刷新权限') } }
}
async function create() {
  if (creating.value) return
  creating.value = true
  try { const res = await createTeamSpace(name.value); if (res.data.code !== 0 || !res.data.data) throw new Error(res.data.message || '创建失败')
    teamPage.value = 1; await loadTeams(); await select(res.data.data)
  } catch (e) { message.error(e instanceof Error ? e.message : '创建失败') } finally { creating.value = false }
}
async function mutate(action: 'add' | 'edit' | 'delete') {
  if (mutating.value || !space.value) return
  if (!/^[1-9]\d{0,18}$/.test(memberId.value)) { message.error('请输入合法用户 ID'); return }
  const selected = space.value, version = scopeVersion
  mutating.value = true
  try { const res = await mutateSpaceMember(action, selected.id, memberId.value, role.value)
    if (version !== scopeVersion) return
    if (res.data.code !== 0) throw new Error(res.data.message || '操作失败')
    message.success('操作成功'); await loadMembers()
  } catch (e) { if (version === scopeVersion) message.error(e instanceof Error ? e.message : '操作失败') } finally { mutating.value = false }
}
function search() { picturePage.value = 1; void loadPictures() }
function upload() { if (space.value) router.push({ path: '/add_picture', query: { spaceId: String(space.value.id) } }) }
function refresh() { if (space.value) void select(space.value) }
onMounted(loadTeams)
</script>
