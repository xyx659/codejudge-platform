<!-- 管理端：用户管理，展示后端接口联通状态 -->
<template>
  <div class="page">
    <h1>用户管理</h1>
    <p class="desc">管理学生、教师与管理员账号</p>
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

// 挂载后请求管理端用户接口，展示联通状态
onMounted(async () => {
  try {
    const res = await get('/admin/users')
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
