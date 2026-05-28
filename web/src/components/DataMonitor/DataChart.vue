<template>
  <div class="card">
    <div class="flex justify-between align-center mb-20">
      <h2>数据趋势图表</h2>
      <div class="flex gap-10">
        <select v-model="selectedDevice" class="form-control" style="width: 180px;">
          <option value="">选择设备</option>
          <option v-for="d in devices" :key="d.deviceId" :value="d.deviceId">
            {{ d.deviceId }}
          </option>
        </select>
        <button class="btn btn-primary" @click="loadHistory">加载</button>
      </div>
    </div>

    <div v-if="!selectedDevice" class="text-center text-muted py-20">
      请选择一个设备查看其历史数据趋势
    </div>

    <div v-else class="chart-wrapper">
      <canvas ref="chartCanvas"></canvas>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'
import { Chart, registerables } from 'chart.js'
import { dataApi } from '../../api'

Chart.register(...registerables)

const props = defineProps({
  devices: {
    type: Array,
    default: () => []
  }
})

const selectedDevice = ref('')
const chartCanvas = ref(null)
let chartInstance = null

const loadHistory = async () => {
  if (!selectedDevice.value) return
  try {
    const endTime = Date.now()
    const startTime = endTime - 3600 * 1000
    const res = await dataApi.getHistory(selectedDevice.value, {
      startTime,
      endTime,
      limit: 120
    })
    const data = (res.data || []).reverse()
    renderChart(data)
  } catch (e) {
    console.error('Failed to load history:', e)
  }
}

const renderChart = (data) => {
  if (chartInstance) {
    chartInstance.destroy()
  }

  if (!chartCanvas.value) return

  const labels = data.map(d => {
    const d2 = new Date(d.timestamp)
    return d2.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  })

  const createGradient = (ctx, color1, color2) => {
    const gradient = ctx.createLinearGradient(0, 0, 0, 300)
    gradient.addColorStop(0, color1)
    gradient.addColorStop(1, color2)
    return gradient
  }

  const ctx = chartCanvas.value.getContext('2d')

  chartInstance = new Chart(ctx, {
    type: 'line',
    data: {
      labels,
      datasets: [
        {
          label: '温度 (°C)',
          data: data.map(d => d.temperature),
          borderColor: '#ff6b6b',
          backgroundColor: createGradient(ctx, 'rgba(255,107,107,0.2)', 'rgba(255,107,107,0)'),
          fill: true,
          tension: 0.3,
          pointRadius: 0,
          borderWidth: 2
        },
        {
          label: '湿度 (%)',
          data: data.map(d => d.humidity),
          borderColor: '#4ecdc4',
          backgroundColor: createGradient(ctx, 'rgba(78,205,196,0.2)', 'rgba(78,205,196,0)'),
          fill: true,
          tension: 0.3,
          pointRadius: 0,
          borderWidth: 2
        },
        {
          label: '电压 (V)',
          data: data.map(d => d.voltage),
          borderColor: '#ffd93d',
          backgroundColor: createGradient(ctx, 'rgba(255,217,61,0.15)', 'rgba(255,217,61,0)'),
          fill: true,
          tension: 0.3,
          pointRadius: 0,
          borderWidth: 2,
          yAxisID: 'y1'
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: {
        mode: 'index',
        intersect: false
      },
      plugins: {
        legend: {
          position: 'top'
        }
      },
      scales: {
        x: {
          display: true,
          title: {
            display: true,
            text: '时间'
          },
          ticks: {
            maxTicksLimit: 15
          }
        },
        y: {
          type: 'linear',
          display: true,
          position: 'left',
          title: {
            display: true,
            text: '温度 / 湿度'
          }
        },
        y1: {
          type: 'linear',
          display: true,
          position: 'right',
          title: {
            display: true,
            text: '电压 (V)'
          },
          grid: {
            drawOnChartArea: false
          }
        }
      }
    }
  })
}

watch(selectedDevice, (newVal) => {
  if (newVal) loadHistory()
})

onBeforeUnmount(() => {
  if (chartInstance) {
    chartInstance.destroy()
  }
})
</script>

<style scoped>
.chart-wrapper {
  width: 100%;
  height: 320px;
}
</style>
