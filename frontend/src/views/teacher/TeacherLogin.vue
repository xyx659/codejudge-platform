<!-- 教师端登录页 -->
<template>
  <LoginPanel
    role="TEACHER"
    title="教师端登录"
    subtitle="题库管理 · 考试管理 · 学情分析"
    home-path="/teacher/home"
    theme="#059669"
    theme-dark="#153e31"
    account-label="工号"
    hint="演示工号：teacher / teacher123"
  />
</template>

<script setup>
import LoginPanel from '../../components/LoginPanel.vue'
</script>
