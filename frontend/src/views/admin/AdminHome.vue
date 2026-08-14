<!-- 管理端：工作台，展示后端接口联通状态 -->
<template>
  <div class="page">
    <h1>工作台</h1>
    <p class="desc">系统配置、日志审计与全局考试监控</p>
    <div class="card">
      <div class="label">后端接口状态</div>
      <div>{{ status }}</div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { get } from '../../api/http'

const status = ref('加载中...')

// 挂载后请求管理端监控接口，展示联通状态
onMounted(async () => {
  try {
    const res = await get('/admin/monitor')
    status.value = `${res.message} / ${res.data.endpoint}`
  } catch (error) {
    status.value = '后端未启动'
  }
})
</script>

<style scoped>
.page h1 {
  font-size: 24px;
  margin-bottom: 8px;
}

.page .desc {
  color: #6b7280;
  margin-bottom: 20px;
}

.card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
}

.card .label {
  font-weight: 600;
  margin-bottom: 6px;
}
</style>
