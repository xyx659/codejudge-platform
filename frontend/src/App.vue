<!-- 根组件：仅承载路由出口，全局基础样式写在这里 -->
<template>
  <router-view />
</template>

<style>
/* 全局重置 */
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  background: #f5f7fa;
  color: #1f2937;
}
</style>
