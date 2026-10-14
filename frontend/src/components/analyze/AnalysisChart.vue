<template>
  <a-empty v-if="!rows.length || !total" description="暂无可展示的数据" />
  <div v-else-if="kind === 'bars'" class="bars" role="img" :aria-label="title">
    <div v-for="row in rows" :key="row.label" class="bar-row">
      <span class="label" :title="row.label">{{ row.label }}</span>
      <div class="track"><div class="bar" :style="{ width: Math.max(1, row.value / maximum * 100) + '%' }" /></div>
      <span>{{ format(row.value) }}</span>
    </div>
  </div>
  <div v-else-if="kind === 'donut'" class="donut-layout">
    <div class="donut" :style="{ background: donut }" role="img" :aria-label="title">
      <div>{{ total }}<small>张图片</small></div>
    </div>
    <div><p v-for="(row, index) in rows" :key="row.label"><i :style="{ background: colors[index % colors.length] }" />{{ row.label }}：{{ row.value }}（{{ (row.value / total * 100).toFixed(1) }}%）</p></div>
  </div>
  <div v-else>
    <svg viewBox="0 0 620 240" class="line-chart" role="img" :aria-label="title">
      <line x1="38" y1="205" x2="598" y2="205" stroke="#aab5bd" />
      <line x1="38" y1="20" x2="38" y2="205" stroke="#aab5bd" />
      <text x="4" y="24" font-size="12">{{ maximum }}</text>
      <text x="22" y="205" font-size="12">0</text>
      <polyline :points="points" fill="none" stroke="#1677ff" stroke-width="3" />
      <circle v-for="(row, index) in rows" :key="row.label" :cx="x(index)" :cy="y(row.value)" r="3" fill="#1677ff"><title>{{ row.label }}：{{ row.value }}</title></circle>
      <text x="38" y="230" font-size="12">{{ rows[0]?.label }}</text>
      <text x="598" y="230" font-size="12" text-anchor="end">{{ rows[rows.length - 1]?.label }}</text>
    </svg>
    <p class="hint">悬停查看各期数量；未出现的时间段表示没有当前存量图片，未进行历史事件补录。</p>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { formatSize } from '@/utils'
const props = withDefaults(defineProps<{
  rows: { label: string; value: number }[]; kind?: 'bars' | 'line' | 'donut'; title: string; bytes?: boolean
}>(), { kind: 'bars', bytes: false })
const colors = ['#1677ff', '#13c2c2', '#faad14', '#f759ab', '#9254de']
const total = computed(() => props.rows.reduce((sum, row) => sum + row.value, 0))
const maximum = computed(() => Math.max(1, ...props.rows.map(row => row.value)))
const x = (index: number) => 38 + index * 560 / Math.max(1, props.rows.length - 1)
const y = (value: number) => 205 - value / maximum.value * 180
const points = computed(() => props.rows.map((row, index) => `${x(index)},${y(row.value)}`).join(' '))
const format = (value: number) => props.bytes ? formatSize(value) : String(value)
const donut = computed(() => {
  let offset = 0
  return 'conic-gradient(' + props.rows.map((row, index) => {
    const start = offset; offset += row.value / Math.max(1, total.value) * 100
    return `${colors[index % colors.length]} ${start}% ${offset}%`
  }).join(',') + ')'
})
</script>
<style scoped>
.bars { display: grid; gap: 12px; margin: 16px 0; }
.bar-row { display: grid; grid-template-columns: minmax(70px, 100px) minmax(20px, 1fr) auto; align-items: center; gap: 10px; }
.label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.track { background: #eef3fa; border-radius: 8px; height: 14px; }
.bar { background: #1677ff; border-radius: 8px; height: 100%; }
.donut-layout { display: flex; align-items: center; gap: 24px; flex-wrap: wrap; }
.donut { width: 160px; height: 160px; border-radius: 50%; display: grid; place-items: center; }
.donut div { background: white; width: 108px; height: 108px; border-radius: 50%; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 24px; }
small { font-size: 12px; color: #667; }
i { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 6px; }
.line-chart { width: 100%; max-height: 280px; }
.hint { font-size: 12px; color: #667; }
</style>
