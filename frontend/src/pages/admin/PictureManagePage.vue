<template>
  <div id="pictureManagePage">
    <!-- 搜索栏 --->
    <a-flex justify="space-between">
      <h2>图片管理</h2>
      <a-button type="primary" href="/add_picture" target="_blank">+ 创建图片</a-button>
    </a-flex>
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="关键词" name="searchText">
        <a-input
          v-model:value="searchParams.searchText"
          placeholder="从名称和简介搜索"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="类型" name="category">
        <a-input v-model:value="searchParams.category" placeholder="请输入类型" allow-clear />
      </a-form-item>
      <a-form-item label="标签" name="tags">
        <a-select
          v-model:value="searchParams.tags"
          mode="tags"
          placeholder="请输入标签"
          style="min-width: 180px"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="审核状态">
        <a-select v-model:value="searchParams.reviewStatus" :options="PIC_REVIEW_STATUS_OPTIONS"
          style="min-width: 120px" allow-clear />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
    </a-form>

    <!-- 表格 --->
    <a-table
      :columns="columns"
      :data-source="dataList"
      :pagination="pagination"
      @change="doTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'url'">
          <a-image :src="record.url" :width="120" />
        </template>
        <!-- 标签 -->
        <template v-if="column.dataIndex === 'tags'">
          <a-space wrap>
            <a-tag v-for="tag in JSON.parse(record.tags || '[]')" :key="tag">{{ tag }}</a-tag>
          </a-space>
        </template>
        <!-- 图片信息 -->
        <template v-if="column.dataIndex === 'reviewStatus'">
          {{ PIC_REVIEW_STATUS_MAP[record.reviewStatus] ?? '未知' }}
          <div>{{ record.reviewMessage }}</div>
        </template>
        <template v-if="column.dataIndex === 'picInfo'">
          <div>格式：{{ record.picFormat }}</div>
          <div>宽度：{{ record.picWidth }}</div>
          <div>高度：{{ record.picHeight }}</div>
          <div>宽高比：{{ record.picScale }}</div>
          <div>大小：{{ (record.picSize / 1024).toFixed(2) }}KB</div>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.dataIndex === 'editTime'">
          {{ dayjs(record.editTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" :href="`/add_picture?id=${record.id}`" target="_blank">编辑</a-button>
            <a-button type="link" @click="openReview(record)">审核</a-button>
            <a-button type="link" danger @click="doDelete(record.id)">删除</a-button>
          </a-space>
        </template>
      </template>

    </a-table>
    <a-modal v-model:open="reviewOpen" title="审核图片" :confirm-loading="reviewLoading" @ok="submitReview">
      <a-radio-group v-model:value="reviewForm.reviewStatus">
        <a-radio :value="1">通过</a-radio><a-radio :value="2">拒绝</a-radio>
      </a-radio-group>
      <a-textarea v-model:value="reviewForm.reviewMessage" placeholder="审核说明（拒绝时必填）"
        :maxlength="512" :rows="3" style="margin-top: 16px" />
    </a-modal>
  </div>
</template>
<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  deletePictureUsingPost,
  doPictureReviewUsingPost,
  listPictureByPageUsingPost
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { PIC_REVIEW_STATUS_MAP, PIC_REVIEW_STATUS_OPTIONS } from '@/constants/picture'
const columns = [
  { title: '审核状态', dataIndex: 'reviewStatus' },
  {
    title: 'id',
    dataIndex: 'id',
    width: 80,
  },
  {
    title: '图片',
    dataIndex: 'url',
  },
  {
    title: '名称',
    dataIndex: 'name',
  },
  {
    title: '简介',
    dataIndex: 'introduction',
    ellipsis: true,
  },
  {
    title: '类型',
    dataIndex: 'category',
  },
  {
    title: '标签',
    dataIndex: 'tags',
  },
  {
    title: '图片信息',
    dataIndex: 'picInfo',
  },
  {
    title: '用户 id',
    dataIndex: 'userId',
    width: 80,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
  },
  {
    title: '编辑时间',
    dataIndex: 'editTime',
  },
  {
    title: '操作',
    key: 'action',
  },
]


// 数据
const dataList = ref<API.Picture[]>([])
const reviewOpen = ref(false)
const reviewLoading = ref(false)
const reviewForm = reactive<API.PictureReviewRequest>({ reviewStatus: 1, reviewMessage: '' })
const openReview = (picture: API.Picture) => {
  reviewForm.id = picture.id
  reviewForm.expectedRevision = picture.revision
  reviewForm.reviewStatus = picture.reviewStatus === 1 ? 2 : 1
  reviewForm.reviewMessage = ''
  reviewOpen.value = true
}
const submitReview = async () => {
  if (reviewLoading.value) return
  if (reviewForm.reviewStatus === 2 && !reviewForm.reviewMessage?.trim()) {
    message.warning('请填写拒绝原因')
    return
  }
  reviewLoading.value = true
  try {
    const res = await doPictureReviewUsingPost({ ...reviewForm })
    if (res.data.code === 0) {
      message.success('审核成功')
      reviewOpen.value = false
      await fetchData()
    } else message.error(res.data.message || '审核失败')
  } catch { message.error('审核请求失败') }
  finally { reviewLoading.value = false }
}
const total = ref(0)

// 搜索条件
const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 10,
  sortField: 'createTime',
  sortOrder: 'descend',
})

// 分页参数
const pagination = computed(() => {
  return {
    current: searchParams.current ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
  }
})

// 获取数据
const fetchData = async () => {
  const res = await listPictureByPageUsingPost({
    ...searchParams,
  })
  if (res.data.data) {
    dataList.value = res.data.data.records ?? []
    total.value = res.data.data.total ?? 0
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
}

// 页面加载时请求一次
onMounted(() => {
  fetchData()
})

// 获取数据
const doSearch = () => {
  // 重置搜索条件
  searchParams.current = 1
  fetchData()
}

// 表格变化处理
const doTableChange = (page: any) => {
  searchParams.current = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

// 删除数据
const doDelete = async (id: string) => {
  if (!id) {
    return
  }
  const row = dataList.value.find(item => String(item.id) === String(id))
  const res = await deletePictureUsingPost({ id, expectedRevision: row?.revision })
  if (res.data.code === 0) {
    message.success('删除成功')
    // 刷新数据
    fetchData()
  } else {
    message.error('删除失败')
  }
}
</script>

