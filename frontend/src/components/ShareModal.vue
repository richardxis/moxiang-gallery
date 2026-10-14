<template>
  <a-button :disabled="!validId" @click="opened = true">分享</a-button>
  <a-modal v-model:open="opened" title="分享图片" :footer="null">
    <p>复制链接或扫码打开图片详情。</p>
    <a-typography-paragraph :copyable="{ text: link }">{{ link }}</a-typography-paragraph>
    <a-qrcode v-if="opened && link" :value="link" :size="200" />
    <p v-if="privatePicture" style="margin-top: 16px">空间图片需要接收者登录并拥有对应空间的访问权限。</p>
  </a-modal>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
const props = defineProps<{ pictureId?: string | number; privatePicture?: boolean }>()
const opened = ref(false)
const validId = computed(() => /^[1-9]\d{0,18}$/.test(String(props.pictureId || '')))
const link = computed(() => validId.value ? new URL(import.meta.env.BASE_URL.replace(/\/$/, '') + '/picture/' + props.pictureId, window.location.origin).toString() : '')
watch(() => props.pictureId, () => { opened.value = false })
</script>
