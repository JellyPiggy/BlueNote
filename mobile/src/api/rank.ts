import { apiRequest } from './request'
import type {
  MyYearlyCreatorRankResponse,
  WeeklyHotNotesRankResponse,
  YearlyCreatorGrowthRankResponse
} from './types'

export function getWeeklyHotNotesRank(cursor?: string | null, size = 20) {
  const query = new URLSearchParams()
  query.set('size', String(size))
  if (cursor) {
    query.set('cursor', cursor)
  }
  return apiRequest<WeeklyHotNotesRankResponse>({
    path: `/api/ranks/weekly-hot-notes?${query.toString()}`,
    auth: false
  })
}

export function getYearlyCreatorGrowthRank(cursor?: string | null, size = 20) {
  const query = new URLSearchParams()
  query.set('size', String(size))
  if (cursor) {
    query.set('cursor', cursor)
  }
  return apiRequest<YearlyCreatorGrowthRankResponse>({
    path: `/api/ranks/yearly-creator-growth?${query.toString()}`,
    auth: false
  })
}

export function getMyYearlyCreatorRank() {
  return apiRequest<MyYearlyCreatorRankResponse>({
    path: '/api/ranks/creators/me/yearly-growth-rank'
  })
}
