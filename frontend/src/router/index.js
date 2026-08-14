// 路由配置：按角色（学生/教师/管理端）分为三组，各自挂载独立布局与页面
import { createRouter, createWebHistory } from 'vue-router'
import StudentLayout from '../layouts/student/StudentLayout.vue'
import TeacherLayout from '../layouts/teacher/TeacherLayout.vue'
import AdminLayout from '../layouts/admin/AdminLayout.vue'
import StudentHome from '../views/student/StudentHome.vue'
import StudentScores from '../views/student/StudentScores.vue'
import TeacherHome from '../views/teacher/TeacherHome.vue'
import TeacherQuestions from '../views/teacher/TeacherQuestions.vue'
import AdminHome from '../views/admin/AdminHome.vue'
import AdminUsers from '../views/admin/AdminUsers.vue'

const routes = [
  // 默认跳转到学生端首页
  { path: '/', redirect: '/student/home' },
  // 学生端
  {
    path: '/student',
    component: StudentLayout,
    children: [
      { path: '', redirect: '/student/home' },
      { path: 'home', component: StudentHome },
      { path: 'scores', component: StudentScores }
    ]
  },
  // 教师端
  {
    path: '/teacher',
    component: TeacherLayout,
    children: [
      { path: '', redirect: '/teacher/home' },
      { path: 'home', component: TeacherHome },
      { path: 'questions', component: TeacherQuestions }
    ]
  },
  // 管理端
  {
    path: '/admin',
    component: AdminLayout,
    children: [
      { path: '', redirect: '/admin/home' },
      { path: 'home', component: AdminHome },
      { path: 'users', component: AdminUsers }
    ]
  }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
