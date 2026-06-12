import type { DeviceInfo } from '@/api/types'

const DEVICE_ID_KEY = 'bluenote.deviceId'
const APP_VERSION = '0.1.0'

export function getDeviceInfo(): DeviceInfo {
  const system = uni.getSystemInfoSync()
  const platform = platformOf(system.platform)
  return {
    deviceId: getOrCreateDeviceId(),
    deviceName: system.model || system.deviceModel || null,
    platform,
    appVersion: APP_VERSION
  }
}

export function getPushDeviceRegistration() {
  const system = uni.getSystemInfoSync()
  const device = getDeviceInfo()
  return {
    deviceId: device.deviceId,
    platform: device.platform,
    pushProvider: 'NOOP' as const,
    providerClientId: null,
    appVersion: device.appVersion,
    osVersion: system.osName || system.osVersion || null,
    deviceModel: system.model || system.deviceModel || null
  }
}

function getOrCreateDeviceId() {
  const cached = uni.getStorageSync(DEVICE_ID_KEY)
  if (cached) {
    return String(cached)
  }
  const id = `device-${Date.now()}-${Math.random().toString(16).slice(2)}`
  uni.setStorageSync(DEVICE_ID_KEY, id)
  return id
}

function platformOf(platform?: string): DeviceInfo['platform'] {
  const value = (platform ?? '').toLowerCase()
  if (value.includes('ios')) {
    return 'IOS'
  }
  if (value.includes('android')) {
    return 'ANDROID'
  }
  return 'H5'
}
