<script setup>
import { computed, onMounted, ref } from 'vue'
import DefaultAvatarIcon from '../components/common/DefaultAvatarIcon.vue'
import GlowButton from '../components/common/GlowButton.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import {
  fetchAdminUsers,
  invalidateApiCache,
  updateAdminUser,
  updateAdminUserRole,
  updateAdminUserStatus
} from '../api'
import { mapErrorMessage } from '../utils/errorMap'
import { getRoleLabel } from '../utils/role'
import { ChevronLeft, ChevronRight, PencilLine, RefreshCw, Search, Users, X } from 'lucide-vue-next'

const authStore = useAuthStore()
const { success, error } = useToast()

const DEFAULT_PAGE_SIZE = 10

function buildDefaultFilters() {
  return {
    keyword: '',
    roleType: '',
    status: '',
    page: 1,
    pageSize: DEFAULT_PAGE_SIZE
  }
}

const loading = ref(true)
const actionLoadingKey = ref('')
const editSaving = ref(false)
const users = ref([])
const totalCount = ref(0)

const filters = ref(buildDefaultFilters())
const searchInput = ref('')
const editingUser = ref(null)
const editForm = ref({
  nickname: '',
  email: '',
  phone: '',
  avatarUrl: ''
})

const roleOptions = [
  { value: 0, label: '学生' },
  { value: 1, label: '管理员' },
  { value: 2, label: '教师' }
]

// The contract only guarantees status values 0/1/2. The docs do not name 2,
// so the UI keeps that label neutral instead of guessing business semantics.
const statusOptions = [
  { value: 1, label: '正常', tone: 'ok' },
  { value: 0, label: '禁用', tone: 'off' },
  { value: 2, label: '状态 2', tone: 'warn' }
]

const totalPages = computed(() => Math.max(1, Math.ceil(totalCount.value / filters.value.pageSize)))
const appliedFilterCount = computed(() => {
  let count = 0
  if (filters.value.keyword) count += 1
  if (filters.value.roleType !== '') count += 1
  if (filters.value.status !== '') count += 1
  return count
})

function getStatusMeta(status) {
  const matched = statusOptions.find((option) => Number(option.value) === Number(status))
  if (matched) return matched
  if (status === '' || status === null || status === undefined) {
    return { value: '', label: '--', tone: 'unknown' }
  }
  return { value: status, label: `状态 ${status}`, tone: 'unknown' }
}

function isUserBusy(userId) {
  return actionLoadingKey.value.startsWith(`${userId}:`)
}

function formatDate(value) {
  if (!value) return '--'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleDateString('zh-CN')
}

function formatDateTime(value) {
  if (!value) return '暂无记录'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false })
}

async function loadUsers({ silent = false } = {}) {
  if (!authStore.token) return
  if (!silent) loading.value = true
  try {
    const response = await fetchAdminUsers(authStore.token, filters.value)
    users.value = Array.isArray(response?.data) ? response.data : []
    totalCount.value = Number(response?.total || 0)
  } catch (e) {
    error(`加载用户列表失败：${mapErrorMessage(e)}`)
  } finally {
    if (!silent) loading.value = false
  }
}

function applySearch() {
  filters.value.keyword = searchInput.value.trim()
  filters.value.page = 1
  void loadUsers()
}

function applySelectFilters() {
  filters.value.page = 1
  void loadUsers()
}

function resetFilters() {
  searchInput.value = ''
  filters.value = buildDefaultFilters()
  void loadUsers()
}

function prevPage() {
  if (filters.value.page <= 1) return
  filters.value.page -= 1
  void loadUsers()
}

function nextPage() {
  if (filters.value.page >= totalPages.value) return
  filters.value.page += 1
  void loadUsers()
}

function openEditDialog(user) {
  editingUser.value = {
    ...user,
    roleType: Number(user.roleType ?? 0),
    status: Number(user.status ?? 1)
  }
  editForm.value = {
    nickname: user.nickname || '',
    email: user.email || '',
    phone: user.phone || '',
    avatarUrl: user.avatarUrl || ''
  }
}

function closeEditDialog(force = false) {
  if (editSaving.value && !force) return
  editingUser.value = null
  editForm.value = {
    nickname: '',
    email: '',
    phone: '',
    avatarUrl: ''
  }
}

async function handleRoleChange(user, newRole) {
  if (Number(user.roleType) === Number(newRole)) return
  actionLoadingKey.value = `${user.id}:role`
  try {
    await updateAdminUserRole(authStore.token, user.id, Number(newRole))
    success(`已将 ${user.nickname || user.username} 角色改为「${getRoleLabel(Number(newRole))}」`)
    invalidateApiCache('/admin/')
    if (editingUser.value?.id === user.id) {
      editingUser.value.roleType = Number(newRole)
    }
    await loadUsers({ silent: true })
  } catch (e) {
    error(`更新角色失败：${mapErrorMessage(e)}`)
  } finally {
    actionLoadingKey.value = ''
  }
}

async function handleStatusChange(user, newStatus) {
  if (Number(user.status) === Number(newStatus)) return
  actionLoadingKey.value = `${user.id}:status`
  try {
    await updateAdminUserStatus(authStore.token, user.id, Number(newStatus))
    success(`已将 ${user.nickname || user.username} 状态改为「${getStatusMeta(Number(newStatus)).label}」`)
    invalidateApiCache('/admin/')
    if (editingUser.value?.id === user.id) {
      editingUser.value.status = Number(newStatus)
    }
    await loadUsers({ silent: true })
  } catch (e) {
    error(`更新账号状态失败：${mapErrorMessage(e)}`)
  } finally {
    actionLoadingKey.value = ''
  }
}

async function handleSaveUserProfile() {
  if (!editingUser.value || editSaving.value) return
  const currentUser = editingUser.value
  editSaving.value = true
  try {
    await updateAdminUser(authStore.token, currentUser.id, {
      nickname: editForm.value.nickname.trim(),
      email: editForm.value.email.trim(),
      phone: editForm.value.phone.trim(),
      avatarUrl: editForm.value.avatarUrl.trim(),
      roleType: Number(currentUser.roleType),
      status: Number(currentUser.status)
    })
    success(`已更新 ${currentUser.nickname || currentUser.username} 的资料`)
    invalidateApiCache('/admin/')
    closeEditDialog(true)
    await loadUsers({ silent: true })
  } catch (e) {
    error(`更新用户资料失败：${mapErrorMessage(e)}`)
  } finally {
    editSaving.value = false
  }
}

onMounted(() => {
  void loadUsers()
})
</script>

<template>
  <div class="um-page page-animate">
    <header class="workspace-page-head um-head">
      <div class="um-head-copy">
        <h1 class="workspace-page-title">用户管理</h1>
        <p class="um-subtitle">保留真实契约支持的用户查询、角色调整、状态调整与资料编辑。</p>
      </div>
      <span class="um-badge">
        <Users :size="14" />
        共 {{ totalCount }} 位用户
      </span>
    </header>

    <article class="panel">
      <header class="panel-head panel-head-row">
        <h2 class="panel-title">用户列表</h2>
        <span class="panel-badge">
          {{ appliedFilterCount ? `已应用 ${appliedFilterCount} 个筛选` : '未应用筛选' }}
        </span>
      </header>

      <div class="panel-body">
        <div class="toolbar">
          <div class="search-wrap">
            <Search :size="15" class="search-icon" />
            <input
              v-model="searchInput"
              class="panel-input search-input"
              placeholder="搜索用户名 / 昵称 / 邮箱"
              @keydown.enter="applySearch"
            />
          </div>

          <select v-model="filters.roleType" class="panel-input" @change="applySelectFilters">
            <option value="">全部角色</option>
            <option :value="0">学生</option>
            <option :value="1">管理员</option>
            <option :value="2">教师</option>
          </select>

          <select v-model="filters.status" class="panel-input" @change="applySelectFilters">
            <option value="">全部状态</option>
            <option v-for="option in statusOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>

          <GlowButton variant="primary" @click="applySearch">
            <Search :size="14" />
            搜索
          </GlowButton>

          <button class="btn-ghost" type="button" @click="resetFilters">
            <RefreshCw :size="13" />
            重置
          </button>
        </div>

        <div class="list-meta">
          <span>当前第 {{ filters.page }} / {{ totalPages }} 页</span>
          <span>本页 {{ users.length }} 条</span>
          <span>每页 {{ filters.pageSize }} 条</span>
        </div>

        <div v-if="loading" class="um-loading-skel">
          <SkeletonCard type="list" :lines="6" />
        </div>

        <div v-else class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>用户信息</th>
                <th>当前角色</th>
                <th>账号状态</th>
                <th>注册时间</th>
                <th>最近登录</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="user in users"
                :key="user.id"
                :class="{ 'row-loading': isUserBusy(user.id) }"
              >
                <td>
                  <div class="user-cell">
                    <img
                      v-if="user.avatarUrl"
                      :src="user.avatarUrl"
                      class="user-avatar"
                      alt="avatar"
                    />
                    <span v-else class="user-avatar user-avatar-fallback" aria-hidden="true">
                      <DefaultAvatarIcon />
                    </span>
                    <div class="user-cell-text">
                      <strong>{{ user.nickname || user.username || `用户 #${user.id}` }}</strong>
                      <span class="muted">@{{ user.username || '--' }}</span>
                      <span class="muted">{{ user.email || '未设置邮箱' }}</span>
                    </div>
                  </div>
                </td>

                <td>
                  <span :class="['role-pill', `role-${Number(user.roleType)}`]">
                    {{ getRoleLabel(Number(user.roleType)) }}
                  </span>
                </td>

                <td>
                  <span :class="['status-pill', `status-${getStatusMeta(user.status).tone}`]">
                    {{ getStatusMeta(user.status).label }}
                  </span>
                </td>

                <td class="muted">{{ formatDate(user.createdAt) }}</td>
                <td class="muted">{{ formatDateTime(user.lastLoginAt) }}</td>

                <td class="operations-cell">
                  <div class="action-stack">
                    <label class="action-select">
                      <span>角色</span>
                      <select
                        class="panel-input slim-input"
                        :value="String(Number(user.roleType ?? 0))"
                        :disabled="isUserBusy(user.id)"
                        @change="handleRoleChange(user, Number($event.target.value))"
                      >
                        <option v-for="option in roleOptions" :key="option.value" :value="option.value">
                          {{ option.label }}
                        </option>
                      </select>
                    </label>

                    <label class="action-select">
                      <span>状态</span>
                      <select
                        class="panel-input slim-input"
                        :value="String(Number(user.status ?? 1))"
                        :disabled="isUserBusy(user.id)"
                        @change="handleStatusChange(user, Number($event.target.value))"
                      >
                        <option v-for="option in statusOptions" :key="option.value" :value="option.value">
                          {{ option.label }}
                        </option>
                      </select>
                    </label>

                    <button
                      type="button"
                      class="action-btn secondary"
                      :disabled="isUserBusy(user.id)"
                      @click="openEditDialog(user)"
                    >
                      <PencilLine :size="13" />
                      编辑资料
                    </button>
                  </div>
                </td>
              </tr>

              <tr v-if="!users.length">
                <td colspan="6" class="empty-row">
                  <Users :size="22" />
                  <p>未找到符合条件的用户</p>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="!loading && totalCount > 0" class="pagination">
          <span class="page-info">共 {{ totalCount }} 条记录</span>
          <div class="page-btns">
            <button class="page-btn" :disabled="filters.page === 1" @click="prevPage">
              <ChevronLeft :size="15" />
            </button>
            <span class="page-num">{{ filters.page }}</span>
            <button class="page-btn" :disabled="filters.page >= totalPages" @click="nextPage">
              <ChevronRight :size="15" />
            </button>
          </div>
        </div>
      </div>
    </article>
  </div>

  <Teleport to="body">
    <transition name="modal-fade">
      <div v-if="editingUser" class="user-edit-modal-backdrop" @click.self="closeEditDialog()">
        <div class="user-edit-modal">
          <div class="user-edit-modal-head">
            <div class="user-edit-modal-title">
              <h2>编辑用户资料</h2>
              <p>
                @{{ editingUser.username || '--' }}
                · {{ getRoleLabel(Number(editingUser.roleType)) }}
                · {{ getStatusMeta(editingUser.status).label }}
              </p>
            </div>

            <button
              type="button"
              class="user-edit-close"
              :disabled="editSaving"
              @click="closeEditDialog()"
            >
              <X :size="16" />
            </button>
          </div>

          <div class="user-edit-modal-body">
            <div class="user-edit-preview">
              <img
                v-if="editForm.avatarUrl"
                :src="editForm.avatarUrl"
                class="user-edit-avatar"
                alt="avatar preview"
              />
              <span v-else class="user-edit-avatar user-edit-avatar-fallback" aria-hidden="true">
                <DefaultAvatarIcon />
              </span>
              <div class="user-edit-preview-copy">
                <strong>{{ editForm.nickname || editingUser.nickname || editingUser.username }}</strong>
                <span>{{ editForm.email || '未设置邮箱' }}</span>
              </div>
            </div>

            <div class="user-edit-grid">
              <label class="user-edit-field">
                <span>昵称</span>
                <input v-model="editForm.nickname" class="panel-input" placeholder="请输入昵称" />
              </label>

              <label class="user-edit-field">
                <span>邮箱</span>
                <input v-model="editForm.email" class="panel-input" placeholder="请输入邮箱" />
              </label>

              <label class="user-edit-field">
                <span>手机号</span>
                <input v-model="editForm.phone" class="panel-input" placeholder="请输入手机号" />
              </label>

              <label class="user-edit-field user-edit-field-full">
                <span>头像地址</span>
                <input v-model="editForm.avatarUrl" class="panel-input" placeholder="请输入头像 URL" />
              </label>
            </div>
          </div>

          <div class="user-edit-modal-actions">
            <button
              type="button"
              class="action-btn secondary"
              :disabled="editSaving"
              @click="closeEditDialog()"
            >
              取消
            </button>
            <GlowButton variant="primary" :loading="editSaving" @click="handleSaveUserProfile">
              保存资料
            </GlowButton>
          </div>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.um-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.um-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.um-head-copy {
  display: grid;
  gap: 8px;
}

.um-subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--c-text-secondary);
}

.um-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-size: 12px;
  font-weight: 700;
}

.panel {
  display: flex;
  flex-direction: column;
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  box-shadow: var(--shadow-card-quiet);
  overflow: hidden;
}

.panel-head {
  padding: 16px 22px 12px;
  border-bottom: 1px solid var(--c-border-glass);
}

.panel-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--c-text-primary);
}

.panel-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-size: 11px;
  font-weight: 600;
}

.panel-body {
  padding: 18px 22px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 140px 140px auto auto;
  gap: 10px;
  align-items: center;
}

.search-wrap {
  position: relative;
  min-width: 180px;
}

.search-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--c-text-muted);
  pointer-events: none;
  z-index: 1;
}

.panel-input {
  width: 100%;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-primary);
  font-family: var(--font-sans);
  font-size: 13.5px;
  transition: border-color var(--duration-fast), box-shadow var(--duration-fast);
}

.search-wrap .search-input {
  padding-left: 38px;
}

.panel-input::placeholder {
  color: var(--c-text-faint);
}

.panel-input:focus {
  outline: none;
  border-color: var(--c-accent-primary);
  box-shadow: 0 0 0 3px var(--c-accent-primary-glow);
}

.btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 8px 14px;
  border-radius: 9px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color var(--duration-fast), color var(--duration-fast);
}

.btn-ghost:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}

.list-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: var(--c-text-muted);
}

.table-wrap {
  overflow-x: auto;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-family: var(--font-sans);
}

.data-table th {
  padding: 10px 14px;
  text-align: left;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--c-text-muted);
  background: var(--c-bg-surface-hover);
  border-bottom: 1px solid var(--c-border-glass);
}

.data-table td {
  padding: 12px 14px;
  border-bottom: 1px solid var(--c-border-glass);
  color: var(--c-text-primary);
  font-size: 13px;
  vertical-align: middle;
}

.data-table tbody tr:last-child td {
  border-bottom: none;
}

.data-table tbody tr.row-loading td {
  opacity: 0.6;
  pointer-events: none;
}

.user-cell {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  flex-shrink: 0;
}

.user-avatar-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  background:
    radial-gradient(circle at 28% 24%, rgba(255, 255, 255, 0.3), transparent 34%),
    linear-gradient(135deg, rgba(0, 87, 194, 0.18), rgba(0, 110, 242, 0.34));
  color: var(--c-accent-primary);
}

:global([data-theme='dark']) .user-avatar-fallback {
  background:
    radial-gradient(circle at 28% 24%, rgba(255, 255, 255, 0.12), transparent 34%),
    linear-gradient(135deg, rgba(175, 198, 255, 0.22), rgba(82, 106, 184, 0.46));
  color: #eef3ff;
}

.user-cell-text {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.user-cell-text strong {
  color: var(--c-text-primary);
  font-size: 13.5px;
  font-weight: 600;
}

.muted {
  color: var(--c-text-muted);
  font-size: 11.5px;
}

.role-pill,
.status-pill {
  display: inline-flex;
  align-items: center;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.role-0 {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}

.role-1 {
  background: rgba(140, 90, 180, 0.14);
  color: #8c5ab4;
}

.role-2 {
  background: rgba(30, 138, 91, 0.12);
  color: #1e8a5b;
}

.status-ok {
  background: rgba(30, 138, 91, 0.12);
  color: #1e8a5b;
}

.status-off {
  background: rgba(178, 59, 46, 0.12);
  color: #b23b2e;
}

.status-warn {
  background: rgba(164, 94, 5, 0.14);
  color: #8d5a06;
}

.status-unknown {
  background: rgba(92, 104, 123, 0.12);
  color: var(--c-text-secondary);
}

.operations-cell {
  min-width: 320px;
}

.action-stack {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 8px;
}

.action-select {
  display: grid;
  gap: 4px;
  min-width: 110px;
}

.action-select span {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.slim-input {
  min-width: 0;
  padding: 8px 10px;
  font-size: 12.5px;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  transition: background-color var(--duration-fast), border-color var(--duration-fast);
}

.action-btn.secondary {
  background: rgba(0, 87, 194, 0.08);
  color: var(--c-accent-primary);
  border-color: rgba(0, 87, 194, 0.18);
}

.action-btn.secondary:hover:not(:disabled) {
  background: rgba(0, 87, 194, 0.14);
}

.action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.empty-row {
  text-align: center;
  padding: 36px 0;
  color: var(--c-text-muted);
}

.empty-row p {
  margin: 8px 0 0;
}

.pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
}

.page-info {
  font-size: 12.5px;
  color: var(--c-text-muted);
}

.page-btns {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-btn {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  cursor: pointer;
  transition: background-color var(--duration-fast), border-color var(--duration-fast), color var(--duration-fast);
}

.page-btn:hover:not(:disabled) {
  background: var(--c-accent-primary-glow);
  border-color: var(--c-border-glass-hover);
  color: var(--c-accent-primary);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-num {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-accent-primary);
  min-width: 22px;
  text-align: center;
}

.um-loading-skel {
  padding: 8px 0 4px;
}

.user-edit-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.44);
  backdrop-filter: blur(6px);
}

.user-edit-modal {
  width: min(640px, 100%);
  border-radius: 18px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  box-shadow: var(--shadow-card-raised);
  overflow: hidden;
}

.user-edit-modal-head,
.user-edit-modal-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 22px;
}

.user-edit-modal-head {
  border-bottom: 1px solid var(--c-border-glass);
}

.user-edit-modal-title h2 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 700;
  color: var(--c-text-primary);
}

.user-edit-modal-title p {
  margin: 6px 0 0;
  font-size: 12.5px;
  color: var(--c-text-secondary);
}

.user-edit-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 999px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  cursor: pointer;
}

.user-edit-modal-body {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 22px;
}

.user-edit-preview {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
}

.user-edit-avatar {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  object-fit: cover;
  flex-shrink: 0;
}

.user-edit-avatar-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10px;
  background: var(--c-bg-base-elevated);
  color: var(--c-text-muted);
}

.user-edit-preview-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.user-edit-preview-copy strong {
  color: var(--c-text-primary);
  font-size: 14px;
}

.user-edit-preview-copy span {
  color: var(--c-text-secondary);
  font-size: 12.5px;
}

.user-edit-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.user-edit-field {
  display: grid;
  gap: 8px;
}

.user-edit-field span {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.user-edit-field-full {
  grid-column: 1 / -1;
}

.user-edit-modal-actions {
  border-top: 1px solid var(--c-border-glass);
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 180ms ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}

@media (max-width: 1080px) {
  .toolbar {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 768px) {
  .toolbar {
    grid-template-columns: 1fr;
  }

  .pagination {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .user-edit-modal-backdrop {
    padding: 14px;
  }

  .user-edit-grid {
    grid-template-columns: 1fr;
  }
}
</style>
