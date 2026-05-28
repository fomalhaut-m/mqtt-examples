<template>
  <div class="system-monitor">
    <div v-if="!systemData" class="card text-center text-muted py-20">
      <p>等待系统数据...</p>
      <p style="font-size:12px;">请确保 <code>system_monitor.py</code> 正在运行</p>
    </div>

    <template v-else>
      <div class="sys-header card mb-20">
        <div class="flex justify-between align-center">
          <div>
            <h2>{{ systemData.hostname }}</h2>
            <span class="text-muted">{{ systemData.platform }} {{ systemData.arch }}</span>
          </div>
          <div class="text-right">
            <div class="text-sm text-muted">运行时间</div>
            <div class="fw-bold">{{ systemData.uptime?.uptime_str }}</div>
          </div>
          <div v-if="systemData.public_ip" class="text-right">
            <div class="text-sm text-muted">公网 IP</div>
            <div class="fw-bold">{{ systemData.public_ip }}</div>
          </div>
        </div>
      </div>

      <div class="grid-2">
        <div class="card">
          <h3>CPU</h3>
          <div class="gauge-row">
            <div class="gauge">
              <svg viewBox="0 0 120 70">
                <circle cx="60" cy="60" r="50" fill="none" stroke="#eee" stroke-width="10" />
                <circle cx="60" cy="60" r="50" fill="none" :stroke="colorForPercent(systemData.cpu.percent)"
                  stroke-width="10" stroke-linecap="round"
                  :stroke-dasharray="314" :stroke-dashoffset="314 - 314 * systemData.cpu.percent / 100"
                  transform="rotate(180 60 60)" />
              </svg>
              <div class="gauge-value">{{ systemData.cpu.percent.toFixed(1) }}%</div>
            </div>
            <div>
              <div>{{ systemData.cpu.count_logical }} 逻辑核心</div>
              <div class="text-sm text-muted">{{ systemData.cpu.count_physical }} 物理核心 · {{ (systemData.cpu.freq_current || 0) }}MHz</div>
            </div>
          </div>
          <div class="per-core mt-10">
            <div v-for="(c, i) in systemData.cpu.per_core" :key="i" class="core-bar">
              <span class="core-label">{{ i }}</span>
              <div class="core-track"><div class="core-fill" :style="{ width: c + '%', background: colorForPercent(c) }"></div></div>
              <span class="core-val">{{ c }}%</span>
            </div>
          </div>
        </div>

        <div class="card">
          <h3>内存</h3>
          <div class="progress-section">
            <div class="progress-label">物理内存 {{ systemData.memory.percent.toFixed(1) }}%</div>
            <div class="progress-bar"><div class="progress-fill" :style="{ width: systemData.memory.percent + '%', background: colorForPercent(systemData.memory.percent) }"></div></div>
            <div class="text-sm text-muted">{{ systemData.memory.used_gb }} / {{ systemData.memory.total_gb }} GB</div>
          </div>
          <div class="progress-section mt-10">
            <div class="progress-label">可用 {{ systemData.memory.available_gb }} GB</div>
            <div class="text-sm text-muted">缓存 {{ systemData.memory.cached_gb }} GB</div>
          </div>
          <div class="progress-section mt-10">
            <div class="progress-label">Swap {{ systemData.memory.swap_percent.toFixed(1) }}%</div>
            <div class="progress-bar"><div class="progress-fill" :style="{ width: systemData.memory.swap_percent + '%', background: '#ff9800' }"></div></div>
            <div class="text-sm text-muted">{{ systemData.memory.swap_used_gb }} / {{ systemData.memory.swap_total_gb }} GB</div>
          </div>
        </div>
      </div>

      <div class="grid-2">
        <div class="card">
          <h3>磁盘</h3>
          <div v-for="(d, i) in systemData.disks" :key="i" class="progress-section">
            <div class="progress-label">{{ d.mountpoint }} ({{ d.device }})</div>
            <div class="progress-bar"><div class="progress-fill" :style="{ width: d.percent + '%', background: colorForPercent(d.percent) }"></div></div>
            <div class="text-sm text-muted">{{ d.used_gb }} / {{ d.total_gb }} GB</div>
          </div>
          <div v-if="systemData.disk_rate" class="mt-10 flex gap-20">
            <span>读 {{ systemData.disk_rate.read_mb_s }} MB/s</span>
            <span>写 {{ systemData.disk_rate.write_mb_s }} MB/s</span>
          </div>
        </div>

        <div class="card">
          <h3>网络</h3>
          <div class="net-row">
            <div>
              <div class="text-sm text-muted">下载速度</div>
              <div class="fw-bold" style="color:#4ecdc4">{{ (systemData.network.download_kb_s || 0).toFixed(1) }} KB/s</div>
            </div>
            <div>
              <div class="text-sm text-muted">上传速度</div>
              <div class="fw-bold" style="color:#ff6b6b">{{ (systemData.network.upload_kb_s || 0).toFixed(1) }} KB/s</div>
            </div>
            <div>
              <div class="text-sm text-muted">总下载</div>
              <div>{{ (systemData.network.recv_mb || 0).toFixed(1) }} MB</div>
            </div>
            <div>
              <div class="text-sm text-muted">总上传</div>
              <div>{{ (systemData.network.sent_mb || 0).toFixed(1) }} MB</div>
            </div>
          </div>
          <div class="mt-10 text-sm text-muted">
            TCP {{ systemData.network.tcp_count || 0 }} · UDP {{ systemData.network.udp_count || 0 }}
          </div>
          <div v-if="systemData.network.interface" class="mt-10 text-sm text-muted">
            <div v-for="(ip, iface) in systemData.network.interface" :key="iface">{{ iface }}: {{ ip }}</div>
          </div>
        </div>
      </div>

      <div class="grid-2" v-if="hasSensors">
        <div v-if="tempKeys.length" class="card">
          <h3>温度</h3>
          <div v-for="key in tempKeys" :key="key">
            <div class="text-sm fw-bold">{{ key }}</div>
            <div v-for="t in systemData.temperatures[key]" :key="t.label" class="flex gap-10 mb-5">
              <span class="fw-bold">{{ t.current }}°C</span>
              <span class="text-sm text-muted">{{ t.label }}</span>
            </div>
          </div>
        </div>

        <div v-if="fanKeys.length" class="card">
          <h3>风扇</h3>
          <div v-for="key in fanKeys" :key="key">
            <div class="text-sm fw-bold">{{ key }}</div>
            <div v-for="f in systemData.fans[key]" :key="f.label" class="flex gap-10 mb-5">
              <span class="fw-bold">{{ f.current }} RPM</span>
              <span class="text-sm text-muted">{{ f.label }}</span>
            </div>
          </div>
        </div>

        <div v-if="systemData.battery?.has_battery" class="card">
          <h3>电池</h3>
          <div class="text-center">
            <div class="gauge-value text-success">{{ systemData.battery.percent }}%</div>
            <div>{{ systemData.battery.charging ? '充电中' : '放电中' }}</div>
            <div v-if="systemData.battery.remaining_min" class="text-sm text-muted">剩余 {{ systemData.battery.remaining_min }} 分钟</div>
          </div>
        </div>

        <div v-if="systemData.ping" class="card">
          <h3>网络延迟</h3>
          <div class="flex justify-between">
            <span>网关</span><span class="fw-bold">{{ systemData.ping.gateway_ms || '-' }} ms</span>
          </div>
          <div class="flex justify-between mt-5">
            <span>百度</span><span class="fw-bold">{{ systemData.ping.baidu_ms || '-' }} ms</span>
          </div>
        </div>
      </div>

      <div class="card" v-if="systemData.top_processes?.length">
        <h3>进程 TOP 15</h3>
        <table class="table">
          <thead><tr><th>PID</th><th>名称</th><th>CPU %</th><th>内存 %</th><th>状态</th></tr></thead>
          <tbody>
            <tr v-for="p in systemData.top_processes" :key="p.pid">
              <td>{{ p.pid }}</td>
              <td>{{ p.name }}</td>
              <td>{{ p.cpu_percent }}</td>
              <td>{{ p.memory_percent }}</td>
              <td>{{ p.status }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { sseApi } from '../../api'

const systemData = ref(null)
let eventSource = null

const tempKeys = computed(() => systemData.value?.temperatures ? Object.keys(systemData.value.temperatures) : [])
const fanKeys = computed(() => systemData.value?.fans ? Object.keys(systemData.value.fans) : [])
const hasSensors = computed(() => tempKeys.value.length > 0 || fanKeys.value.length > 0)

const colorForPercent = (pct) => {
  if (pct > 90) return '#f56c6c'
  if (pct > 70) return '#e6a23c'
  return '#67c23a'
}

onMounted(async () => {
  const url = sseApi.getSseUrl()
  eventSource = new EventSource(url)

  eventSource.addEventListener('systemMetrics', (event) => {
    try {
      systemData.value = JSON.parse(event.data)
    } catch (e) {
      console.error('SystemMetrics parse error:', e)
    }
  })

  eventSource.addEventListener('connected', () => {
    console.log('SSE connected for system monitor')
  })

  eventSource.onerror = () => {
    console.warn('SSE error, retrying...')
  }

  setTimeout(async () => {
    if (!systemData.value) {
      try {
        const res = await sseApi.getSystemSnapshot()
        if (res.success && res.data) {
          systemData.value = res.data
        }
      } catch (e) {
        console.warn('System snapshot API fallback:', e)
      }
    }
  }, 2000)
})

onBeforeUnmount(() => {
  if (eventSource) eventSource.close()
})
</script>

<style scoped>
.system-monitor h2 { font-size: 18px; }
.system-monitor h3 { font-size: 15px; margin-bottom: 12px; color: #555; }

.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px; }

.gauge-row { display: flex; align-items: center; gap: 16px; }
.gauge { position: relative; width: 100px; height: 70px; }
.gauge svg { width: 100px; height: 70px; }
.gauge-value { font-size: 24px; font-weight: 700; position: absolute; bottom: 0; left: 50%; transform: translateX(-50%); }

.per-core { display: flex; flex-wrap: wrap; gap: 4px; }
.core-bar { display: flex; align-items: center; gap: 4px; width: 100%; }
.core-label { font-size: 11px; width: 16px; text-align: right; color: #999; }
.core-val { font-size: 11px; width: 30px; }
.core-track { flex: 1; height: 8px; background: #eee; border-radius: 4px; overflow: hidden; }
.core-fill { height: 100%; border-radius: 4px; transition: width 1s; }

.progress-section { margin-bottom: 8px; }
.progress-label { font-size: 13px; margin-bottom: 2px; }
.progress-bar { height: 12px; background: #eee; border-radius: 6px; overflow: hidden; margin-bottom: 2px; }
.progress-fill { height: 100%; border-radius: 6px; transition: width 1s; min-width: 2px; }

.net-row { display: flex; gap: 20px; }

.mt-5 { margin-top: 5px; }
.mt-10 { margin-top: 10px; }
.mb-5 { margin-bottom: 5px; }
.mb-20 { margin-bottom: 20px; }
.gap-20 { gap: 20px; }
.text-right { text-align: right; }
</style>
