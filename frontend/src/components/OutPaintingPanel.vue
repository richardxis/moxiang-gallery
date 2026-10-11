<template>
  <a-card title="AI 扩图（生成新结果，不覆盖原图）" style="margin-top: 16px">
    <a-alert type="info" show-icon message="仅支持本人公共图片或管理员操作。调用云服务可能计费；停止等待不会取消云端任务。" />
    <a-space wrap style="margin-top: 16px">
      <a-select v-model:value="mode" :disabled="busy" style="width: 130px">
        <a-select-option value="scale">按倍数扩图</a-select-option>
        <a-select-option value="ratio">按宽高比扩图</a-select-option>
      </a-select>
      <template v-if="mode === 'scale'">
        <span>横向</span><a-input-number v-model:value="xScale" :min="1" :max="3" :step="0.1" :disabled="busy" />
        <span>纵向</span><a-input-number v-model:value="yScale" :min="1" :max="3" :step="0.1" :disabled="busy" />
      </template>
      <a-select v-else v-model:value="ratio" :disabled="busy" style="width: 100px">
        <a-select-option v-for="value in ratios" :key="value" :value="value">{{ value }}</a-select-option>
      </a-select>
      <a-button type="primary" :loading="busy" :disabled="busy" @click="create">创建任务</a-button>
      <a-button v-if="busy" @click="stop">停止等待</a-button>
      <a-button v-if="task && !busy && !terminal(task.taskStatus)" @click="resume">继续查询已有任务</a-button>
    </a-space>
    <p v-if="task">任务 {{ task.taskId }}：{{ task.taskStatus }}</p>
    <a-image v-if="task?.outputImageUrl" :src="task.outputImageUrl" style="max-height: 500px" />
    <p v-if="task?.outputImageUrl">结果为第三方临时地址，本阶段仅预览，尚未自动保存至 COS 或入库。</p>
  </a-card>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { createOutPaintingTask, queryOutPaintingTask, type OutPaintingTask } from '@/api/outPainting'

const props = defineProps<{ pictureId: string | number }>()
const mode = ref('scale'), xScale = ref<number | null>(1.5), yScale = ref<number | null>(1.5)
const ratios = ['1:1', '3:4', '4:3', '9:16', '16:9']
const ratio = ref('16:9'), busy = ref(false), task = ref<OutPaintingTask>()
let generation = 0, timer: ReturnType<typeof setTimeout> | undefined, controller: AbortController | undefined
let startedAt = 0, failures = 0
const terminal = (status: string) => ['SUCCEEDED', 'FAILED', 'CANCELED', 'UNKNOWN', 'EXPIRED', 'SUBMISSION_UNKNOWN'].includes(status)
function stop() {
  generation++
  if (timer) clearTimeout(timer)
  controller?.abort()
  busy.value = false
}
async function poll(token: number) {
  if (token !== generation || !task.value) return
  if (Date.now() - startedAt > 5 * 60 * 1000) {
    stop(); message.warning('等待超过 5 分钟，可稍后继续查询；云端任务未取消'); return
  }
  controller = new AbortController()
  try {
    const response = await queryOutPaintingTask(task.value.taskId, controller.signal)
    if (token !== generation) return
    if (response.data.code !== 0 || !response.data.data) {
      if ([40100, 40101, 40400, 50301].includes(response.data.code)) {
        stop(); message.error(response.data.message || '任务不可查询'); return
      }
      throw new Error(response.data.message || '任务查询失败')
    }
    failures = 0
    task.value = response.data.data
    if (terminal(task.value.taskStatus)) { busy.value = false; return }
  } catch {
    if (token !== generation) return
    failures++
    if (failures >= 3) { stop(); message.warning('连续查询失败，已停止等待；不会自动重新创建任务'); return }
  }
  timer = setTimeout(() => poll(token), Math.min(2000 + failures * 1000, 5000))
}
async function create() {
  if (busy.value) return
  if (mode.value === 'scale' && (!xScale.value || !yScale.value || (xScale.value <= 1 && yScale.value <= 1))) {
    message.warning('至少一个扩展倍数须大于 1'); return
  }
  stop()
  const token = generation
  busy.value = true
  task.value = undefined
  try {
    const response = await createOutPaintingTask(props.pictureId, mode.value === 'ratio'
      ? { outputRatio: ratio.value } : { xScale: xScale.value!, yScale: yScale.value! })
    if (token !== generation) return
    if (response.data.code !== 0 || !response.data.data) {
      busy.value = false; message.error(response.data.message || '创建失败，请勿自动重复提交'); return
    }
    task.value = response.data.data
    startedAt = Date.now(); failures = 0
    if (terminal(task.value.taskStatus)) { busy.value = false; return }
    await poll(token)
  } catch {
    if (token === generation) { busy.value = false; message.error('创建响应未知，请勿立即重复提交，以免重复计费') }
  }
}
function resume() {
  if (!task.value || busy.value) return
  stop(); busy.value = true; startedAt = Date.now(); failures = 0
  void poll(generation)
}
watch(() => props.pictureId, () => { stop(); task.value = undefined })
onBeforeUnmount(stop)
</script>
