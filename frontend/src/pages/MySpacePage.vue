<template>
  <div>
    <h2>我的个人空间</h2>
    <a-alert type="warning" show-icon message="当前实现空间记录访问隔离；COS 文件隐私依赖桶权限，尚未接入签名访问。请勿上传敏感图片。" />
    <a-spin :spinning="loading">
      <a-card v-if="!space && !loading && !loadFailed" title="创建个人空间" style="margin-top: 16px">
        <a-input v-model:value="spaceName" :maxlength="128" placeholder="空间名称" />
        <a-button type="primary" :loading="creating" @click="create">创建普通版空间</a-button>
        <p>每人一个；最多 100 张、100 MiB 的显示图片元数据额度，不等于 COS 总存储费用。</p>
      </a-card>
      <a-button v-if="loadFailed" @click="reload">重新加载</a-button>
      <a-card v-if="space" :title="space.spaceName" style="margin-top: 16px">
        <p>图片：{{ space.totalCount }} / {{ space.maxCount }}；体积：{{ formatSize(Number(space.totalSize)) }} / {{ formatSize(Number(space.maxSize)) }}</p>
        <a-space wrap>
          <a-button type="primary" @click="upload">上传到此空间</a-button>
          <ColorSearchPanel :space-id="space.id" />
          <BatchEditPictureModal :pictures="selectedPictures" :space-id="space.id" @saved="fetchPictures" />
          <a-button @click="router.push({ path: '/space_analyze', query: { spaceId: String(space.id) } })">空间分析</a-button>
          <a-input-search v-model:value="searchText" placeholder="搜索空间图片" @search="search" />
          <a-button @click="reload">刷新</a-button>
        </a-space>
        <a-list :grid="{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 4 }" :data-source="pictures" style="margin-top: 16px">
          <template #renderItem="{ item }">
            <a-list-item><a-checkbox :checked="selectedIds.includes(String(item.id))" @change="toggle(String(item.id))">选择</a-checkbox><a-card hoverable @click="router.push('/picture/' + item.id)">
              <template #cover><img :src="item.thumbnailUrl || item.url" loading="lazy" :alt="item.name" style="height: 160px; object-fit: cover" /></template>
              <a-card-meta :title="item.name" />
            </a-card></a-list-item>
          </template>
        </a-list>
        <a-pagination v-model:current="current" :page-size="12" :total="total" @change="fetchPictures" />
      </a-card>
    </a-spin>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import ColorSearchPanel from '@/components/ColorSearchPanel.vue'
import BatchEditPictureModal from '@/components/BatchEditPictureModal.vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { createPersonalSpace, getMySpace, type Space } from '@/api/space'
import { listPictureVoByPageUsingPost } from '@/api/pictureController'
import { formatSize } from '@/utils'
const router = useRouter(), space = ref<Space | null>(null), spaceName = ref('我的墨象空间')
const loading = ref(false), creating = ref(false), loadFailed = ref(false)
const pictures = ref<API.PictureVO[]>([]), total = ref(0), current = ref(1), searchText = ref('')
const selectedIds = ref<string[]>([])
const selectedPictures = computed(() => pictures.value.filter(picture => selectedIds.value.includes(String(picture.id))))
function toggle(id: string) { selectedIds.value = selectedIds.value.includes(id) ? selectedIds.value.filter(value => value !== id) : [...selectedIds.value, id] }
let queryVersion = 0
async function fetchPictures() {
  if (!space.value) return
  const version = ++queryVersion
  selectedIds.value = []
  try {
    const result = await listPictureVoByPageUsingPost({ spaceId: space.value.id, current: current.value, pageSize: 12, searchText: searchText.value })
    if (version !== queryVersion) return
    if (result.data.code !== 0) { message.error(result.data.message || '查询失败'); return }
    pictures.value = result.data.data?.records || []; total.value = Number(result.data.data?.total || 0)
  } catch { if (version === queryVersion) message.error('空间图片加载失败') }
}
async function reload() {
  loading.value = true; loadFailed.value = false
  try {
    const result = await getMySpace()
    if (result.data.code !== 0) { loadFailed.value = true; message.error(result.data.message || '加载空间失败'); return }
    space.value = result.data.data || null
    if (space.value) await fetchPictures()
  } catch { loadFailed.value = true; message.error('加载空间失败，请检查数据库和迁移') }
  finally { loading.value = false }
}
async function create() {
  if (creating.value) return
  creating.value = true
  try {
    const result = await createPersonalSpace(spaceName.value)
    if (result.data.code !== 0) { message.error(result.data.message || '创建失败'); return }
    await reload()
  } catch { message.error('创建失败') }
  finally { creating.value = false }
}
function search() { current.value = 1; void fetchPictures() }
function upload() { if (space.value) router.push({ path: '/add_picture', query: { spaceId: String(space.value.id) } }) }
onMounted(reload)
</script>
