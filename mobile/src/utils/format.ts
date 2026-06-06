export function formatCount(value: number | null | undefined) {
  const count = Number(value ?? 0)
  if (count >= 10000) {
    return `${(count / 10000).toFixed(1)}万`
  }
  return String(count)
}

export function formatTime(value: string | null | undefined) {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

export function splitTopics(raw: string) {
  return raw
    .split(/[#,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 10)
}

export function fallbackAvatar(name?: string | null) {
  const initial = (name || 'BN').slice(0, 2).toUpperCase()
  return initial
}

export function createClientRequestId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

