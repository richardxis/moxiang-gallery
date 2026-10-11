<template >
  <div id="app">
    <BasicLayout> </BasicLayout>
  </div>
</template>

<script setup lang="ts">
import BasicLayout from '@/layouts/BasicLayout.vue'

// import { healthUsingGet } from '@/api/mainController'
// import { useLoginUserStore } from '@/stores/userLoginUserStore.ts'

// 已经改为在权限校验文件中获取
// const loginUserStore = useLoginUserStore()
// loginUserStore.fetchLoginUser()
//
// healthUsingGet().then((res) => {
//   console.log(res)
// })
</script>

<style scoped>

</style>
