<template>
  <a-button :disabled="!pictures.length || disabled" @click="open">批量编辑（{{ pictures.length }}）</a-button>
  <a-modal v-model:open="opened" title="批量编辑图片" ok-text="保存" cancel-text="取消" :confirm-loading="saving" :mask-closable="!saving" :closable="!saving" @ok="submit" @cancel="cancel">
    <p>已选 {{ pictures.length }} 张。勾选需要修改的字段；本批次全部成功后生效。</p>
    <a-form layout="vertical">
      <a-form-item><a-checkbox v-model:checked="changeCategory">修改分类</a-checkbox><a-input v-if="changeCategory" v-model:value="category" :maxlength="64" placeholder="留空清除分类" /></a-form-item>
      <a-form-item><a-checkbox v-model:checked="changeTags">替换标签</a-checkbox><a-select v-if="changeTags" v-model:value="tags" mode="tags" :max-tag-count="5" style="width: 100%" placeholder="不填写则清除标签" /></a-form-item>
      <a-form-item><a-checkbox v-model:checked="changeName">批量重命名</a-checkbox><a-input v-if="changeName" v-model:value="nameRule" :maxlength="128" placeholder="例如：项目素材{序号}-{原名}" /></a-form-item>
    </a-form>
    <p v-if="changeName">命名预览：{{ preview }}</p>
    <a-alert v-if="error" type="error" show-icon :message="error" />
  </a-modal>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { editPicturesByBatch, type BatchPicture } from '@/api/pictureDiscovery'
const props = defineProps<{ pictures: BatchPicture[]; spaceId?: string | number; disabled?: boolean }>()
const emit = defineEmits<{ saved: [] }>()
const opened = ref(false), saving = ref(false), error = ref('')
const changeCategory = ref(false), changeTags = ref(false), changeName = ref(false)
const category = ref(''), tags = ref<string[]>([]), nameRule = ref('素材{序号}')
const preview = computed(() => nameRule.value.split('{序号}').join('1').split('{原名}').join(props.pictures[0]?.name || ''))
let generation = 0
function open() { error.value = ''; changeCategory.value = false; changeTags.value = false; changeName.value = false; opened.value = true }
function cancel() { if (!saving.value) opened.value = false }
async function submit() {
  if (saving.value) return
  if (!changeCategory.value && !changeTags.value && !changeName.value) { error.value = '请选择要修改的字段'; return }
  if (changeName.value && !nameRule.value.trim()) { error.value = '请填写命名规则'; return }
  const revisions: Record<string, string | number> = {}, ids: (string | number)[] = []
  for (const picture of props.pictures) {
    if (!picture.id || picture.revision == null) { error.value = '图片缺少版本，请刷新列表后重新选择'; return }
    ids.push(picture.id); revisions[String(picture.id)] = picture.revision
  }
  const version = ++generation; saving.value = true; error.value = ''
  try {
    const result = await editPicturesByBatch({ pictureIdList: ids, spaceId: props.spaceId, expectedRevisions: revisions,
      ...(changeCategory.value ? { category: category.value } : {}), ...(changeTags.value ? { tags: tags.value } : {}), ...(changeName.value ? { nameRule: nameRule.value } : {}) })
    if (version !== generation) return
    if (result.data.code !== 0) { error.value = (result.data.message || '保存失败') + '；请刷新后重新选择'; return }
    message.success('已修改 ' + result.data.data?.updatedCount + ' 张图片'); opened.value = false; emit('saved')
  } catch { if (version === generation) error.value = '暂未确认保存结果，请刷新列表检查后再提交' }
  finally { if (version === generation) saving.value = false }
}
watch(() => props.spaceId, () => { generation++; opened.value = false; saving.value = false; error.value = '' })
</script>
