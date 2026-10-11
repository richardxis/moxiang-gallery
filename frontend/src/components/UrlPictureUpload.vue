<template>
  <div class="url-upload">
    <a-input-search v-model:value="fileUrl" placeholder="请输入已启用来源域名的图片 URL"
      enter-button="导入图片" :loading="loading" @search="handleUpload" />
    <p>支持 JPEG、PNG、WebP，最大 2MB。来源域名须由后端明确启用。</p>
    <img v-if="picture?.url" :src="picture.url" alt="导入图片预览" />
  </div>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { uploadPictureByUrlUsingPost } from '@/api/pictureController'
const props = defineProps<{ picture?: API.PictureVO; spaceId?: string | number; onSuccess?: (picture: API.PictureVO) => void }>()
const fileUrl = ref('')
const loading = ref(false)
const handleUpload = async () => {
  if (loading.value) return
  try {
    const url = new URL(fileUrl.value.trim())
    if (!['http:', 'https:'].includes(url.protocol)) throw new Error('protocol')
  } catch {
    message.warning('请输入有效的 HTTP(S) 图片地址')
    return
  }
  loading.value = true
  try {
    const res = await uploadPictureByUrlUsingPost({ fileUrl: fileUrl.value.trim(), id: props.picture?.id, expectedRevision: props.picture?.revision, spaceId: props.picture?.spaceId ?? props.spaceId })
    if (res.data.code === 0 && res.data.data) {
      message.success('导入成功')
      props.onSuccess?.(res.data.data)
    } else message.error(res.data.message || '导入失败')
  } catch { message.error('导入请求失败，请检查网络与来源配置') }
  finally { loading.value = false }
}
</script>
<style scoped>
.url-upload { margin-bottom: 16px; }
.url-upload p { color: #666; margin-top: 8px; }
.url-upload img { display: block; max-width: 100%; max-height: 480px; margin: 16px auto; }
</style>
