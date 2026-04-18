<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import { fetchAdminDashboard } from '../api'
import PremiumCard from '../components/common/PremiumCard.vue'
import { Users, Briefcase, FileText, Activity } from 'lucide-vue-next'

const authStore = useAuthStore()
const { error } = useToast()
const loading = ref(true)
const dashboard = ref(null)

onMounted(async () => {
  if (authStore.user?.roleType !== 1) return
  try {
    dashboard.value = await fetchAdminDashboard(authStore.token)
  } catch (e) {
    error('加载管理数据失败: ' + e.message)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="admin-view page-shell">
    <div class="page-header">
      <div class="header-content">
        <h1>管理仪表盘</h1>
        <p>系统运行状态与核心指标监控</p>
      </div>
    </div>

    <div v-if="loading" class="loading-state">
      <div class="loader-ring"></div>
      <p>正在加载系统数据...</p>
    </div>
    <template v-else-if="dashboard">
      <div class="kpi-grid">
        <PremiumCard class="kpi-card" glowColor="primary">
          <div class="kpi-icon-box" style="background: rgba(59, 130, 246, 0.1); color: #3b82f6;">
            <Users :size="24" />
          </div>
          <div class="kpi-info">
            <h3>总用户数</h3>
            <div class="kpi-value">{{ dashboard.totalUsers }}</div>
            <div class="kpi-sub">今日新增 {{ dashboard.newUsersToday }}</div>
          </div>
        </PremiumCard>

        <PremiumCard class="kpi-card" glowColor="teal">
          <div class="kpi-icon-box" style="background: rgba(20, 184, 166, 0.1); color: #14b8a6;">
            <Briefcase :size="24" />
          </div>
          <div class="kpi-info">
            <h3>岗位数据总量</h3>
            <div class="kpi-value">{{ dashboard.totalJobs }}</div>
            <div class="kpi-sub">近7天新增 {{ dashboard.newJobs7d || 0 }}</div>
          </div>
        </PremiumCard>

        <PremiumCard class="kpi-card" glowColor="purple">
          <div class="kpi-icon-box" style="background: rgba(168, 85, 247, 0.1); color: #a855f7;">
            <FileText :size="24" />
          </div>
          <div class="kpi-info">
            <h3>已生成报告</h3>
            <div class="kpi-value">{{ dashboard.totalReports }}</div>
            <div class="kpi-sub">系统历史累计</div>
          </div>
        </PremiumCard>

        <PremiumCard class="kpi-card" glowColor="rose">
          <div class="kpi-icon-box" style="background: rgba(244, 63, 94, 0.1); color: #f43f5e;">
            <Activity :size="24" />
          </div>
          <div class="kpi-info">
            <h3>今日活跃</h3>
            <div class="kpi-value">{{ dashboard.activeToday || 0 }}</div>
            <div class="kpi-sub">
              学生 {{ dashboard.studentCount }} | 教师 {{ dashboard.teacherCount }} | 管理员 {{ dashboard.adminCount }}
            </div>
          </div>
        </PremiumCard>
      </div>

      <div class="dashboard-grid">
        <PremiumCard title="近期系统日志" class="full-width">
          <div class="log-list">
            <div v-for="log in dashboard.recentLogs" :key="log.id" class="log-item">
              <span class="log-time">{{ new Date(log.createdAt).toLocaleString() }}</span>
              <span class="log-user">{{ log.username || 'System' }}</span>
              <span class="log-action">{{ log.operation }}</span>
              <span class="log-ip">{{ log.ip }}</span>
            </div>
            <div v-if="!dashboard.recentLogs?.length" class="empty-state">
              暂无操作日志
            </div>
          </div>
        </PremiumCard>
      </div>
    </template>
  </div>
</template>

<style scoped>
.admin-view {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 20px;
}

.kpi-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
}

.kpi-icon-box {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.kpi-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.kpi-info h3 {
  font-size: 14px;
  color: var(--c-text-secondary);
  font-weight: 500;
  margin: 0;
}

.kpi-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--c-text-primary);
  line-height: 1;
}

.kpi-sub {
  font-size: 12px;
  color: var(--c-text-muted);
}

.dashboard-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 24px;
}

.log-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.log-item {
  display: grid;
  grid-template-columns: 180px 120px flex 120px;
  gap: 16px;
  padding: 12px;
  background: var(--c-bg-surface-hover);
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--c-text-secondary);
  align-items: center;
}

.log-time { color: var(--c-text-muted); }
.log-user { font-weight: 600; color: var(--c-text-primary); }
.log-action { flex: 1; }

@media (max-width: 768px) {
  .log-item {
    grid-template-columns: 1fr;
    gap: 4px;
  }
}
</style>
