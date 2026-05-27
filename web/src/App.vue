<template>
  <div id="app">
    <header class="header">
      <h1>MQTT 多设备模拟控制台</h1>
      <nav class="nav">
        <button 
          :class="['nav-item', { active: activeModule === 'control' }]"
          @click="activeModule = 'control'"
        >
          设备控制
        </button>
        <button 
          :class="['nav-item', { active: activeModule === 'monitor' }]"
          @click="activeModule = 'monitor'"
        >
          实时监控
        </button>
      </nav>
    </header>

    <main class="main">
      <DeviceControl v-if="activeModule === 'control'" />
      <div v-else class="card">
        <h2>实时数据监控中心</h2>
        <p class="text-muted">实时数据展示模块开发中...</p>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import DeviceControl from './components/DeviceControl/index.vue'

const activeModule = ref('control')
</script>

<style scoped>
.header {
  background-color: #2c3e50;
  color: white;
  padding: 20px 30px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.header h1 {
  font-size: 20px;
  font-weight: 600;
}

.nav {
  display: flex;
  gap: 5px;
}

.nav-item {
  padding: 8px 20px;
  background: none;
  border: none;
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  border-radius: 4px;
  font-size: 14px;
  transition: all 0.2s;
}

.nav-item:hover {
  background-color: rgba(255, 255, 255, 0.1);
  color: white;
}

.nav-item.active {
  background-color: #3498db;
  color: white;
}

.main {
  padding: 20px 30px;
  min-height: calc(100vh - 70px);
}
</style>