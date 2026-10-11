<template>
  <div class="batch-page">
    <h2>批量导入公共素材</h2>
    <a-alert type="info" show-icon message="仅管理员可用，最多 30 张。失败候选会跳过，请确认素材使用授权。" />
    <a-form layout="vertical" :model="form" @finish="submit">
      <a-form-item label="搜索词" name="searchText" :rules="[{ required: true, message: '请输入搜索词' }]">
        <a-input v-model:value="form.searchText" :maxlength="100" />
      </a-form-item>
      <a-form-item label="名称前缀"><a-input v-model:value="form.namePrefix" :maxlength="80" /></a-form-item>
      <a-form-item label="期望数量"><a-input-number v-model:value="form.count" :min="1" :max="30" /></a-form-item>
      <a-button type="primary" html-type="submit" :loading="loading">开始导入</a-button>
    </a-form>
    <a-result v-if="imported !== undefined" status="success" :title="`成功导入 ${imported} 张`"
      sub-title="返回数量只包含成功入库的图片；不足期望数量时可能存在来源或存储失败。" />
  </div>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { uploadPictureByBatchUsingPost } from '@/api/pictureController'
const form = reactive<API.PictureUploadByBatchRequest>({ searchText: '', namePrefix: '', count: 10 })
const loading = ref(false)
const imported = ref<number>()
const submit = async () => {
  if (loading.value) return
  loading.value = true
  imported.value = undefined
  try {
    const res = await uploadPictureByBatchUsingPost(form)
    if (res.data.code === 0 && res.data.data !== undefined) imported.value = res.data.data
    else message.error(res.data.message || '导入失败')
  } catch { message.error('请求失败，请刷新图库检查已导入结果，避免重复提交') }
  finally { loading.value = false }
}
</script>
<style scoped>
.batch-page { max-width: 720px; margin: auto; }
form { margin-top: 20px; }
</style>
