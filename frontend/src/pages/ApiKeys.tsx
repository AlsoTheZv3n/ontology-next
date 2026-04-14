import { useEffect, useState } from 'react'
import { Copy, KeyRound, Trash2, Plus, AlertCircle, Check } from 'lucide-react'

type ApiKey = {
  id: string
  name: string
  prefix?: string
  scopes?: string[]
  createdAt?: string
  lastUsedAt?: string | null
}

type CreatedKey = { id: string; rawKey: string; name: string }

const AVAILABLE_SCOPES = [
  { id: 'read', label: 'Read objects, types, links' },
  { id: 'write', label: 'Create and update objects' },
  { id: 'admin', label: 'Admin operations (tenant-wide)' },
]

/**
 * API Keys management page. Lists the tenant's keys and hands the plaintext
 * of newly-minted keys to the user exactly once. The backend never returns
 * the raw value after creation, so this is the only safe point to copy it.
 */
export function ApiKeys() {
  const [keys, setKeys] = useState<ApiKey[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [showCreate, setShowCreate] = useState(false)
  const [newName, setNewName] = useState('')
  const [selectedScopes, setSelectedScopes] = useState<string[]>(['read'])
  const [creating, setCreating] = useState(false)
  const [justCreated, setJustCreated] = useState<CreatedKey | null>(null)
  const [copied, setCopied] = useState(false)

  const load = async () => {
    setLoading(true)
    setError(null)
    try {
      const resp = await fetch('/api/v1/api-keys')
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
      const data = await resp.json()
      setKeys(Array.isArray(data) ? data : [])
    } catch (e: any) {
      setError(e.message ?? 'Failed to load API keys')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const toggleScope = (id: string) =>
    setSelectedScopes(prev =>
      prev.includes(id) ? prev.filter(s => s !== id) : [...prev, id],
    )

  const submitCreate = async () => {
    if (!newName.trim()) return
    setCreating(true)
    setError(null)
    try {
      const resp = await fetch('/api/v1/api-keys', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ name: newName.trim(), scopes: selectedScopes }),
      })
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
      const data = await resp.json()
      if (!data.rawKey) throw new Error('Backend did not return rawKey')
      setJustCreated({ id: data.id, rawKey: data.rawKey, name: newName.trim() })
      setShowCreate(false)
      setNewName('')
      setSelectedScopes(['read'])
      await load()
    } catch (e: any) {
      setError(e.message ?? 'Failed to create API key')
    } finally {
      setCreating(false)
    }
  }

  const revoke = async (id: string, name: string) => {
    if (!window.confirm(
      `Revoke "${name}"? Any client using this key will fail immediately.`
    )) return
    try {
      const resp = await fetch(`/api/v1/api-keys/${id}`, { method: 'DELETE' })
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
      await load()
    } catch (e: any) {
      setError(e.message ?? 'Failed to revoke key')
    }
  }

  const copyRawKey = async () => {
    if (!justCreated) return
    try {
      await navigator.clipboard.writeText(justCreated.rawKey)
      setCopied(true)
      setTimeout(() => setCopied(false), 2500)
    } catch {
      // Clipboard API unavailable (older browser / no HTTPS) — fall back to manual copy.
    }
  }

  return (
    <div className="p-8 max-w-4xl">
      <div className="mb-6 flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-display font-extrabold tracking-tight text-on-surface mb-1">
            API Keys
          </h1>
          <p className="text-sm text-on-surface-dim">
            Programmatic credentials for the REST and GraphQL APIs and the SDKs.
          </p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 px-3 py-2 rounded-lg bg-accent text-white text-sm font-medium hover:opacity-90"
        >
          <Plus size={14} /> Create Key
        </button>
      </div>

      {error && (
        <div className="mb-4 flex items-center gap-2 p-3 rounded-lg bg-red-50 text-red-700 text-sm">
          <AlertCircle size={14} />
          {error}
        </div>
      )}

      <div className="bg-surface-container rounded-xl overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-on-surface-dim text-sm">Loading…</div>
        ) : keys.length === 0 ? (
          <div className="p-10 text-center">
            <KeyRound size={28} className="mx-auto text-on-surface-dim mb-2" />
            <p className="text-sm text-on-surface-dim">No API keys yet.</p>
            <p className="text-xs text-on-surface-dim mt-1">
              Create your first key to authenticate SDK or CLI clients.
            </p>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-on-surface-dim border-b border-surface-bright">
                <th className="px-4 py-3 font-medium">Name</th>
                <th className="px-4 py-3 font-medium">Prefix</th>
                <th className="px-4 py-3 font-medium">Scopes</th>
                <th className="px-4 py-3 font-medium">Last used</th>
                <th className="px-4 py-3 font-medium w-12" />
              </tr>
            </thead>
            <tbody>
              {keys.map(k => (
                <tr key={k.id} className="border-b border-surface-bright last:border-0">
                  <td className="px-4 py-3 font-medium text-on-surface">{k.name}</td>
                  <td className="px-4 py-3 font-mono text-xs text-on-surface-dim">
                    {k.prefix ? `${k.prefix}…` : '—'}
                  </td>
                  <td className="px-4 py-3 text-on-surface-dim">
                    {(k.scopes ?? []).join(', ') || '—'}
                  </td>
                  <td className="px-4 py-3 text-on-surface-dim">
                    {k.lastUsedAt ? new Date(k.lastUsedAt).toLocaleString() : 'never'}
                  </td>
                  <td className="px-4 py-3">
                    <button
                      onClick={() => revoke(k.id, k.name)}
                      aria-label={`Revoke ${k.name}`}
                      className="p-1 rounded hover:bg-red-50 text-red-500"
                    >
                      <Trash2 size={14} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {showCreate && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-surface rounded-xl p-6 w-[26rem] max-w-[90vw]">
            <h2 className="text-lg font-semibold text-on-surface mb-4">Create API Key</h2>
            <label className="block text-xs text-on-surface-dim mb-1">Name</label>
            <input
              type="text"
              value={newName}
              onChange={e => setNewName(e.target.value)}
              placeholder="e.g. Nightly ETL"
              className="w-full px-3 py-2 rounded-lg bg-surface-bright text-on-surface text-sm mb-4"
              autoFocus
            />

            <div className="text-xs text-on-surface-dim mb-2">Scopes</div>
            <div className="space-y-2 mb-4">
              {AVAILABLE_SCOPES.map(s => (
                <label key={s.id} className="flex items-start gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={selectedScopes.includes(s.id)}
                    onChange={() => toggleScope(s.id)}
                    className="mt-0.5"
                  />
                  <div>
                    <div className="text-sm text-on-surface">{s.id}</div>
                    <div className="text-xs text-on-surface-dim">{s.label}</div>
                  </div>
                </label>
              ))}
            </div>

            <div className="flex justify-end gap-2">
              <button
                onClick={() => setShowCreate(false)}
                className="px-3 py-2 rounded-lg text-sm text-on-surface-dim hover:bg-surface-bright"
                disabled={creating}
              >
                Cancel
              </button>
              <button
                onClick={submitCreate}
                disabled={!newName.trim() || creating}
                className="px-3 py-2 rounded-lg bg-accent text-white text-sm font-medium hover:opacity-90 disabled:opacity-50"
              >
                {creating ? 'Creating…' : 'Create'}
              </button>
            </div>
          </div>
        </div>
      )}

      {justCreated && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-surface rounded-xl p-6 w-[32rem] max-w-[90vw]">
            <h2 className="text-lg font-semibold text-on-surface mb-1">
              Your new API key — save it now
            </h2>
            <p className="text-xs text-on-surface-dim mb-4">
              This is the only time the full key will be shown. Store it in your
              secret manager before closing this dialog.
            </p>
            <div className="p-3 rounded-lg bg-surface-bright font-mono text-sm text-on-surface break-all">
              {justCreated.rawKey}
            </div>
            <div className="flex justify-end gap-2 mt-4">
              <button
                onClick={copyRawKey}
                className="flex items-center gap-2 px-3 py-2 rounded-lg bg-surface-bright text-sm text-on-surface hover:bg-surface-container"
              >
                {copied ? <Check size={14} /> : <Copy size={14} />}
                {copied ? 'Copied' : 'Copy'}
              </button>
              <button
                onClick={() => { setJustCreated(null); setCopied(false) }}
                className="px-3 py-2 rounded-lg bg-accent text-white text-sm font-medium hover:opacity-90"
              >
                I've saved it
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
