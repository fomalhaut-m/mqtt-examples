<template>
  <div class="device-control">
    <div class="card">
      <div class="flex justify-between align-center mb-20">
        <h2>模拟设备控制中心</h2>
        <div class="flex gap-10">
          <span class="text-sm text-muted">设备总数: {{ devices.length }}</span>
          <span class="text-sm status-online">在线: {{ onlineCount }}</span>
          <span class="text-sm status-offline">离线: {{ offlineCount }}</span>
        </div>
      </div>
    </div>

    <DeviceList 
      :devices="devices"
      @add="showAddModal"
      @edit="showEditModal"
      @delete="handleDelete"
      @start="handleStart"
      @stop="handleStop"
      @report="handleReport"
    />

    <DeviceForm 
      v-if="showForm"
      :device="editingDevice"
      @close="closeForm"
      @submit="handleFormSubmit"
    />
  </div>
</template>

<script setup>import { ref, computed, onMounted } from 'vue';
import DeviceList from './DeviceList.vue';
import DeviceForm from './DeviceForm.vue';
import { deviceApi } from '../../api';
const devices = ref([]);
const showForm = ref(false);
const editingDevice = ref(null);
const onlineCount = computed(() => devices.value.filter(d => d.status === 'online').length);
const offlineCount = computed(() => devices.value.filter(d => d.status === 'offline').length);
const loadDevices = async () => {
 try {
 const data = await deviceApi.getAll();
 devices.value = data;
 }
 catch (error) {
 console.error('Failed to load devices:', error);
 }
};
const showAddModal = () => {
 editingDevice.value = null;
 showForm.value = true;
};
const showEditModal = (device) => {
 editingDevice.value = device;
 showForm.value = true;
};
const closeForm = () => {
 showForm.value = false;
 editingDevice.value = null;
};
const handleFormSubmit = async (device) => {
 try {
 if (editingDevice.value) {
 await deviceApi.update(editingDevice.value.id, device);
 }
 else {
 await deviceApi.create(device);
 }
 closeForm();
 await loadDevices();
 }
 catch (error) {
 console.error('Failed to save device:', error);
 alert('保存失败');
 }
};
const handleDelete = async (id) => {
 if (!confirm('确定要删除该设备吗？'))
 return;
 try {
 await deviceApi.delete(id);
 await loadDevices();
 }
 catch (error) {
 console.error('Failed to delete device:', error);
 alert('删除失败');
 }
};
const handleStart = async (id) => {
 try {
 await deviceApi.start(id);
 await loadDevices();
 }
 catch (error) {
 console.error('Failed to start device:', error);
 alert('启动失败');
 }
};
const handleStop = async (id) => {
 try {
 await deviceApi.stop(id);
 await loadDevices();
 }
 catch (error) {
 console.error('Failed to stop device:', error);
 alert('停止失败');
 }
};
const handleReport = async (id) => {
 try {
 await deviceApi.report(id);
 }
 catch (error) {
 console.error('Failed to trigger report:', error);
 alert('上报失败');
 }
};
onMounted(() => {
 loadDevices();
 setInterval(loadDevices, 5000);
});
</script>