import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import App from './App'

const alert = {
  id: 'alert-123',
  type: 'KEY_REUSE_MISMATCH',
  status: 'OPEN',
  debtorAccount: 'XXXXXXXXXXXX4444',
  creditorAccount: 'XXXXXXXXXXXX8888',
  amount: 25.5,
  currency: 'USD',
  createdAt: '2026-11-14T06:30:00Z',
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('alert queue', () => {
  it('renders alerts and sends a dismiss PATCH with the requested status', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        content: [alert],
        page: 0,
        size: 10,
        totalElements: 1,
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    expect(await screen.findByText('XXXXXXXXXXXX4444')).toBeTruthy()
    await userEvent.click(screen.getByRole('button', { name: 'Dismiss' }))

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(2)
    })
    const [url, options] = fetchMock.mock.calls[1] as [string, RequestInit]
    expect(url).toBe('/api/alerts/alert-123')
    expect(options.method).toBe('PATCH')
    expect(JSON.parse(String(options.body))).toEqual({
      status: 'DISMISSED',
      note: 'Dismissed by investigator',
    })
    expect(new Headers(options.headers).get('X-User')).toBe('investigator')
    expect(await screen.findByText('DISMISSED')).toBeTruthy()
  })

  it('shows an error when an alert action fails', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        content: [alert],
        page: 0,
        size: 10,
        totalElements: 1,
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ error: 'Alert is no longer open' }), {
        status: 409,
        headers: { 'Content-Type': 'application/json' },
      }))
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)
    await userEvent.click(await screen.findByRole('button', { name: 'Mark reviewed' }))

    expect((await screen.findByRole('alert')).textContent).toContain('Alert is no longer open')
  })
})
