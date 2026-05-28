<template>
  <div class="data-monitor">
    <DataStatsCard :stats="statistics" />

    <DataTable
      :data="latestData"
      @refresh="loadLatestData"
      @detail="showDeviceDetail"
    />

    <DataChart :devices="monitorDevices" />

    <AlertPanel />
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import DataStatsCard from './DataStatsCard.vue'
import DataTable from './DataTable.vue'
import DataChart from './DataChart.vue'
import AlertPanel from './AlertPanel.vue'
import { dataApi, deviceApi, sseApi } from '../../api'

const latestData = ref([])
const statistics = ref({})
const monitorDevices = ref([])
let eventSource = null
let refreshTimer = null

const loadLatestData = async () => {
  try {
    const res = await dataApi.getLatest()
    const apiData = res.data
    if (apiData && apiData.length > 0) {
      const dataMap = new Map(latestData.value.map(d => [d.deviceId, d]))
      apiData.forEach(item => {
        dataMap.set(item.deviceId, item)
      })
      latestData.value = [...dataMap.values()]
    }
  } catch (e) {
    console.error('Failed to load latest data:', e)
  }
}

const loadStatistics = async () => {
  try {
    const res = await dataApi.getStatistics()
    if (res.data) {
      statistics.value = res.data
    }
  } catch (e) {
    console.error('Failed to load statistics:', e)
  }
}

const loadDevices = async () => {
  try {
    const res = await deviceApi.getAll()
    if (res.data) {
      monitorDevices.value = res.data
    }
  } catch (e) {
    console.error('Failed to load devices:', e)
  }
}

const connectSse = () => {
  const url = sseApi.getSseUrl()
  eventSource = new EventSource(url)

  eventSource.addEventListener('deviceData', (event) => {
    try {
      const deviceData = JSON.parse(event.data)
      const dataMap = new Map(latestData.value.map(d => [d.deviceId, d]))
      dataMap.set(deviceData.deviceId, deviceData)
      latestData.value = [...dataMap.values()]
    } catch (e) {
      console.error('SSE parse error:', e)
    }
  })

  eventSource.addEventListener('connected', () => {
    console.log('SSE connected')
  })

  eventSource.onerror = () => {
    console.warn('SSE connection error, will retry...')
  }
}

const showDeviceDetail = () => {
  loadDevices()
}

onMounted(async () => {
  loadDevices()
  await loadLatestData()
  loadStatistics()
  connectSse()
  refreshTimer = setInterval(() => {
    loadStatistics()
  }, 10000)
})

onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
  }
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})
</script>
