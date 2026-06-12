import { apiRequest } from './request'
import type { PushDeviceRegisterRequest, PushDeviceRegisterResponse } from './types'

export function registerPushDevice(data: PushDeviceRegisterRequest) {
  return apiRequest<PushDeviceRegisterResponse>({
    method: 'POST',
    path: '/api/push/devices/register',
    data
  })
}

export function unbindPushDevice(deviceId: string) {
  return apiRequest({
    method: 'DELETE',
    path: `/api/push/devices/${encodeURIComponent(deviceId)}`
  })
}
