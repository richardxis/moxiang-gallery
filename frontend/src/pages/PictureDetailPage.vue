<template>
  <div id="pictureDetailPage">
    <a-row :gutter="[16, 16]">
      <!-- 图片展示区 -->
      <a-col :sm="24" :md="16" :xl="18">
        <a-card title="图片预览">
          <a-image
            style="max-height: 600px; object-fit: contain"
            :src="picture.url"
          />
        </a-card>
      </a-col>
      <!-- 图片信息区 -->
      <a-col :sm="24" :md="8" :xl="6">
        <a-card title="图片信息">
          <a-descriptions :column="1">
            <a-descriptions-item label="作者">
              <a-space>
                <a-avatar :size="24" :src="picture.user?.userAvatar" />
                <div>{{ picture.user?.userName }}</div>
              </a-space>
            </a-descriptions-item>
            <a-descriptions-item label="名称">
              {{ picture.name ?? '未命名' }}
            </a-descriptions-item>
            <a-descriptions-item label="简介">
              {{ picture.introduction ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="分类">
              {{ picture.category ?? '默认' }}
            </a-descriptions-item>
            <a-descriptions-item label="标签">
              <a-tag v-for="tag in picture.tags" :key="tag">
                {{ tag }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="格式">
              {{ picture.picFormat ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="宽度">
              {{ picture.picWidth ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="高度">
              {{ picture.picHeight ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="宽高比">
              {{ picture.picScale ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="大小">
              {{ formatSize(picture.picSize) }}
            </a-descriptions-item>
          </a-descriptions>
          <!-- 图片操作 -->
          <a-space wrap>
            <ShareModal :picture-id="picture.id" :private-picture="!!picture.spaceId" />
            <a-button v-if="loginUserStore.loginUser.id && picture.id && !picture.spaceId" @click="router.push({ path: '/search_picture', query: { pictureId: String(picture.id) } })">以图搜图</a-button>
            <a-button type="primary" @click="doDownload">
              免费下载
              <template #icon>
                <DownloadOutlined />
              </template>
            </a-button>

            <a-button v-if="canEdit" type="default" @click="doEdit">
              编辑
              <template #icon>
                <EditOutlined />
              </template>
            </a-button>
            <a-button v-if="canDelete" danger @click="doDelete">
              删除
              <template #icon>
                <DeleteOutlined />
              </template>
            </a-button>

          </a-space>

        </a-card>
      </a-col>
    </a-row>
    <OutPaintingPanel v-if="canEdit && picture.id && !picture.spaceId" :key="String(picture.id)" :picture-id="picture.id" />
    <CollaborativePreview v-if="canEdit && spaceType === 1 && picture.id && picture.url" :key="String(picture.id)" :picture-id="picture.id" :image-url="picture.url" :picture-revision="String(picture.revision ?? 0)" @saved="fetchPictureDetail" />

  </div>
</template>

<script setup lang="ts">
// 数据
import { computed, ref, watch } from 'vue'
import {
  deletePictureUsingPost,
  getPictureVoByIdUsingGet,
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import { downloadImage, formatSize } from '@/utils'
import router from '@/router'
import { useLoginUserStore } from '@/stores/userLoginUserStore.ts'
import OutPaintingPanel from '@/components/OutPaintingPanel.vue'
import CollaborativePreview from '@/components/CollaborativeEditor.vue'
import ShareModal from '@/components/ShareModal.vue'
import { getSpaceById, getSpacePermissions } from '@/api/space'

const props = defineProps<{
  id: string | number
}>()

const picture = ref<API.PictureVO>({})
const spacePermissions = ref<string[]>([])
const spaceType = ref<number>()

const loginUserStore = useLoginUserStore()
// 是否具有编辑权限
const canEdit = computed(() => {
  const loginUser = loginUserStore.loginUser;
  // 未登录不可编辑
  if (!loginUser.id) {
    return false
  }
  // 仅本人或管理员可编辑
  const user = picture.value.user || {}
  if (picture.value.spaceId) return spacePermissions.value.includes('picture:edit')
  return String(loginUser.id) === String(picture.value.userId ?? user.id) || loginUser.userRole === 'admin'
})
const canDelete = computed(() => picture.value.spaceId ? spacePermissions.value.includes('picture:delete') : canEdit.value)

// 编辑
const doEdit = () => {
  router.push('/add_picture?id=' + picture.value.id)
}
// 删除
const doDelete = async () => {
  const id = picture.value.id
  if (!id) {
    return
  }
  const res = await deletePictureUsingPost({ id, expectedRevision: picture.value.revision })
  if (res.data.code === 0) {
    message.success('删除成功')
    await router.push(picture.value.spaceId ? (spaceType.value === 1 ? '/team_space' : '/my_space') : '/')
  } else {
    message.error('删除失败')
  }
}




// 获取图片详情
const fetchPictureDetail = async () => {
  const requestedId = props.id
  try {
    const res = await getPictureVoByIdUsingGet({
      id: requestedId,
    })
    if (String(props.id) !== String(requestedId)) return
    if (res.data.code === 0 && res.data.data) {
      picture.value = res.data.data
      if (picture.value.spaceId) {
        const [space, grants] = await Promise.all([getSpaceById(picture.value.spaceId), getSpacePermissions(picture.value.spaceId)])
        if (String(props.id) !== String(requestedId)) return
        if (space.data.code === 0 && grants.data.code === 0) {
          spaceType.value = space.data.data?.spaceType
          spacePermissions.value = grants.data.data || []
        } else { picture.value = {}; message.error('空间权限加载失败') }
      }
    } else {
      message.error('获取图片详情失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取图片详情失败：' + e.message)
  }
}

watch(() => props.id, () => { picture.value = {}; spacePermissions.value = []; spaceType.value = undefined; void fetchPictureDetail() }, { immediate: true })

// 处理下载
const doDownload = () => {
  downloadImage(picture.value.url)
}



</script>


<style scoped>
#pictureDetailPage {
}

</style>
