<template>
  <div class="analysis-page">
    <h2>空间图库分析 · {{ scopeName }}</h2>
    <a-alert show-icon type="info" message="统计当前未删除图片，包含管理范围内的待审核图片；用户趋势依据现存图片创建时间，不代表完整上传历史。" />
    <a-space wrap class="toolbar">
      <template v-if="isAdmin">
        <a-button @click="router.push('/space_analyze?queryAll=true')">全站分析</a-button>
        <a-button @click="router.push('/space_analyze?queryPublic=true')">公共图库</a-button>
      </template>
      <a-button :loading="loading" @click="reload">刷新分析</a-button>
      <a-button @click="router.push('/my_space')">我的空间</a-button>
      <a-button @click="router.push('/team_space')">团队空间</a-button>
    </a-space>
    <a-alert v-if="scopeError" type="error" show-icon :message="scopeError" />
    <a-row v-else :gutter="[16, 16]">
      <a-col :xs="24" :lg="12">
        <a-card title="空间使用情况" :loading="loading">
          <a-alert v-if="errors.usage" type="error" :message="errors.usage" />
          <template v-else-if="usage">
            <a-row :gutter="16">
              <a-col :span="12"><a-statistic title="已使用存储" :value="formatSize(Number(usage.usedSize))" /><p v-if="usage.maxSize != null">配额 {{ formatSize(Number(usage.maxSize)) }}</p><a-progress v-if="usage.sizeUsageRatio != null" type="dashboard" :percent="Math.min(100, usage.sizeUsageRatio)" :format="() => usage!.sizeUsageRatio + '%'" /></a-col>
              <a-col :span="12"><a-statistic title="当前图片数量" :value="Number(usage.usedCount)" /><p v-if="usage.maxCount != null">配额 {{ usage.maxCount }} 张</p><a-progress v-if="usage.countUsageRatio != null" type="dashboard" :percent="Math.min(100, usage.countUsageRatio)" :format="() => usage!.countUsageRatio + '%'" /></a-col>
            </a-row>
            <p v-if="usage.maxSize == null">公共图库与全站统计不设容量配额。</p>
          </template>
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card title="图片分类分析" :loading="loading">
          <a-alert v-if="errors.category" type="error" :message="errors.category" />
          <template v-else><h4>分类图片数量</h4><AnalysisChart :rows="categoryCounts" title="分类图片数量" /><h4>分类存储占用</h4><AnalysisChart :rows="categoryBytes" title="分类存储大小" bytes /></template>
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card title="常用标签" :loading="loading">
          <a-alert v-if="errors.tag" type="error" :message="errors.tag" />
          <a-empty v-else-if="!tags.length" description="暂无标签" />
          <div v-else class="tag-cloud"><span v-for="item in tags" :key="item.tag" :style="{ fontSize: (14 + Number(item.count) / maxTagCount * 18) + 'px' }" :title="item.count + ' 张关联图片'">{{ item.tag }} <small>{{ item.count }}</small></span></div>
          <p class="hint">按关联图片数降序，最多展示前 {{ topN }} 个；同一图片重复标签只计一次。</p>
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card title="图片大小分布" :loading="loading"><a-alert v-if="errors.size" type="error" :message="errors.size" /><AnalysisChart v-else :rows="sizeRows" kind="donut" title="图片大小分布" /><p class="hint">1KB = 1024 字节；大小缺失或为负的历史异常记录不进入分段。</p></a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card title="用户上传趋势" :loading="loading">
          <a-space wrap class="toolbar">
            <a-radio-group v-model:value="timeDimension" button-style="solid" @change="reload"><a-radio-button value="day">日</a-radio-button><a-radio-button value="week">周</a-radio-button><a-radio-button value="month">月</a-radio-button></a-radio-group>
            <a-input v-model:value="userId" placeholder="用户 ID（留空为全部）" :maxlength="19" />
            <label>起始日 <input v-model="startDate" type="date" /></label><label>结束日 <input v-model="endDate" type="date" /></label>
            <a-button @click="reload">应用筛选</a-button>
          </a-space>
          <a-alert v-if="errors.user" type="error" :message="errors.user" /><AnalysisChart v-else :rows="userRows" kind="line" title="现存图片创建时间趋势" />
          <a-collapse v-if="activities.length"><a-collapse-panel key="data" header="查看各期明细"><p v-for="item in activities" :key="item.period">{{ item.period }}：{{ item.count }} 张</p></a-collapse-panel></a-collapse>
        </a-card>
      </a-col>
      <a-col v-if="isAdmin" :xs="24" :lg="12">
        <a-card title="空间存储排行（全站）" :loading="loading">
          <a-space class="toolbar"><span>前 N 名／标签数</span><a-input-number v-model:value="topN" :min="1" :max="50" /><a-button @click="reload">更新</a-button></a-space>
          <a-alert v-if="errors.rank" type="error" :message="errors.rank" />
          <template v-else><AnalysisChart :rows="rankRows" title="空间存储排行" bytes /><p v-for="(item, index) in ranks" :key="item.id"><router-link :to="{ path: '/space_analyze', query: { spaceId: item.id } }">{{ index + 1 }}. {{ item.spaceName }}</router-link> · {{ formatSize(Number(item.totalSize)) }} · {{ item.totalCount }} 张</p></template>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/userLoginUserStore'
import { formatSize } from '@/utils'
import { analyzeSpace, type AnalysisScope, type Usage, type Category, type Tag, type SizeBucket, type UserActivity, type SpaceRank } from '@/api/spaceAnalyze'
import AnalysisChart from '@/components/analyze/AnalysisChart.vue'
const route = useRoute(), router = useRouter(), login = useLoginUserStore()
const isAdmin = computed(() => login.loginUser.userRole === 'admin')
const loading = ref(false), errors = ref<Record<string, string>>({})
const usage = ref<Usage>(), categories = ref<Category[]>([]), tags = ref<Tag[]>([])
const sizes = ref<SizeBucket[]>([]), activities = ref<UserActivity[]>([]), ranks = ref<SpaceRank[]>([])
const timeDimension = ref<'day' | 'week' | 'month'>('day'), userId = ref(''), topN = ref(10)
const startDate = ref(''), endDate = ref('')
let generation = 0
const trueValue = (value: unknown) => value === 'true' || value === '1'
const scope = computed<AnalysisScope>(() => ({
  ...(typeof route.query.spaceId === 'string' ? { spaceId: route.query.spaceId } : {}),
  queryAll: trueValue(route.query.queryAll), queryPublic: trueValue(route.query.queryPublic),
}))
const scopeError = computed(() => {
  const value = scope.value
  if (Number(!!value.spaceId) + Number(!!value.queryAll) + Number(!!value.queryPublic) !== 1) return '请从个人空间、团队空间或管理员分析入口进入，并只选择一个分析范围。'
  if (value.spaceId && !/^[1-9]\d{0,18}$/.test(value.spaceId)) return '空间 ID 无效。'
  if ((value.queryAll || value.queryPublic) && !isAdmin.value) return '公共与全站分析仅系统管理员可查看。'
  return ''
})
const scopeName = computed(() => scope.value.queryAll ? '全站' : scope.value.queryPublic ? '公共图库' : '空间 ' + (scope.value.spaceId || '未指定'))
const categoryCounts = computed(() => categories.value.map(item => ({ label: item.category, value: Number(item.count) })))
const categoryBytes = computed(() => categories.value.map(item => ({ label: item.category, value: Number(item.totalSize) })))
const maxTagCount = computed(() => Math.max(1, ...tags.value.map(item => Number(item.count))))
const sizeRows = computed(() => sizes.value.map(item => ({ label: item.sizeRange, value: Number(item.count) })))
const userRows = computed(() => activities.value.map(item => ({ label: item.period, value: Number(item.count) })))
const rankRows = computed(() => ranks.value.map((item, index) => ({ label: `${index + 1}. ${item.spaceName}`, value: Number(item.totalSize) })))
function clear() { usage.value = undefined; categories.value = []; tags.value = []; sizes.value = []; activities.value = []; ranks.value = []; errors.value = {} }
async function reload() {
  const current = ++generation
  clear(); loading.value = false
  if (scopeError.value) return
  if (userId.value && !/^[1-9]\d{0,18}$/.test(userId.value)) { message.error('请输入合法的用户 ID'); return }
  if (startDate.value && endDate.value && startDate.value > endDate.value) { message.error('起始日不能晚于结束日'); return }
  loading.value = true
  const body = { ...scope.value, topN: topN.value || 10, timeDimension: timeDimension.value,
    ...(userId.value ? { userId: userId.value } : {}), ...(startDate.value ? { startDate: startDate.value } : {}), ...(endDate.value ? { endDate: endDate.value } : {}) }
  // Each card can report its own error; a late response from a previous scope is discarded.
  async function load<T>(kind: 'usage' | 'category' | 'tag' | 'size' | 'user' | 'rank', apply: (value: T) => void) {
    try {
      const response = await analyzeSpace<T>(kind, body)
      if (current !== generation) return
      if (response.data.code !== 0 || response.data.data == null) { errors.value[kind] = response.data.message || '分析失败'; return }
      apply(response.data.data)
    } catch { if (current === generation) errors.value[kind] = '请求失败，请检查后端服务或重新登录。' }
  }
  await Promise.allSettled([
    load<Usage>('usage', value => usage.value = value), load<Category[]>('category', value => categories.value = value),
    load<Tag[]>('tag', value => tags.value = value), load<SizeBucket[]>('size', value => sizes.value = value),
    load<UserActivity[]>('user', value => activities.value = value),
    ...(isAdmin.value ? [load<SpaceRank[]>('rank', value => ranks.value = value)] : []),
  ])
  if (current === generation) loading.value = false
}
watch(() => [route.fullPath, login.loginUser.id, login.loginUser.userRole], () => { void reload() }, { immediate: true })
</script>
<style scoped>
.analysis-page { padding-bottom: 24px; }
.toolbar { margin: 16px 0; }
.tag-cloud { display: flex; flex-wrap: wrap; gap: 12px 18px; align-items: center; min-height: 150px; color: #1677ff; }
.hint { color: #667; font-size: 12px; margin-top: 16px; }
label { display: inline-flex; gap: 6px; align-items: center; }
</style>
