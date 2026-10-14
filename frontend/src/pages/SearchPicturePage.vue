<template>
  <div>
    <h2>以图搜图</h2>
    <p>搜索公共图片的相似图片与来源。</p>
    <a-spin :spinning="loading">
      <a-alert v-if="error" type="error" show-icon :message="error" />
      <p v-if="error"><a href="https://graph.baidu.com/pcpage/index?tpl_from=pc" target="_blank" rel="noopener noreferrer">打开识图服务</a></p>
      <template v-if="result">
        <a-alert v-if="result.status === 'OPEN_PROVIDER'" type="info" show-icon :message="result.message || '请在服务商页面继续查看'" />
        <p><a :href="safeLink(result.pageUrl)" target="_blank" rel="noopener noreferrer">打开识图结果页</a></p>
        <a-empty v-if="result.status === 'COMPLETE' && !result.results.length" description="没有找到相似图片" />
        <a-list :grid="{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 4 }" :data-source="result.results">
          <template #renderItem="{ item }"><a-list-item><a :href="safeLink(item.fromUrl)" target="_blank" rel="noopener noreferrer"><a-card hoverable>
            <template #cover><img :src="safeLink(item.thumbUrl)" alt="相似图片" loading="lazy" referrerpolicy="no-referrer" style="height: 180px; object-fit: cover" /></template>
            <a-card-meta title="查看图片来源" />
          </a-card></a></a-list-item></template>
        </a-list>
      </template>
    </a-spin>
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { searchByPicture, type ExternalSearch } from '@/api/pictureDiscovery'
const route = useRoute(), loading = ref(false), result = ref<ExternalSearch>(), error = ref('')
let generation = 0
function safeLink(value: string) { try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password ? url.toString() : undefined } catch { return undefined } }
async function load() {
  const version = ++generation; result.value = undefined; error.value = ''; loading.value = false
  const id = typeof route.query.pictureId === 'string' ? route.query.pictureId : ''
  if (!/^[1-9]\d{0,18}$/.test(id)) { error.value = '请选择有效的公共图片'; return }
  loading.value = true
  try {
    const response = await searchByPicture(id)
    if (version !== generation) return
    if (response.data.code !== 0 || !response.data.data) { error.value = response.data.message || '搜索失败'; return }
    result.value = response.data.data
  } catch { if (version === generation) error.value = '搜索请求失败，请稍后重试' }
  finally { if (version === generation) loading.value = false }
}
watch(() => route.fullPath, () => { void load() }, { immediate: true })
</script>
