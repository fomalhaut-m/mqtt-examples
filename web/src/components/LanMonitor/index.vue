<template>
  <div class="lan-monitor">
    <div v-if="!lanData" class="card text-center text-muted py-20">
      <p>等待局域网扫描数据...</p>
      <p style="font-size:12px;">请确保 <code>lan_scanner.py</code> 正在运行</p>
    </div>

    <template v-else>
      <div class="lan-header card mb-20">
        <div class="flex justify-between align-center">
          <div>
            <h2>{{ lanData.hostname }}</h2>
            <span class="text-muted">本机: {{ lanData.network?.local_ip }}</span>
          </div>
          <div class="text-right">
            <div class="text-sm text-muted">扫描网段</div>
            <div class="fw-bold">{{ lanData.network?.cidr }}</div>
          </div>
          <div class="text-right">
            <div class="text-sm text-muted">扫描方式</div>
            <div class="fw-bold">{{ lanData.network?.scan_method }}</div>
          </div>
          <div class="text-right">
            <div class="text-sm text-muted">更新时间</div>
            <div class="fw-bold">{{ formatTime(lanData.timestamp) }}</div>
          </div>
        </div>
      </div>

      <div class="stats-row mb-20">
        <div class="stat-card">
          <div class="stat-value">{{ lanData.network?.total_hosts || 0 }}</div>
          <div class="stat-label">总地址</div>
        </div>
        <div class="stat-card stat-alive">
          <div class="stat-value">{{ lanData.network?.alive_hosts || 0 }}</div>
          <div class="stat-label">活跃设备</div>
        </div>
        <div class="stat-card stat-identified">
          <div class="stat-value">{{ lanData.network?.identified || 0 }}</div>
          <div class="stat-label">已识别</div>
        </div>
        <div class="stat-card stat-unknown">
          <div class="stat-value">{{ lanData.network?.unknown || 0 }}</div>
          <div class="stat-label">未识别</div>
        </div>
        <div class="stat-card stat-time">
          <div class="stat-value">{{ lanData.network?.scan_duration_s || 0 }}s</div>
          <div class="stat-label">扫描耗时</div>
        </div>
      </div>

      <div class="card">
        <div class="flex justify-between align-center mb-10">
          <h3>设备列表 ({{ lanData.devices?.length || 0 }})</h3>
          <input v-model="searchText" type="text" class="search-input" placeholder="搜索 IP / MAC / 厂商..." />
        </div>

        <table class="lan-table" v-if="filteredDevices.length > 0">
          <thead>
            <tr>
              <th>IP 地址</th>
              <th>MAC 地址</th>
              <th>厂商</th>
              <th>主机名</th>
              <th v-if="hasPorts">开放端口</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(d, i) in filteredDevices" :key="i" :class="{ 'row-local': d.ip === lanData.network?.local_ip }">
              <td><code>{{ d.ip }}</code><span v-if="d.ip === lanData.network?.local_ip" class="tag-local">本机</span></td>
              <td><code>{{ d.mac || '-' }}</code></td>
              <td>
                <span :class="['vendor-tag', { 'vendor-known': d.vendor !== 'Unknown' }]">{{ d.vendor }}</span>
              </td>
              <td>{{ d.hostname || '-' }}</td>
              <td v-if="hasPorts">
                <span v-for="p in d.open_ports" :key="p.port" class="port-tag">{{ p.port }} ({{ p.service }})</span>
                <span v-if="!d.open_ports || d.open_ports.length === 0" class="text-muted">-</span>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="text-center text-muted py-10">无匹配设备</div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { sseApi } from '../../api'

const lanData = ref(null)
const searchText = ref('')
let eventSource = null

const hasPorts = computed(() => {
  return lanData.value?.devices?.some(d => d.open_ports && d.open_ports.length > 0)
})

const filteredDevices = computed(() => {
  const devices = lanData.value?.devices || []
  const keyword = searchText.value.toLowerCase().trim()
  if (!keyword) return devices
  return devices.filter(d =>
    d.ip.toLowerCase().includes(keyword) ||
    (d.mac || '').toLowerCase().includes(keyword) ||
    (d.vendor || '').toLowerCase().includes(keyword) ||
    (d.hostname || '').toLowerCase().includes(keyword)
  )
})

const formatTime = (ts) => {
  if (!ts) return '-'
  return new Date(ts).toLocaleTimeString()
}

onMounted(async () => {
  const url = sseApi.getSseUrl()
  eventSource = new EventSource(url)

  eventSource.addEventListener('lanScan', (event) => {
    try {
      lanData.value = JSON.parse(event.data)
    } catch (e) {
      console.error('LanScan parse error:', e)
    }
  })

  eventSource.onerror = () => {
    console.warn('SSE error, retrying...')
  }

  setTimeout(async () => {
    if (!lanData.value) {
      try {
        const res = await sseApi.getLanSnapshot()
        if (res.success && res.data) {
          lanData.value = res.data
        }
      } catch (e) {
        console.warn('LAN snapshot API fallback:', e)
      }
    }
  }, 2000)
})

onBeforeUnmount(() => {
  if (eventSource) eventSource.close()
})
</script>

<style scoped>
.lan-monitor h2 { font-size: 18px; }
.lan-monitor h3 { font-size: 15px; color: #555; }

.lan-header .flex { align-items: center; }

.stats-row { display: flex; gap: 12px; }
.stat-card {
  flex: 1;
  background: white;
  border-radius: 8px;
  padding: 16px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  border-left: 4px solid #ccc;
}
.stat-card.stat-alive { border-left-color: #67c23a; }
.stat-card.stat-identified { border-left-color: #409eff; }
.stat-card.stat-unknown { border-left-color: #e6a23c; }
.stat-card.stat-time { border-left-color: #909399; }
.stat-value { font-size: 28px; font-weight: 700; }
.stat-label { font-size: 13px; color: #999; margin-top: 4px; }

.search-input {
  padding: 6px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 13px;
  width: 220px;
}

.lan-table { width: 100%; border-collapse: collapse; }
.lan-table th {
  background: #f5f7fa;
  padding: 10px 12px;
  text-align: left;
  font-size: 13px;
  color: #666;
  border-bottom: 2px solid #e4e7ed;
}
.lan-table td {
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
  font-size: 13px;
}
.lan-table code { font-size: 12px; background: #f0f0f0; padding: 1px 5px; border-radius: 3px; }
.lan-table tr:hover { background: #f5f7fa; }

.row-local { background: #ecf5ff; }
.row-local:hover { background: #d9ecff; }
.tag-local {
  display: inline-block;
  margin-left: 6px;
  background: #409eff;
  color: white;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
}

.vendor-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  background: #f0f0f0;
  color: #999;
}
.vendor-tag.vendor-known { background: #e1f3d8; color: #67c23a; }

.port-tag {
  display: inline-block;
  margin-right: 4px;
  margin-bottom: 2px;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  background: #ecf5ff;
  color: #409eff;
}

.mb-10 { margin-bottom: 10px; }
.mb-20 { margin-bottom: 20px; }
.py-10 { padding: 10px 0; }
.py-20 { padding: 20px 0; }
.text-right { text-align: right; }
</style>
