<script setup>
import { defineProps } from 'vue'
import { Inbox, Search, AlertCircle, FileText } from 'lucide-vue-next'

const props = defineProps({
  icon: {
    type: String,
    default: 'inbox', // inbox, search, error, file
  },
  title: {
    type: String,
    default: '暂无数据'
  },
  description: {
    type: String,
    default: '没有找到符合条件的内容'
  },
  actionText: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['action'])
</script>

<template>
  <div class="empty-state">
    <div class="empty-icon">
      <Search v-if="icon === 'search'" :size="48" stroke-width="1.5" />
      <AlertCircle v-else-if="icon === 'error'" :size="48" stroke-width="1.5" class="error-color" />
      <FileText v-else-if="icon === 'file'" :size="48" stroke-width="1.5" />
      <Inbox v-else :size="48" stroke-width="1.5" />
    </div>
    
    <h3 class="empty-title">{{ title }}</h3>
    <p class="empty-desc">{{ description }}</p>
    
    <button v-if="actionText" class="empty-action" @click="emit('action')">
      {{ actionText }}
    </button>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
  background: rgba(255, 255, 255, 0.02);
  border: 1px dashed rgba(255, 255, 255, 0.1);
  border-radius: var(--radius-lg);
  min-height: 250px;
  height: 100%;
}

.empty-icon {
  color: var(--c-text-muted);
  margin-bottom: 16px;
  opacity: 0.8;
  padding: 16px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 50%;
}

.error-color {
  color: #EF4444;
}

.empty-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--c-text-primary);
  margin: 0 0 8px 0;
}

.empty-desc {
  font-size: 14px;
  color: var(--c-text-muted);
  max-width: 300px;
  margin: 0 0 24px 0;
  line-height: 1.5;
}

.empty-action {
  background: rgba(30, 117, 255, 0.1);
  color: var(--c-accent-primary);
  border: 1px solid rgba(30, 117, 255, 0.3);
  padding: 8px 20px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast);
}

.empty-action:hover {
  background: rgba(30, 117, 255, 0.2);
  transform: translateY(-1px);
}
</style>
