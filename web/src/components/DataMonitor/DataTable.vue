<template>
  <div class="card">
    <div class="flex justify-between align-center mb-20">
      <h2>设备实时数据</h2>
      <div class="flex gap-10">
        <input
          v-model="searchText"
          type="text"
          class="form-control"
          placeholder="搜索设备ID..."
          style="width: 200px;"
        />
        <button class="btn btn-primary" @click="$emit('refresh')">刷新</button>
      </div>
    </div>

    <table class="table">
      <thead>
        <tr>
          <th>设备ID</th>
          <th>温度 (°C)</th>
          <th>湿度 (%)</th>
          <th>电压 (V)</th>
          <th>状态</th>
          <th>时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in filteredData" :key="item.deviceId">
          <td class="fw-bold">{{ item.deviceId }}</td>
          <td>
            <span :class="getTempClass(item.temperature)">
              {{ item.temperature?.toFixed(1) ?? '--' }}
            </span>
          </td>
          <td>{{ item.humidity?.toFixed(1) ?? '--' }}</td>
          <td>{{ item.voltage?.toFixed(2) ?? '--' }}</td>
          <td>
            <span :class="getStatusClass(item.status)" class="badge">
              {{ item.status || '--' }}
            </span>
          </td>
          <td class="text-sm text-muted">{{ formatTime(item.timestamp) }}</td>
          <td>
            <button class="btn btn-sm btn-primary" @click="$emit('detail', item.deviceId)">
              历史数据
            </button>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-if="data.length === 0" class="text-center text-muted py-10">
      暂无实时数据
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  data: {
    type: Array,
    default: () => []
  }
})

defineEmits(['refresh', 'detail'])

const searchText = ref('')

const filteredData = computed(() => {
  if (!searchText.value) return props.data
  const keyword = searchText.value.toLowerCase()
  return props.data.filter(d => d.deviceId?.toLowerCase().includes(keyword))
})

const getTempClass = (temp) => {
  if (temp == null) return ''
  if (temp > 35) return 'text-danger'
  if (temp > 28) return 'text-warning'
  return 'text-success'
}

const getStatusClass = (status) => {
  return status === 'online' ? 'badge-success' : 'badge-danger'
}

const formatTime = (ts) => {
  if (!ts) return '--'
  const d = new Date(ts)
  return d.toLocaleTimeString('zh-CN')
}
</script>
