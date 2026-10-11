<template>
  <div id="addPicturePage">
    <h2 style="margin-bottom: 16px">
      {{ route.query?.id ? '修改图片' : '创建图片' }}
    </h2>
    <a-alert v-if="spaceId" type="info" :message="'上传目标：个人空间 ' + spaceId" style="margin-bottom: 16px" />
    <a-tabs :key="route.fullPath" v-model:activeKey="uploadMode">
      <a-tab-pane key="file" tab="本地上传"><PictureUpload :picture="picture" :space-id="spaceId" :onSuccess="onSuccess" /></a-tab-pane>
      <a-tab-pane key="url" tab="URL 导入"><UrlPictureUpload :picture="picture" :space-id="spaceId" :onSuccess="onSuccess" /></a-tab-pane>
    </a-tabs>
    <a-form v-if="picture" layout="vertical" :model="pictureForm" @finish="handleSubmit">
    <a-form-item label="名称" name="name">
        <a-input v-model:value="pictureForm.name" placeholder="请输入名称" />
      </a-form-item>
      <a-form-item label="简介" name="introduction">
        <a-textarea
          v-model:value="pictureForm.introduction"
          placeholder="请输入简介"
          :rows="2"
          autoSize
          allowClear
        />
      </a-form-item>
      <a-form-item label="分类" name="category">
        <a-auto-complete
          v-model:value="pictureForm.category"
          :options="categoryOptions"
          placeholder="请输入分类"
          allowClear
        />
      </a-form-item>
      <a-form-item label="标签" name="tags">
        <a-select
          v-model:value="pictureForm.tags"
          :options="tagOptions"
          mode="tags"
          placeholder="请输入标签"
          allowClear
        />
      </a-form-item>

      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%">创建</a-button>
      </a-form-item>
    </a-form>
  </div>

</template>

<script setup lang="ts">
import PictureUpload from '@/components/PictureUpload.vue'
import UrlPictureUpload from '@/components/UrlPictureUpload.vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  editPictureUsingPost,
  getPictureVoByIdUsingGet,
  listPictureTagCategoryUsingGet
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'

const picture = ref<API.PictureVO>()
const uploadMode = ref('file')
const pictureForm = reactive<API.PictureEditRequest>({})

/**
 * 图片上传成功
 * @param newPicture
 */
const onSuccess = (newPicture: API.PictureVO) => {
  const targetSpaceId = typeof route.query.spaceId === 'string' ? route.query.spaceId : undefined
  if (!route.query.id && String(newPicture.spaceId ?? '') !== String(targetSpaceId ?? '')) return
  if (route.query.id && String(newPicture.id) !== String(route.query.id)) return
  picture.value = newPicture
  pictureForm.name = newPicture.name
}

const router = useRouter()

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: any) => {
  const pictureId = picture.value?.id
  if (!pictureId) {
    return
  }
  const res = await editPictureUsingPost({
    id: pictureId,
    expectedRevision: picture.value?.revision,
    ...values,
  })
  if (res.data.code === 0 && res.data.data) {
    message.success('创建成功')
    // 跳转到图片详情页
    router.push({
      path: `/picture/${pictureId}`,
    })
  } else {
    message.error('创建失败，' + res.data.message)
  }
}

const categoryOptions = ref<{ value: string; label: string }[]>([])
const tagOptions = ref<{ value: string; label: string }[]>([])

// 获取标签和分类选项
const getTagCategoryOptions = async () => {
  const res = await listPictureTagCategoryUsingGet()
  if (res.data.code === 0 && res.data.data) {
    // 转换成下拉选项组件接受的格式
    tagOptions.value = (res.data.data.tagList ?? []).map((data: string) => {
      return {
        value: data,
        label: data,
      }
    })
    categoryOptions.value = (res.data.data.categoryList ?? []).map((data: string) => {
      return {
        value: data,
        label: data,
      }
    })
  } else {
    message.error('加载选项失败，' + res.data.message)
  }
}

onMounted(() => {
  getTagCategoryOptions()
})

const route = useRoute()
const spaceId = computed(() => picture.value?.spaceId ?? (typeof route.query.spaceId === 'string' ? route.query.spaceId : undefined))

// 获取老数据
const getOldPicture = async () => {
  // 获取数据
  const id = typeof route.query.id === 'string' ? route.query.id : undefined
  const requestedPath = route.fullPath
  if (id) {
    const res = await getPictureVoByIdUsingGet({
      id: id,
    })
    if (route.fullPath !== requestedPath) return
    if (res.data.code === 0 && res.data.data) {
      const data = res.data.data
      picture.value = data
      pictureForm.name = data.name
      pictureForm.introduction = data.introduction
      pictureForm.category = data.category
      pictureForm.tags = data.tags
    }
  }
}

watch(() => route.fullPath, () => {
  picture.value = undefined
  Object.keys(pictureForm).forEach(key => delete (pictureForm as Record<string, unknown>)[key])
  void getOldPicture()
}, { immediate: true })


</script>

<style scoped>
#addPicturePage {
  max-width: 720px;
  margin: 0 auto;
}

</style>
