<!-- 管理端登录页 -->
<template>
  <LoginPanel
    role="ADMIN"
    title="管理端登录"
    subtitle="用户管理 · 系统配置 · 审计日志"
    home-path="/admin/home"
    theme="#7c3aed"
    theme-dark="#3b2f63"
    hint="演示账号：admin / admin123"
  />
</template>

<script setup>
import LoginPanel from '../../components/LoginPanel.vue'
</script>
