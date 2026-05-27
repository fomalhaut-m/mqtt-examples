<template>
  <div class="card">
    <div class="flex justify-between align-center mb-20">
      <h2>设备列表</h2>
      <div class="flex gap-10">
        <select v-model="filterStatus" class="form-control" style="width: 120px;">
          <option value="">全部</option>
          <option value="online">在线</option>
          <option value="offline">离线</option>
        </select>
        <button class="btn btn-primary" @click="$emit('add')">添加设备</button>
      </div>
    </div>

    <table class="table">
      <thead>
        <tr>
          <th>设备ID</th>
          <th>设备名称</th>
          <th>设备类型</th>
          <th>上报Topic</th>
          <th>上报间隔</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="device in filteredDevices" :key="device.id">
          <td>{{ device.deviceId }}</td>
          <td>{{ device.deviceName }}</td>
          <td>{{ device.deviceType }}</td>
          <td class="text-sm text-muted">{{ device.reportTopic }}</td>
          <td>{{ device.interval }}s</td>
          <td>
            <span :class="getStatusClass(device.status)" class="badge">
              {{ device.status === 'online' ? '在线' : '离线' }}
            </span>
          </td>
          <td>
            <div class="flex gap-5">
              <button 
                class="btn btn-sm btn-success" 
                @click="$emit('start', device.id)"
                :disabled="device.status === 'online'"
              >
                启动
              </button>
              <button 
                class="btn btn-sm btn-warning" 
                @click="$emit('stop', device.id)"
                :disabled="device.status === 'offline'"
              >
                停止
              </button>
              <button 
                class="btn btn-sm btn-primary" 
                @click="$emit('report', device.id)"
                :disabled="device.status !== 'online'"
              >
                上报一次
              </button>
              <button class="btn btn-sm btn-primary" @click="$emit('edit', device)">编辑</button>
              <button class="btn btn-sm btn-danger" @click="$emit('delete', device.id)">删除</button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-if="devices.length === 0" class="text-center text-muted py-10">
      暂无设备，请添加设备
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  devices: {
    type: Array,
    default: () => []
  }
})

defineEmits(['add', 'edit', 'delete', 'start', 'stop', 'report'])

const filterStatus = ref('')

const filteredDevices = computed(() => {
  if (!filterStatus.value) return props.devices
  return props.devices.filter(d => d.status === filterStatus.value)
})

const getStatusClass = (status) => {
  return status === 'online' ? 'badge-success' : 'badge-danger'
}
</script>

<style scoped>
.btn-sm {
  padding: 4px 10px;
  font-size: 12px;
}
</style>