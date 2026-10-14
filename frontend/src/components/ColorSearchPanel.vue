<template>
  <a-button @click="opened = true">按颜色搜索</a-button>
  <a-modal v-model:open="opened" title="按主色调搜索图片" :footer="null" :width="900">
    <a-space wrap style="margin-bottom: 20px">
      <label>目标颜色 <input v-model="color" type="color" aria-label="目标颜色" /></label>
      <span>{{ color }}</span><span>最多返回</span><a-input-number v-model:value="topN" :min="1" :max="50" />
      <span>最低相似度</span><a-input-number v-model:value="minimum" :min="0" :max="1" :step="0.1" />
      <a-button type="primary" :loading="loading" @click="search">搜索</a-button>
    </a-space>
    <a-alert v-if="error" type="error" show-icon :message="error" />
    <a-empty v-else-if="searched && !loading && !results.length" description="没有匹配图片；缺少主色调的图片不会参与搜索" />
    <a-list :loading="loading" :grid="{ gutter: 16, xs: 1, sm: 2, md: 3 }" :data-source="results">
      <template #renderItem="{ item }"><a-list-item><a-card hoverable @click="router.push('/picture/' + item.id); opened = false">
        <template #cover><img :src="item.thumbnailUrl || item.url" :alt="item.name" loading="lazy" style="height: 150px; object-fit: cover" /></template>
        <a-card-meta :title="item.name || '未命名'" :description="'主色相似度 ' + (item.similarity * 100).toFixed(1) + '%'" />
      </a-card></a-list-item></template>
    </a-list>
  </a-modal>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { searchByColor, type ColorMatch } from '@/api/pictureDiscovery'
const props = defineProps<{ spaceId?: string | number }>()
const router = useRouter(), opened = ref(false), color = ref('#FF0000'), topN = ref(12), minimum = ref(0)
const results = ref<ColorMatch[]>([]), loading = ref(false), searched = ref(false), error = ref('')
let generation = 0
async function search() {
  const version = ++generation; loading.value = true; searched.value = true; results.value = []; error.value = ''
  try {
    const response = await searchByColor({ spaceId: props.spaceId, picColor: color.value, topN: topN.value || 12, minSimilarity: minimum.value ?? 0 })
    if (version !== generation) return
    if (response.data.code !== 0) { error.value = response.data.message || '搜索失败'; return }
    results.value = response.data.data || []
  } catch { if (version === generation) error.value = '搜索请求失败，请稍后重试' }
  finally { if (version === generation) loading.value = false }
}
watch(() => props.spaceId, () => { generation++; opened.value = false; results.value = []; loading.value = false; searched.value = false; error.value = '' })
</script>
