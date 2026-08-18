// 路由配置：三个端各自独立的登录页 + 布局页面，通过全局前置守卫做登录校验与角色隔离
import { createRouter, createWebHistory } from 'vue-router'
import StudentLayout from '../layouts/student/StudentLayout.vue'
import TeacherLayout from '../layouts/teacher/TeacherLayout.vue'
import AdminLayout from '../layouts/admin/AdminLayout.vue'
import StudentHome from '../views/student/StudentHome.vue'
import StudentScores from '../views/student/StudentScores.vue'
import StudentLogin from '../views/student/StudentLogin.vue'
import TeacherHome from '../views/teacher/TeacherHome.vue'
import TeacherQuestions from '../views/teacher/TeacherQuestions.vue'
// ===== 教师端（M3）新增页面 =====
import TeacherCategories from '../views/teacher/TeacherCategories.vue'
import TeacherExams from '../views/teacher/TeacherExams.vue'
import TeacherMonitor from '../views/teacher/TeacherMonitor.vue'
import TeacherAnalytics from '../views/teacher/TeacherAnalytics.vue'
// ===== 教师端（M3）新增页面结束 =====
import TeacherLogin from '../views/teacher/TeacherLogin.vue'
import AdminHome from '../views/admin/AdminHome.vue'
import AdminAuditLogs from '../views/admin/AdminAuditLogs.vue'
import AdminDatabaseMonitor from '../views/admin/AdminDatabaseMonitor.vue'
import AdminQuestions from '../views/admin/AdminQuestions.vue'
import AdminSystemConfig from '../views/admin/AdminSystemConfig.vue'
import AdminUsers from '../views/admin/AdminUsers.vue'
import AdminLogin from '../views/admin/AdminLogin.vue'
import { getToken, getUser } from '../utils/auth'

// 角色 → 对应端首页
const ROLE_HOME = {
  ADMIN: '/admin/home',
  TEACHER: '/teacher/home',
  STUDENT: '/student/home'
}

// 路径前缀 → 对应端登录页
const LOGIN_PATH = {
  student: '/student/login',
  teacher: '/teacher/login',
  admin: '/admin/login'
}

const routes = [
  { path: '/', redirect: '/student/login' },
  // 三个登录页
  { path: '/student/login', component: StudentLogin },
  { path: '/teacher/login', component: TeacherLogin },
  { path: '/admin/login', component: AdminLogin },
  // 学生端
  {
    path: '/student',
    component: StudentLayout,
    meta: { requiresAuth: true, roles: ['STUDENT'] },
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
    meta: { requiresAuth: true, roles: ['TEACHER'] },
    children: [
      { path: '', redirect: '/teacher/home' },
      { path: 'home', component: TeacherHome },
      { path: 'questions', component: TeacherQuestions },
      // ===== 教师端（M3）新增路由 =====
      { path: 'categories', component: TeacherCategories },
      { path: 'exams', component: TeacherExams },
      { path: 'monitor', component: TeacherMonitor },
      { path: 'analytics', component: TeacherAnalytics }
      // ===== 教师端（M3）新增路由结束 =====
    ]
  },
  // 管理端
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true, roles: ['ADMIN'] },
    children: [
      { path: '', redirect: '/admin/home' },
      { path: 'home', component: AdminHome },
      { path: 'users', component: AdminUsers },
      { path: 'questions', component: AdminQuestions },
      { path: 'system-config', component: AdminSystemConfig },
      { path: 'audit-logs', component: AdminAuditLogs },
      { path: 'database-monitor', component: AdminDatabaseMonitor }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫
router.beforeEach((to, from, next) => {
  const token = getToken()
  const user = getUser()

  // 已登录访问任意登录页 → 跳对应角色首页
  if (to.path.endsWith('/login')) {
    if (token && user) {
      next(ROLE_HOME[user.role] || '/student/home')
    } else {
      next()
    }
    return
  }

  // 受保护路由：未登录 → 对应端登录页；角色不符 → 回自己端
  if (to.meta.requiresAuth) {
    if (!token) {
      next(LOGIN_PATH[to.path.split('/')[1]] || '/student/login')
      return
    }
    if (to.meta.roles && user && !to.meta.roles.includes(user.role)) {
      next(ROLE_HOME[user.role] || '/student/home')
      return
    }
  }

  next()
})

export default router
