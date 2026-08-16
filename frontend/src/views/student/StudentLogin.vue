<!-- 学生端登录页 -->
<template>
  <LoginPanel
    role="STUDENT"
    title="学生端登录"
    subtitle="在线编程考核 · 提交作业 · 查看成绩"
    home-path="/student/home"
    theme="#2563eb"
    theme-dark="#1e3a5f"
    hint="演示账号：test / 123456"
  />
</template>

<script setup>
import LoginPanel from '../../components/LoginPanel.vue'
</script>
