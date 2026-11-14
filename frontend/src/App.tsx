import { useEffect, useState } from 'react'
import { getAlerts, updateAlert, type Alert, type AlertPage, type AlertStatus } from './api'
import './App.css'

const PAGE_SIZE = 10
const ALERT_TYPE = 'KEY_REUSE_MISMATCH'

function formatAmount(amount: number, currency: string) {
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency,
    }).format(amount)
  } catch {
    return `${amount.toLocaleString()} ${currency}`
  }
}

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function App() {
  const [type, setType] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState<AlertPage | null>(null)
  const [pageIndex, setPageIndex] = useState(0)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [actionError, setActionError] = useState('')
  const [pendingId, setPendingId] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()

    getAlerts({ type, status, page: pageIndex, size: PAGE_SIZE }, controller.signal)
      .then(setPage)
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setLoadError(error instanceof Error ? error.message : 'Unable to load alerts')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [type, status, pageIndex])

  async function reviewAlert(alert: Alert, nextStatus: Exclude<AlertStatus, 'OPEN'>) {
    setPendingId(alert.id)
    setActionError('')
    const note = nextStatus === 'DISMISSED'
      ? 'Dismissed by investigator'
      : 'Reviewed by investigator'

    try {
      await updateAlert(alert.id, nextStatus, note)
      setPage((current) => current && ({
        ...current,
        content: current.content.map((item) =>
          item.id === alert.id ? { ...item, status: nextStatus } : item,
        ),
      }))
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Unable to update alert')
    } finally {
      setPendingId(null)
    }
  }

  const totalPages = page ? Math.ceil(page.totalElements / PAGE_SIZE) : 0

  return (
    <main className="app-shell">
      <header className="page-header">
        <div>
          <p className="eyebrow">PAYMENT OPERATIONS</p>
          <h1>Alert queue</h1>
          <p className="subtitle">Review and resolve payment alerts.</p>
        </div>
        <div className="queue-count">
          <span className="queue-count-value">{page?.totalElements ?? '—'}</span>
          <span className="queue-count-label">alerts</span>
        </div>
      </header>

      <section className="queue-panel" aria-label="Alert queue">
        <div className="filters">
          <label>
            <span>Type</span>
            <select
              aria-label="Filter by type"
              value={type}
              onChange={(event) => {
                setLoading(true)
                setLoadError('')
                setType(event.target.value)
                setPageIndex(0)
              }}
            >
              <option value="">All types</option>
              <option value={ALERT_TYPE}>Key reuse mismatch</option>
            </select>
          </label>
          <label>
            <span>Status</span>
            <select
              aria-label="Filter by status"
              value={status}
              onChange={(event) => {
                setLoading(true)
                setLoadError('')
                setStatus(event.target.value)
                setPageIndex(0)
              }}
            >
              <option value="">All statuses</option>
              <option value="OPEN">Open</option>
              <option value="REVIEWED">Reviewed</option>
              <option value="DISMISSED">Dismissed</option>
            </select>
          </label>
        </div>

        {loadError && <p className="error-message" role="alert">{loadError}</p>}
        {actionError && <p className="error-message" role="alert">{actionError}</p>}

        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th scope="col">Type</th>
                <th scope="col">Status</th>
                <th scope="col">Debtor account</th>
                <th scope="col">Creditor account</th>
                <th scope="col">Amount</th>
                <th scope="col">Created</th>
                <th scope="col"><span className="visually-hidden">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td className="table-message" colSpan={7}>Loading alerts…</td></tr>
              ) : loadError ? (
                <tr><td className="table-message" colSpan={7}>Alerts could not be loaded.</td></tr>
              ) : page?.content.length ? (
                page.content.map((alert) => (
                  <tr key={alert.id}>
                    <td>
                      <span className="type-label">{alert.type.replaceAll('_', ' ')}</span>
                    </td>
                    <td><span className={`status-badge status-${alert.status.toLowerCase()}`}>{alert.status}</span></td>
                    <td className="account">{alert.debtorAccount}</td>
                    <td className="account">{alert.creditorAccount}</td>
                    <td className="amount">{formatAmount(alert.amount, alert.currency)}</td>
                    <td className="date">{formatDate(alert.createdAt)}</td>
                    <td>
                      {alert.status === 'OPEN' && (
                        <div className="row-actions">
                          <button
                            type="button"
                            className="action-button review-button"
                            disabled={pendingId === alert.id}
                            onClick={() => reviewAlert(alert, 'REVIEWED')}
                          >
                            Mark reviewed
                          </button>
                          <button
                            type="button"
                            className="action-button dismiss-button"
                            disabled={pendingId === alert.id}
                            onClick={() => reviewAlert(alert, 'DISMISSED')}
                          >
                            Dismiss
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))
              ) : (
                <tr><td className="table-message" colSpan={7}>No alerts match these filters.</td></tr>
              )}
            </tbody>
          </table>
        </div>

        <footer className="pagination">
          <span>
            {page && page.totalElements > 0
              ? `Showing ${pageIndex * PAGE_SIZE + 1}–${Math.min((pageIndex + 1) * PAGE_SIZE, page.totalElements)} of ${page.totalElements}`
              : 'No alerts'}
          </span>
          <div className="pagination-actions">
            <button
              type="button"
              disabled={pageIndex === 0 || loading}
              onClick={() => {
                setLoading(true)
                setLoadError('')
                setPageIndex((current) => Math.max(0, current - 1))
              }}
            >
              Previous
            </button>
            <span>Page {pageIndex + 1}{totalPages > 0 ? ` of ${totalPages}` : ''}</span>
            <button
              type="button"
              disabled={loading || pageIndex + 1 >= totalPages}
              onClick={() => {
                setLoading(true)
                setLoadError('')
                setPageIndex((current) => current + 1)
              }}
            >
              Next
            </button>
          </div>
        </footer>
      </section>
    </main>
  )
}

export default App
