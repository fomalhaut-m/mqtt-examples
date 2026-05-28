<template>
  <div class="stats-grid">
    <div class="stat-card">
      <div class="stat-icon" style="background: #e6f7ff;">📊</div>
      <div class="stat-info">
        <div class="stat-value">{{ stats.totalCount || 0 }}</div>
        <div class="stat-label">数据接收总数</div>
      </div>
    </div>
    <div class="stat-card">
      <div class="stat-icon" style="background: #f6ffed;">🟢</div>
      <div class="stat-info">
        <div class="stat-value">{{ stats.onlineDevices || 0 }}</div>
        <div class="stat-label">活跃设备数</div>
      </div>
    </div>
    <div class="stat-card">
      <div class="stat-icon" style="background: #fff7e6;">⚡</div>
      <div class="stat-info">
        <div class="stat-value">{{ stats.dataRate || 0 }} <span class="stat-unit">条/秒</span></div>
        <div class="stat-label">数据速率</div>
      </div>
    </div>
    <div class="stat-card">
      <div class="stat-icon" style="background: #f0f5ff;">🔗</div>
      <div class="stat-info">
        <div class="stat-value">{{ stats.sseConnections || 0 }}</div>
        <div class="stat-label">SSE 连接数</div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  stats: {
    type: Object,
    default: () => ({})
  }
})
</script>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 20px;
}

.stat-card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: transform 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1a1a2e;
  line-height: 1.2;
}

.stat-unit {
  font-size: 13px;
  font-weight: 400;
  color: #999;
}

.stat-label {
  font-size: 13px;
  color: #888;
  margin-top: 4px;
}

@media (max-width: 900px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
