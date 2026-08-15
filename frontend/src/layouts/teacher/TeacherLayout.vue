<!-- 教师端布局：左侧导航栏 + 右侧内容区 -->
<template>
  <div class="layout">
    <aside class="sidebar">
      <h2>教师端</h2>
      <nav>
        <router-link to="/teacher/home">工作台</router-link>
        <router-link to="/teacher/questions">题库管理</router-link>
      </nav>
      <button class="logout" @click="logout">退出登录</button>
    </aside>
    <main class="content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { clearAuth } from '../../utils/auth'

const router = useRouter()

function logout() {
  clearAuth()
  router.replace('/teacher/login')
}
</script>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  width: 220px;
  padding: 24px 16px;
  background: #153e31;
  color: #fff;
}

.sidebar h2 {
  font-size: 18px;
  margin-bottom: 24px;
}

.sidebar nav {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sidebar a {
  color: #d1fae5;
  text-decoration: none;
  padding: 10px 12px;
  border-radius: 6px;
}

.sidebar a.router-link-active {
  background: #059669;
  color: #fff;
}

.sidebar .logout {
  margin-top: 24px;
  width: 100%;
  padding: 10px 12px;
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 6px;
  color: #fff;
  cursor: pointer;
  font-size: 14px;
}

.sidebar .logout:hover {
  background: rgba(255, 255, 255, 0.15);
}

.content {
  flex: 1;
  padding: 32px;
}
</style>
