<template>
  <div class="card">
    <div class="flex justify-between align-center mb-20">
      <h2>
        报警管理
        <span :class="['badge', 'ml-10', alertEnabled ? 'badge-success' : 'badge-danger']">
          {{ alertEnabled ? '已开启' : '已关闭' }}
        </span>
      </h2>
      <div class="flex gap-10">
        <button class="btn btn-warning" @click="handleManualAlert">手动触发报警</button>
        <button class="btn" :class="alertEnabled ? 'btn-danger' : 'btn-success'" @click="handleToggle">
          {{ alertEnabled ? '关闭报警' : '开启报警' }}
        </button>
      </div>
    </div>

    <div v-if="alerts.length === 0" class="text-center text-muted py-10">
      暂无报警记录
    </div>

    <div v-else class="alert-list">
      <div
        v-for="(alert, index) in alerts"
        :key="index"
        class="alert-item"
      >
        <div class="alert-icon">{{ alert.type === 'manual' ? '🔔' : '⚠️' }}</div>
        <div class="alert-content">
          <div class="alert-message">{{ alert.message }}</div>
          <div class="alert-time">{{ formatTime(alert.timestamp) }}</div>
        </div>
        <span class="badge badge-warning">{{ alert.type }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { alertApi } from '../../api'

const alerts = ref([])
const alertEnabled = ref(true)

const loadAlerts = async () => {
  try {
    const res = await alertApi.getAll()
    alerts.value = res.data || []
  } catch (e) {
    console.error('Failed to load alerts:', e)
  }
}

const loadStatus = async () => {
  try {
    const res = await alertApi.getStatus()
    if (res.data) {
      alertEnabled.value = res.data.enabled
    }
  } catch (e) {
    console.error('Failed to load alert status:', e)
  }
}

const handleManualAlert = async () => {
  try {
    await alertApi.manual('手动触发报警: 设备数据异常')
    await loadAlerts()
  } catch (e) {
    console.error('Failed to trigger alert:', e)
  }
}

const handleToggle = async () => {
  try {
    const res = await alertApi.toggle()
    if (res.data) {
      alertEnabled.value = res.data.enabled
    }
  } catch (e) {
    console.error('Failed to toggle alert:', e)
  }
}

const formatTime = (ts) => {
  if (!ts) return '--'
  return new Date(ts).toLocaleString('zh-CN')
}

onMounted(() => {
  loadAlerts()
  loadStatus()
  setInterval(loadAlerts, 10000)
})
</script>

<style scoped>
.alert-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.alert-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #fafafa;
  border-radius: 6px;
  border-left: 4px solid #e6a23c;
}

.alert-icon {
  font-size: 20px;
}

.alert-content {
  flex: 1;
}

.alert-message {
  font-size: 14px;
  color: #333;
}

.alert-time {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
</style>
