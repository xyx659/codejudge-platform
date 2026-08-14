<!-- 教师端布局：左侧导航栏 + 右侧内容区 -->
<template>
  <div class="layout">
    <aside class="sidebar">
      <h2>教师端</h2>
      <nav>
        <router-link to="/teacher/home">工作台</router-link>
        <router-link to="/teacher/questions">题库管理</router-link>
      </nav>
    </aside>
    <main class="content">
      <router-view />
    </main>
  </div>
</template>

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

.content {
  flex: 1;
  padding: 32px;
}
</style>
