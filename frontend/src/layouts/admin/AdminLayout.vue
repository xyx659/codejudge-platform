<!-- 管理端布局：左侧导航栏 + 右侧内容区 -->
<template>
  <div class="layout">
    <aside class="sidebar">
      <h2>管理端</h2>
      <nav>
        <router-link to="/admin/home">工作台</router-link>
        <router-link to="/admin/users">用户管理</router-link>
        <router-link to="/admin/questions">题库管理</router-link>
        <router-link to="/admin/system-config">系统配置</router-link>
        <router-link to="/admin/audit-logs">审计日志</router-link>
        <router-link to="/admin/database-monitor">数据库监控</router-link>
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
  router.replace('/admin/login')
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
  background: #3b2f63;
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
  color: #ede9fe;
  text-decoration: none;
  padding: 10px 12px;
  border-radius: 6px;
}

.sidebar a.router-link-active {
  background: #7c3aed;
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
