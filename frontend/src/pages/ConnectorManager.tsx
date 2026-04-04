import { useState, useEffect } from 'react'
import {
  RefreshCw,
  Plus,
  Database,
  Cloud,
  Snowflake,
  HardDrive,
  Wifi,
  Activity,
  Clock,
  Shield,
  Settings,
  Unplug,
  CheckCircle2,
} from 'lucide-react'
import type { DataSource } from '@/types'

const statusStyles: Record<string, { badge: string; label: string }> = {
  ACTIVE: { badge: 'bg-success/15 text-success', label: 'Active' },
  SYNCING: { badge: 'bg-tertiary/15 text-tertiary', label: 'Syncing' },
  OFFLINE: { badge: 'bg-surface-high text-on-surface-dim', label: 'Offline' },
}

function getStatus(c: DataSource): string {
  if (!c.isActive) return 'OFFLINE'
  return 'ACTIVE'
}

function getConnectorIcon(connectorType: string) {
  const t = connectorType.toUpperCase()
  if (t.includes('S3') || t.includes('CLOUD')) return Cloud
  if (t.includes('SNOW')) return Snowflake
  if (t.includes('FILE') || t.includes('CSV')) return HardDrive
  return Database
}

/* ---------- Mini Sparkline ---------- */
function MiniSparkline({ color = 'primary' }: { color?: string }) {
  const bars = [40, 65, 50, 80, 60, 90, 70, 85]
  return (
    <div className="flex items-end gap-1 h-8">
      {bars.map((h, i) => (
        <div
          key={i}
          className={`w-2 rounded-sm ${color === 'tertiary' ? 'bg-tertiary/40' : 'bg-primary/40'}`}
          style={{ height: `${h}%` }}
        />
      ))}
    </div>
  )
}

/* ---------- Stats Card ---------- */
function StatsCard({
  icon: Icon,
  label,
  value,
  detail,
  detailColor,
}: {
  icon: React.ElementType
  label: string
  value: string | number
  detail?: string
  detailColor?: string
}) {
  return (
    <div className="bg-surface-container rounded-xl p-5">
      <div className="flex items-center gap-2 mb-2">
        <Icon size={14} className="text-on-surface-dim" />
        <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold">
          {label}
        </p>
      </div>
      <p className="text-3xl font-display font-semibold text-on-surface">{value}</p>
      {detail && (
        <p className={`text-xs mt-1 ${detailColor ?? 'text-on-surface-dim'}`}>{detail}</p>
      )}
    </div>
  )
}

/* ---------- Source Card ---------- */
function SourceCard({
  connector,
  onConfigure,
  onDisconnect,
}: {
  connector: DataSource
  onConfigure: () => void
  onDisconnect: () => void
}) {
  const status = getStatus(connector)
  const st = statusStyles[status] ?? statusStyles.OFFLINE
  const ConnIcon = getConnectorIcon(connector.connectorType)

  return (
    <div className="glass-card rounded-xl p-6 flex flex-col">
      {/* Header */}
      <div className="flex items-start justify-between mb-4">
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-xl bg-accent/15">
            <ConnIcon size={18} className="text-accent" />
          </div>
          <div>
            <h3 className="text-sm font-semibold text-on-surface">
              {connector.displayName}
            </h3>
            <p className="text-xs text-on-surface-dim">{connector.connectorType}</p>
          </div>
        </div>
        <span
          className={`text-[10px] uppercase tracking-widest font-bold px-2 py-0.5 rounded-md ${st.badge}`}
        >
          {st.label}
        </span>
      </div>

      {/* Details */}
      <div className="space-y-2 mb-4 flex-1">
        <div className="flex justify-between text-xs">
          <span className="text-on-surface-dim">Protocol</span>
          <span className="text-on-surface">{connector.connectorType}</span>
        </div>
        <div className="flex justify-between text-xs">
          <span className="text-on-surface-dim">Last Sync</span>
          <span className="text-on-surface">
            {connector.lastSyncedAt
              ? new Date(connector.lastSyncedAt).toLocaleString()
              : 'Never'}
          </span>
        </div>
        <div className="flex justify-between text-xs">
          <span className="text-on-surface-dim">Encryption</span>
          <span className="text-on-surface">TLS 1.3</span>
        </div>
      </div>

      {/* Sparkline */}
      <div className="mb-4">
        <MiniSparkline color={status === 'SYNCING' ? 'tertiary' : 'primary'} />
      </div>

      {/* Footer */}
      <div className="flex items-center gap-2 pt-3 border-t border-outline-variant/10">
        <button
          onClick={onConfigure}
          className="flex items-center gap-1.5 px-3 py-1.5 text-xs bg-surface-high rounded-lg text-on-surface-dim hover:text-on-surface hover:bg-surface-highest transition-colors"
        >
          <Settings size={12} />
          Configure
        </button>
        <button
          onClick={onDisconnect}
          className="flex items-center gap-1.5 px-3 py-1.5 text-xs bg-surface-high rounded-lg text-on-surface-dim hover:text-error hover:bg-error/10 transition-colors"
        >
          <Unplug size={12} />
          Disconnect
        </button>
      </div>
    </div>
  )
}

/* ---------- Connect New Source CTA ---------- */
function AddSourceCTA({ onClick }: { onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className="rounded-xl border-2 border-dashed border-outline-variant/20 p-6 flex flex-col items-center justify-center gap-3 text-on-surface-dim hover:border-primary/30 hover:text-on-surface transition-colors min-h-64"
    >
      <div className="p-3 rounded-full bg-surface-container">
        <Plus size={24} />
      </div>
      <div className="text-center">
        <p className="text-sm font-medium">Connect New Source</p>
        <p className="text-xs mt-1">S3, SQL, NoSQL, or API</p>
      </div>
    </button>
  )
}

/* ---------- Create Connector Form ---------- */
function CreateConnectorForm({ onClose }: { onClose: () => void }) {
  const [form, setForm] = useState({
    apiName: '',
    displayName: '',
    connectorType: 'REST_API',
    targetObjectType: '',
    url: '',
    dataPath: '$',
    idField: 'id',
  })

  const handleSubmit = async () => {
    await fetch('/api/v1/connectors', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        apiName: form.apiName,
        displayName: form.displayName,
        connectorType: form.connectorType,
        targetObjectType: form.targetObjectType,
        config: { url: form.url, dataPath: form.dataPath, idField: form.idField },
        columnMapping: {},
        syncIntervalCron: '0 */15 * * * *',
      }),
    })
    onClose()
  }

  const inputClass =
    'w-full bg-surface-highest rounded-lg px-3 py-2.5 text-sm outline-none focus:ring-1 focus:ring-primary/30 text-on-surface placeholder:text-on-surface-dim'

  return (
    <div className="bg-surface-container rounded-xl p-6 mb-6 max-w-lg">
      <h3 className="text-sm font-display font-semibold mb-4 text-on-surface">
        New Data Source
      </h3>
      <div className="space-y-3">
        <input
          className={inputClass}
          placeholder="API Name"
          value={form.apiName}
          onChange={(e) => setForm({ ...form, apiName: e.target.value })}
        />
        <input
          className={inputClass}
          placeholder="Display Name"
          value={form.displayName}
          onChange={(e) => setForm({ ...form, displayName: e.target.value })}
        />
        <select
          className={inputClass}
          value={form.connectorType}
          onChange={(e) => setForm({ ...form, connectorType: e.target.value })}
        >
          <option value="REST_API">REST API</option>
          <option value="JDBC">JDBC</option>
          <option value="CSV">CSV</option>
        </select>
        <input
          className={inputClass}
          placeholder="Target Object Type (e.g. Customer)"
          value={form.targetObjectType}
          onChange={(e) => setForm({ ...form, targetObjectType: e.target.value })}
        />
        <input
          className={inputClass}
          placeholder="URL"
          value={form.url}
          onChange={(e) => setForm({ ...form, url: e.target.value })}
        />
        <div className="flex gap-2 pt-1">
          <button
            onClick={handleSubmit}
            className="px-4 py-2 bg-primary text-on-primary text-sm font-bold rounded-lg hover:brightness-110 transition-all"
          >
            Create
          </button>
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm text-on-surface-dim hover:text-on-surface transition-colors"
          >
            Cancel
          </button>
        </div>
      </div>
    </div>
  )
}

/* ---------- Main Page ---------- */
export function ConnectorManager() {
  const [connectors, setConnectors] = useState<DataSource[]>([])
  const [creating, setCreating] = useState(false)

  const fetchConnectors = async () => {
    try {
      const res = await fetch('/api/v1/connectors')
      setConnectors(await res.json())
    } catch {
      /* empty */
    }
  }

  useEffect(() => {
    fetchConnectors()
  }, [])

  const deleteConnector = async (id: string) => {
    await fetch(`/api/v1/connectors/${id}`, { method: 'DELETE' })
    fetchConnectors()
  }

  const activeCount = connectors.filter((c) => c.isActive).length

  return (
    <div className="p-8 max-w-7xl">
      {/* Header */}
      <div className="flex items-end justify-between mb-6">
        <div>
          <h1 className="text-4xl font-display font-extrabold tracking-tight text-on-surface mb-1">
            Connected Data Sources
          </h1>
          <p className="text-on-surface-dim">
            Manage ingestion pipelines, monitor sync health, and configure connectors
          </p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={fetchConnectors}
            className="flex items-center gap-2 px-4 py-2.5 bg-surface-container text-on-surface-dim text-sm font-medium rounded-lg hover:bg-surface-high transition-colors"
          >
            <RefreshCw size={15} />
            Refresh All
          </button>
          <button
            onClick={() => setCreating(true)}
            className="flex items-center gap-2 px-4 py-2.5 bg-primary text-on-primary text-sm font-bold rounded-lg hover:brightness-110 transition-all"
          >
            <Plus size={15} />
            Add New Source
          </button>
        </div>
      </div>

      {/* Stats Bar */}
      <div className="grid grid-cols-4 gap-4 mb-8">
        <StatsCard icon={Database} label="Total Sources" value={connectors.length} />
        <StatsCard
          icon={Wifi}
          label="Active Streams"
          value={activeCount}
          detail="Nominal"
          detailColor="text-success"
        />
        <StatsCard
          icon={Activity}
          label="Ingestion Rate"
          value="1.2 GB/s"
          detail="Avg. throughput"
        />
        <StatsCard
          icon={Clock}
          label="Global Latency"
          value="42ms"
          detail="p95 response"
        />
      </div>

      {/* Create Form */}
      {creating && (
        <CreateConnectorForm
          onClose={() => {
            setCreating(false)
            fetchConnectors()
          }}
        />
      )}

      {/* Source Cards Grid */}
      <div className="grid grid-cols-3 gap-6 mb-10">
        {connectors.map((c) => (
          <SourceCard
            key={c.id}
            connector={c}
            onConfigure={() => {}}
            onDisconnect={() => deleteConnector(c.id)}
          />
        ))}
        <AddSourceCTA onClick={() => setCreating(true)} />
      </div>

      {/* Bottom Section */}
      <div className="grid grid-cols-12 gap-6">
        {/* Data Flow Intelligence - col-span-8 */}
        <div className="col-span-8 bg-surface-container rounded-xl p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-display font-semibold text-on-surface">
              Data Flow Intelligence
            </h2>
            <div className="flex items-center gap-4 text-xs text-on-surface-dim">
              <div className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-sm bg-primary/40" />
                Ingested
              </div>
              <div className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-sm bg-tertiary/40" />
                Processed
              </div>
            </div>
          </div>
          <div className="flex items-end gap-2 h-36">
            {[35, 55, 45, 70, 60, 80, 50, 90, 65, 75, 85, 40].map((h, i) => (
              <div key={i} className="flex-1 flex flex-col items-center gap-1">
                <div className="w-full flex gap-0.5">
                  <div
                    className="flex-1 rounded-sm bg-primary/30 hover:bg-primary/50 transition-colors"
                    style={{ height: `${h * 1.4}%` }}
                  />
                  <div
                    className="flex-1 rounded-sm bg-tertiary/30 hover:bg-tertiary/50 transition-colors"
                    style={{ height: `${h * 1.1}%` }}
                  />
                </div>
                <span className="text-[9px] text-on-surface-dim">
                  {String(i + 1).padStart(2, '0')}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Security Overview - col-span-4 */}
        <div className="col-span-4 bg-surface-container rounded-xl p-6">
          <h2 className="text-sm font-display font-semibold text-on-surface mb-4">
            Security Overview
          </h2>
          <div className="space-y-3">
            <div className="flex items-center justify-between p-3 rounded-lg bg-surface-low">
              <div className="flex items-center gap-3">
                <CheckCircle2 size={15} className="text-success" />
                <span className="text-sm text-on-surface">TLS Encryption</span>
              </div>
              <span className="text-xs text-success">Secured</span>
            </div>
            <div className="flex items-center justify-between p-3 rounded-lg bg-surface-low">
              <div className="flex items-center gap-3">
                <CheckCircle2 size={15} className="text-success" />
                <span className="text-sm text-on-surface">Auth Tokens</span>
              </div>
              <span className="text-xs text-success">Valid</span>
            </div>
            <div className="flex items-center justify-between p-3 rounded-lg bg-surface-low">
              <div className="flex items-center gap-3">
                <Shield size={15} className="text-warning" />
                <span className="text-sm text-on-surface">Cert Expiry</span>
              </div>
              <span className="text-xs text-warning">23d remaining</span>
            </div>
          </div>

          {/* Automated Optimization card */}
          <div className="mt-4 p-4 rounded-lg bg-primary-container/50">
            <p className="text-xs font-bold text-primary mb-1">Automated Optimization</p>
            <p className="text-xs text-on-surface-dim">
              AI-driven pipeline tuning is active and monitoring throughput.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
