<template>
  <a-card title="团队实时协作编辑（单实例）" style="margin-top: 16px">
    <a-alert type="info" show-icon message="同步旋转/缩放，可导出矩形裁剪结果并保存；仅单实例，文件隐私仍取决于存储配置。" />
    <p>连接：{{ status }}；草稿版本：{{ state.version }}；图片版本：{{ state.pictureRevision }}；编辑者：{{ state.ownerUserId || '无人' }}</p>
    <a-alert v-if="notice" :message="notice" type="info" style="margin-bottom: 12px" />
    <div class="preview-viewport"><img :src="imageUrl" alt="协作预览" :style="{ transform: `rotate(${state.rotation}deg) scale(${state.scale})` }" /></div>
    <a-space wrap style="margin-top: 16px">
      <a-button v-if="!socket" @click="start">连接协作</a-button>
      <a-button v-if="socket" :disabled="saving" @click="stop">断开</a-button>
      <a-button :disabled="!ready || !!state.ownerSessionId || saving" @click="send({ type: 'ENTER_EDIT' })">申请编辑锁</a-button>
      <a-button :disabled="!ownsLock || saving" @click="send({ type: 'EXIT_EDIT', lockToken })">释放编辑锁</a-button>
      <a-button v-for="item in actions" :key="item.action" :disabled="!ownsLock || pending || saving" @click="edit(item.action)">{{ item.label }}</a-button>
    </a-space>
    <div style="margin-top: 16px">
      <a-checkbox v-model:checked="cropEnabled" :disabled="!ownsLock || saving">矩形裁剪</a-checkbox>
      <p>裁剪坐标基于旋转/缩放后的像素；范围 {{ geometry.width }} × {{ geometry.height }}。裁剪框是持锁者本地参数，最终结果保存后共享。</p>
      <a-space v-if="cropEnabled" wrap>
        <span>X</span><a-input-number v-model:value="crop.x" :min="0" :max="Math.max(0, geometry.width - 1)" :precision="0" :disabled="saving || !ownsLock" />
        <span>Y</span><a-input-number v-model:value="crop.y" :min="0" :max="Math.max(0, geometry.height - 1)" :precision="0" :disabled="saving || !ownsLock" />
        <span>宽</span><a-input-number v-model:value="crop.width" :min="1" :max="geometry.width" :precision="0" :disabled="saving || !ownsLock" />
        <span>高</span><a-input-number v-model:value="crop.height" :min="1" :max="geometry.height" :precision="0" :disabled="saving || !ownsLock" />
      </a-space>
      <a-space wrap style="margin-top: 12px; display: flex">
        <a-button type="primary" :loading="saving" :disabled="!canSave" @click="save">保存编辑结果</a-button>
        <a-button v-if="uncertain && lastAttempt" :disabled="saving" @click="resolveAttempt">查询或重试上次保存</a-button>
        <span v-if="sourceLoading">正在读取可导出图片…</span>
      </a-space>
      <p>导出 PNG 最大 2 MiB，单边 ≤8192、总像素 ≤1600 万。保存成功重置预览并释放锁；图片、额度和版本一起更新。</p>
    </div>
  </a-card>
</template>
<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, shallowRef, watch } from 'vue'
import { getCollaborationResult, loadPictureSource, saveCollaboration, type SaveRequest } from '@/api/collaboration'
const props = defineProps<{ pictureId: string | number; imageUrl: string; pictureRevision: string }>()
const emit = defineEmits<{ saved: [] }>()
type Snapshot = { epoch: string; version: string; pictureRevision: string; rotation: number; scale: number; ownerSessionId: string | null; ownerUserId: string | null }
const initial = (): Snapshot => ({ epoch: '', version: '0', pictureRevision: props.pictureRevision, rotation: 0, scale: 1, ownerSessionId: null, ownerUserId: null })
const state = ref<Snapshot>(initial()), socket = shallowRef<WebSocket | null>(null)
const status = ref('未连接'), notice = ref(''), sessionId = ref(''), lockToken = ref(''), ready = ref(false), pending = ref(false)
const saving = ref(false), uncertain = ref(false), cropEnabled = ref(false)
const sourceImage = shallowRef<HTMLImageElement | null>(null), sourceRevision = ref(''), sourceLoading = ref(false)
const crop = reactive({ x: 0, y: 0, width: 1, height: 1 })
const lastAttempt = shallowRef<{ body: SaveRequest; blob: Blob } | null>(null)
const confirmed = new Set<string>()
const ownsLock = computed(() => ready.value && !!lockToken.value && state.value.ownerSessionId === sessionId.value)
const geometry = computed(() => {
  const image = sourceImage.value
  if (!image) return { width: 0, height: 0 }
  const swapped = state.value.rotation === 90 || state.value.rotation === 270
  return { width: Math.max(1, Math.round((swapped ? image.naturalHeight : image.naturalWidth) * state.value.scale)), height: Math.max(1, Math.round((swapped ? image.naturalWidth : image.naturalHeight) * state.value.scale)) }
})
const cropValid = computed(() => [crop.x, crop.y, crop.width, crop.height].every(Number.isInteger) && crop.x >= 0 && crop.y >= 0 && crop.width > 0 && crop.height > 0 && crop.x + crop.width <= geometry.value.width && crop.y + crop.height <= geometry.value.height)
const canSave = computed(() => ownsLock.value && !pending.value && !saving.value && !sourceLoading.value && !!sourceImage.value && sourceRevision.value === state.value.pictureRevision && geometry.value.width <= 8192 && geometry.value.height <= 8192 && geometry.value.width * geometry.value.height <= 16000000 && (!cropEnabled.value || cropValid.value))
const actions = [{ action: 'ROTATE_LEFT', label: '向左旋转' }, { action: 'ROTATE_RIGHT', label: '向右旋转' }, { action: 'ZOOM_IN', label: '放大' }, { action: 'ZOOM_OUT', label: '缩小' }, { action: 'RESET', label: '重置' }]
let generation = 0, stopped = true, attempts = 0, lastReceived = 0, sourceGeneration = 0, blobUrl = '', requestedRefreshRevision = ''
let heartbeat: ReturnType<typeof setInterval> | undefined, reconnect: ReturnType<typeof setTimeout> | undefined
function resetCrop() { crop.x = 0; crop.y = 0; crop.width = Math.max(1, geometry.value.width); crop.height = Math.max(1, geometry.value.height) }
async function loadSource() {
  const version = ++sourceGeneration; sourceLoading.value = true
  try {
    const content = await loadPictureSource(props.pictureId)
    if (version !== sourceGeneration) return
    const url = URL.createObjectURL(content.blob), image = new Image()
    try { await new Promise<void>((resolve, reject) => { image.onload = () => resolve(); image.onerror = () => reject(new Error('图片解码失败')); image.src = url }) }
    catch (error) { URL.revokeObjectURL(url); throw error }
    if (version !== sourceGeneration) { URL.revokeObjectURL(url); return }
    if (blobUrl) URL.revokeObjectURL(blobUrl)
    blobUrl = url; sourceImage.value = image; sourceRevision.value = content.revision; resetCrop()
  } catch (error) { if (version === sourceGeneration) { sourceImage.value = null; sourceRevision.value = ''; notice.value = error instanceof Error ? error.message : '读取图片失败' } }
  finally { if (version === sourceGeneration) sourceLoading.value = false }
}
function clearTimers() { if (heartbeat) clearInterval(heartbeat); if (reconnect) clearTimeout(reconnect); heartbeat = undefined; reconnect = undefined }
function send(event: Record<string, unknown>) { if (socket.value?.readyState === WebSocket.OPEN && ready.value) socket.value.send(JSON.stringify(event)) }
function edit(action: string) { if (!ownsLock.value || pending.value || saving.value) return; pending.value = true; send({ type: 'EDIT_ACTION', action, epoch: state.value.epoch, version: state.value.version, lockToken: lockToken.value }) }
function stop() { stopped = true; generation++; clearTimers(); const old = socket.value; socket.value = null; ready.value = false; lockToken.value = ''; sessionId.value = ''; pending.value = false; state.value = initial(); status.value = '已断开'; old?.close(1000, '主动退出') }
function start() { stop(); stopped = false; attempts = 0; notice.value = ''; connect(generation) }
function connect(version: number) {
  if (stopped || version !== generation) return
  status.value = '连接中'; ready.value = false; lockToken.value = ''; sessionId.value = ''; pending.value = false
  let ws: WebSocket
  try { const url = new URL(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8123'); if (!['http:', 'https:'].includes(url.protocol)) throw new Error('协议无效'); url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'; url.pathname = '/api/ws/picture/edit'; url.search = ''; url.hash = ''; url.searchParams.set('pictureId', String(props.pictureId)); ws = new WebSocket(url); socket.value = ws }
  catch { stopped = true; socket.value = null; status.value = '配置错误'; notice.value = '请核对 VITE_API_BASE_URL'; return }
  ws.onopen = () => { if (version !== generation) return; status.value = '等待快照'; lastReceived = Date.now(); heartbeat = setInterval(() => { if (Date.now() - lastReceived > 25000) { ws.close(4000, '心跳超时'); return }; send({ type: 'PING' }); if (ownsLock.value) send({ type: 'RENEW', lockToken: lockToken.value }) }, 10000) }
  ws.onmessage = event => {
    if (version !== generation || socket.value !== ws) return
    try {
      const packet = JSON.parse(event.data)
      if (typeof packet.type !== 'string' || typeof packet.epoch !== 'string' || typeof packet.version !== 'string' || !/^\d+$/.test(packet.version)) return
      if (typeof packet.rotation !== 'number' || ![0, 90, 180, 270].includes(packet.rotation) || typeof packet.scale !== 'number' || packet.scale < 0.2 || packet.scale > 3) return
      lastReceived = Date.now()
      if (packet.type === 'WELCOME') { sessionId.value = packet.sessionId; ready.value = true; attempts = 0; status.value = '已连接'; requestedRefreshRevision = '' }
      if (!ready.value || (state.value.epoch && state.value.epoch !== packet.epoch) || BigInt(packet.version) < BigInt(state.value.version)) return
      const pictureRevision = typeof packet.pictureRevision === 'string' && /^\d+$/.test(packet.pictureRevision) ? packet.pictureRevision : props.pictureRevision
      state.value = { epoch: packet.epoch, version: packet.version, pictureRevision, rotation: packet.rotation, scale: packet.scale, ownerSessionId: packet.ownerSessionId, ownerUserId: packet.ownerUserId }
      if (packet.type === 'LOCK_GRANTED' && packet.ownerSessionId === sessionId.value) lockToken.value = packet.lockToken
      if (packet.ownerSessionId !== sessionId.value) lockToken.value = ''
      if (packet.type === 'EDIT_ACTION' || packet.type === 'ERROR') pending.value = false
      if (packet.type === 'ERROR') notice.value = packet.message || '操作被拒绝，请刷新状态'
      if (packet.type === 'PICTURE_SAVED' || packet.type === 'PICTURE_CHANGED') {
        pending.value = false; resetCrop()
        if (typeof packet.requestId === 'string') { confirmed.add(packet.requestId); if (confirmed.size > 8) confirmed.delete(confirmed.values().next().value!); if (lastAttempt.value?.body.requestId === packet.requestId) { lastAttempt.value = null; uncertain.value = false } }
        notice.value = '图片已更新到版本 ' + pictureRevision
      }
      if ((packet.type === 'PICTURE_SAVED' || packet.type === 'PICTURE_CHANGED' || pictureRevision !== props.pictureRevision) && requestedRefreshRevision !== pictureRevision) { requestedRefreshRevision = pictureRevision; emit('saved') }
    } catch { notice.value = '收到无效协作消息' }
  }
  ws.onclose = event => { if (version !== generation || socket.value !== ws) return; clearTimers(); socket.value = null; ready.value = false; lockToken.value = ''; sessionId.value = ''; pending.value = false; state.value = initial(); if (stopped) return; if (event.code === 1008 || attempts >= 5) { stopped = true; status.value = '连接停止'; notice.value = '权限或连接变化，请刷新权限后手动重连'; return }; status.value = '等待重连'; reconnect = setTimeout(() => connect(version), Math.min(10000, 500 * 2 ** attempts++) + Math.random() * 300) }
  ws.onerror = () => { if (version === generation) notice.value = '连接失败，请检查登录、后端地址和 Origin 白名单' }
}
async function submit(attempt: { body: SaveRequest; blob: Blob }) {
  lastAttempt.value = attempt
  try {
    const response = await saveCollaboration(attempt.blob, attempt.body)
    if (response.data.code === 0) { lastAttempt.value = null; uncertain.value = false; notice.value = '保存成功，图片版本 ' + String(response.data.data?.revision ?? ''); if (!confirmed.has(attempt.body.requestId)) emit('saved') }
    else if ([40000, 40100, 40101, 40300, 40400, 40900].includes(Number(response.data.code))) { lastAttempt.value = null; uncertain.value = false; notice.value = response.data.message || '保存被拒绝，请刷新' }
    else { uncertain.value = true; notice.value = '保存结果未确认，请查询原请求，勿重复导出提交' }
  } catch { if (confirmed.has(attempt.body.requestId)) { lastAttempt.value = null; uncertain.value = false; notice.value = '已收到服务端保存成功通知' } else { uncertain.value = true; notice.value = '网络异常，保存结果未确认，可查询或重试原请求' } }
}
async function save() {
  if (!canSave.value || !sourceImage.value) return
  saving.value = true
  try {
    const snapshot = { ...state.value }, image = sourceImage.value, bounds = { ...geometry.value }
    const rectangle = cropEnabled.value ? { ...crop } : { x: 0, y: 0, width: bounds.width, height: bounds.height }
    const base = document.createElement('canvas'); base.width = bounds.width; base.height = bounds.height
    const context = base.getContext('2d'); if (!context) throw new Error('浏览器不能导出图片')
    context.translate(base.width / 2, base.height / 2); context.rotate(snapshot.rotation * Math.PI / 180); context.scale(snapshot.scale, snapshot.scale); context.drawImage(image, -image.naturalWidth / 2, -image.naturalHeight / 2)
    const canvas = document.createElement('canvas'); canvas.width = rectangle.width; canvas.height = rectangle.height
    const output = canvas.getContext('2d'); if (!output) throw new Error('浏览器不能导出图片')
    output.drawImage(base, rectangle.x, rectangle.y, rectangle.width, rectangle.height, 0, 0, rectangle.width, rectangle.height)
    const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob(value => value ? resolve(value) : reject(new Error('图片导出失败')), 'image/png'))
    if (!blob.size || blob.size > 2 * 1024 * 1024) throw new Error('导出结果超过 2 MiB，请缩小或裁剪')
    if (!ownsLock.value || snapshot.epoch !== state.value.epoch || snapshot.version !== state.value.version) throw new Error('导出期间协作状态已变化，请重试')
    await submit({ blob, body: { id: props.pictureId, expectedRevision: snapshot.pictureRevision, requestId: crypto.randomUUID(), connectionId: sessionId.value, lockToken: lockToken.value, epoch: snapshot.epoch, draftVersion: snapshot.version, ...(cropEnabled.value ? { cropX: rectangle.x, cropY: rectangle.y, cropWidth: rectangle.width, cropHeight: rectangle.height } : {}) } })
  } catch (error) { notice.value = error instanceof Error ? error.message : '保存失败' }
  finally { saving.value = false }
}
async function resolveAttempt() {
  const attempt = lastAttempt.value; if (!attempt || saving.value) return
  saving.value = true
  try { const response = await getCollaborationResult(attempt.body.requestId); if (response.data.code === 0) { lastAttempt.value = null; uncertain.value = false; notice.value = '已确认保存成功，图片版本 ' + String(response.data.data?.revision ?? ''); emit('saved') } else if (response.data.code === 40400 && ownsLock.value && state.value.epoch === attempt.body.epoch && state.value.version === attempt.body.draftVersion) await submit(attempt); else notice.value = response.data.message || '暂未确认提交结果，请稍后查询；状态变化后需要重新编辑' }
  catch { notice.value = '暂时无法查询保存结果，请稍后重试' }
  finally { saving.value = false }
}
watch(() => [geometry.value.width, geometry.value.height], resetCrop)
watch(() => [props.pictureId, props.imageUrl, props.pictureRevision, state.value.pictureRevision], () => { void loadSource() }, { immediate: true })
watch(() => props.pictureId, stop)
onUnmounted(() => { stop(); sourceGeneration++; if (blobUrl) URL.revokeObjectURL(blobUrl) })
</script>
<style scoped>
.preview-viewport { height: 420px; display: flex; align-items: center; justify-content: center; overflow: hidden; background: #f4f4f4; }
.preview-viewport img { max-width: 80%; max-height: 80%; object-fit: contain; }
</style>
