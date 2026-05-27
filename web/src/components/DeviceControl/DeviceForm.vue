<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <div class="modal">
      <div class="modal-header">
        <h3 class="modal-title">{{ isEdit ? '编辑设备' : '添加设备' }}</h3>
        <button class="modal-close" @click="$emit('close')">×</button>
      </div>

      <form @submit.prevent="handleSubmit">
        <div class="form-group">
          <label>设备ID</label>
          <input 
            v-model="form.deviceId" 
            type="text" 
            class="form-control" 
            :disabled="isEdit"
            placeholder="请输入设备ID"
            required
          />
        </div>

        <div class="form-group">
          <label>设备名称</label>
          <input 
            v-model="form.deviceName" 
            type="text" 
            class="form-control" 
            placeholder="请输入设备名称"
            required
          />
        </div>

        <div class="form-group">
          <label>设备类型</label>
          <input 
            v-model="form.deviceType" 
            type="text" 
            class="form-control" 
            placeholder="请输入设备类型"
            required
          />
        </div>

        <div class="form-group">
          <label>上报Topic</label>
          <input 
            v-model="form.reportTopic" 
            type="text" 
            class="form-control" 
            placeholder="请输入上报Topic"
            required
          />
        </div>

        <div class="form-group">
          <label>上报间隔（秒）</label>
          <input 
            v-model.number="form.interval" 
            type="number" 
            class="form-control" 
            min="1"
            placeholder="请输入上报间隔"
            required
          />
        </div>

        <div class="form-group">
          <label>指令Topic</label>
          <input 
            v-model="form.cmdTopic" 
            type="text" 
            class="form-control" 
            placeholder="请输入指令Topic"
          />
        </div>

        <div class="form-group">
          <label>回执Topic</label>
          <input 
            v-model="form.replyTopic" 
            type="text" 
            class="form-control" 
            placeholder="请输入回执Topic"
          />
        </div>

        <div class="form-group">
          <label class="flex items-center gap-10">
            <input 
              v-model="form.enableCmdListen" 
              type="checkbox" 
            />
            启用指令监听
          </label>
        </div>

        <div class="form-group">
          <label class="flex items-center gap-10">
            <input 
              v-model="form.enableErrorData" 
              type="checkbox" 
            />
            启用异常数据模拟
          </label>
        </div>

        <div class="modal-footer">
          <button type="button" class="btn" @click="$emit('close')">取消</button>
          <button type="submit" class="btn btn-primary">保存</button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  device: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['close', 'submit'])

const isEdit = computed(() => !!props.device)

const form = ref({
  deviceId: '',
  deviceName: '',
  deviceType: 'sensor',
  reportTopic: '',
  interval: 5,
  cmdTopic: '',
  replyTopic: '',
  enableCmdListen: false,
  enableErrorData: false,
  autoReport: false,
  dataRange: {},
  status: 'offline'
})

watch(() => props.device, (newDevice) => {
  if (newDevice) {
    form.value = { ...newDevice }
  } else {
    resetForm()
  }
}, { immediate: true })

const resetForm = () => {
  form.value = {
    deviceId: '',
    deviceName: '',
    deviceType: 'sensor',
    reportTopic: '',
    interval: 5,
    cmdTopic: '',
    replyTopic: '',
    enableCmdListen: false,
    enableErrorData: false,
    autoReport: false,
    dataRange: {},
    status: 'offline'
  }
}

const handleSubmit = () => {
  emit('submit', { ...form.value })
}

import { computed } from 'vue'
</script>