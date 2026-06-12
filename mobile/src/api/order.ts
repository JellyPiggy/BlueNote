import { apiRequest } from './request'
import type {
  OrderActivityCurrentResponse,
  OrderCancelResponse,
  OrderCouponListResponse,
  OrderDetailResponse,
  OrderPayResponse,
  OrderSeckillResultResponse,
  OrderSeckillSubmitResponse,
  OrderSeckillTokenResponse
} from './types'

export function getCurrentOrderActivity() {
  return apiRequest<OrderActivityCurrentResponse>({
    path: '/api/order/coupon-activities/current'
  })
}

export function getSeckillToken(activityId: string) {
  return apiRequest<OrderSeckillTokenResponse>({
    method: 'POST',
    path: '/api/order/seckill/token',
    data: { activityId }
  })
}

export function submitSeckillOrder(payload: {
  activityId: string
  clientRequestId: string
  seckillToken: string
}) {
  return apiRequest<OrderSeckillSubmitResponse>({
    method: 'POST',
    path: '/api/order/seckill/orders',
    data: payload
  })
}

export function getSeckillResult(requestId: string) {
  return apiRequest<OrderSeckillResultResponse>({
    path: `/api/order/seckill/results/${requestId}`
  })
}

export function getOrderDetail(orderId: string) {
  return apiRequest<OrderDetailResponse>({
    path: `/api/order/orders/${orderId}`
  })
}

export function mockPayOrder(orderId: string) {
  return apiRequest<OrderPayResponse>({
    method: 'POST',
    path: `/api/order/orders/${orderId}/pay`,
    data: { channel: 'MOCK' }
  })
}

export function cancelOrder(orderId: string) {
  return apiRequest<OrderCancelResponse>({
    method: 'POST',
    path: `/api/order/orders/${orderId}/cancel`
  })
}

export function getMyCoupons(status: 'UNUSED' | 'USED' | 'EXPIRED' | null = 'UNUSED', cursor?: string | null, pageSize = 20) {
  const query = new URLSearchParams()
  query.set('pageSize', String(pageSize))
  if (status) {
    query.set('status', status)
  }
  if (cursor) {
    query.set('cursor', cursor)
  }
  return apiRequest<OrderCouponListResponse>({
    path: `/api/order/my-coupons?${query.toString()}`
  })
}
