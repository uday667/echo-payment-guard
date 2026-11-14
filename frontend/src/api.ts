export type AlertStatus = 'OPEN' | 'REVIEWED' | 'DISMISSED'

export interface Alert {
  id: string
  type: string
  status: AlertStatus
  debtorAccount: string
  creditorAccount: string
  amount: number
  currency: string
  createdAt: string
}

export interface AlertPage {
  content: Alert[]
  page: number
  size: number
  totalElements: number
}

interface ApiError {
  error?: string
  message?: string
}

async function responseError(response: Response): Promise<Error> {
  const body: ApiError = await response.json().catch(() => ({}))
  return new Error(body.error ?? body.message ?? `Request failed (${response.status})`)
}

export async function getAlerts(
  filters: { type: string; status: string; page: number; size: number },
  signal?: AbortSignal,
): Promise<AlertPage> {
  const params = new URLSearchParams({
    page: String(filters.page),
    size: String(filters.size),
  })
  if (filters.type) params.set('type', filters.type)
  if (filters.status) params.set('status', filters.status)

  const response = await fetch(`/api/alerts?${params}`, { signal })
  if (!response.ok) throw await responseError(response)
  return response.json() as Promise<AlertPage>
}

export async function updateAlert(
  id: string,
  status: Exclude<AlertStatus, 'OPEN'>,
  note: string,
): Promise<void> {
  const response = await fetch(`/api/alerts/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'X-User': 'investigator',
    },
    body: JSON.stringify({ status, note }),
  })
  if (!response.ok) throw await responseError(response)
}
