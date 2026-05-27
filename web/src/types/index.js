export interface Device {
  id: string
  deviceId: string
  deviceName: string
  deviceType: string
  reportTopic: string
  autoReport: boolean
  interval: number
  dataRange: DataRange
  enableErrorData: boolean
  cmdTopic: string
  enableCmdListen: boolean
  replyTopic: string
  status: 'online' | 'offline'
}

export interface DataRange {
  temperature?: { min: number; max: number }
  humidity?: { min: number; max: number }
  voltage?: { min: number; max: number }
}

export interface ReportData {
  deviceId: string
  timestamp: number
  temperature: number
  humidity: number
  voltage: number
  status: string
}

export interface Command {
  type: 'start_report' | 'stop_report' | 'set_interval' | 'offline' | 'custom'
  params: Record<string, unknown>
  timestamp: number
}

export interface CommandReply {
  deviceId: string
  command: Command
  status: 'success' | 'failed'
  timestamp: number
}

export interface Statistics {
  totalDevices: number
  onlineDevices: number
  dataRate: number
  receivedCount: number
}

export interface Alert {
  id: string
  deviceId: string
  type: 'manual' | 'threshold'
  message: string
  timestamp: number
  enabled: boolean
}