import { useState } from 'react'
import { useQuery } from '@apollo/client'
import { GET_ACTION_LOG, GET_ACTION_TYPES } from '@/api/graphql/queries'
import {
  Activity,
  ChevronDown,
  ChevronRight,
  AlertTriangle,
  Download,
  RotateCcw,
  Copy,
  Zap,
  Edit3,
  Trash2,
  PlusCircle,
  ChevronLeft,
} from 'lucide-react'
import type { ActionLogEntry } from '@/types'

const actionIconStyles: Record<string, string> = {
  MODIFY: 'bg-tertiary/15 text-tertiary',
  CREATE: 'bg-success/15 text-success',
  DELETE: 'bg-error/15 text-error',
}

function getActionIcon(actionType: string) {
  const upper = actionType.toUpperCase()
  if (upper.includes('CREATE') || upper.includes('ADD')) return { icon: PlusCircle, style: actionIconStyles.CREATE }
  if (upper.includes('DELETE') || upper.includes('REMOVE')) return { icon: Trash2, style: actionIconStyles.DELETE }
  return { icon: Edit3, style: actionIconStyles.MODIFY }
}

/* ---------- JSON Diff ---------- */
function JsonDiffPanel({
  beforeState,
  afterState,
}: {
  beforeState?: Record<string, unknown>
  afterState?: Record<string, unknown>
}) {
  const [open, setOpen] = useState(false)
  if (!beforeState && !afterState) return null

  return (
    <div className="mt-3">
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center gap-1.5 text-xs text-on-surface-dim hover:text-on-surface transition-colors"
      >
        {open ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
        View Changes
      </button>
      {open && (
        <div className="grid grid-cols-2 gap-3 mt-2">
          <div>
            <p className="text-[10px] uppercase tracking-widest text-on-surface-dim font-bold mb-1">
              Before
            </p>
            <pre className="p-3 bg-surface-dim rounded-lg text-xs overflow-x-auto text-on-surface-dim font-mono leading-relaxed">
              {beforeState ? JSON.stringify(beforeState, null, 2) : '(empty)'}
            </pre>
          </div>
          <div>
            <p className="text-[10px] uppercase tracking-widest text-on-surface-dim font-bold mb-1">
              After
            </p>
            <pre className="p-3 bg-surface-dim rounded-lg text-xs overflow-x-auto text-success/80 font-mono leading-relaxed">
              {afterState ? JSON.stringify(afterState, null, 2) : '(empty)'}
            </pre>
          </div>
        </div>
      )}
    </div>
  )
}

/* ---------- Timeline Entry ---------- */
function TimelineEntry({ log }: { log: ActionLogEntry }) {
  const { icon: ActionIcon, style: iconStyle } = getActionIcon(log.actionType)

  return (
    <div className="bg-surface-container rounded-xl border border-outline-variant/10 p-5">
      {/* Header */}
      <div className="flex items-start justify-between mb-3">
        <div className="flex items-center gap-3">
          <div className={`p-2 rounded-full ${iconStyle}`}>
            <ActionIcon size={15} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <p className="text-sm font-semibold text-on-surface">{log.actionType}</p>
              <span
                className={`text-[10px] uppercase tracking-widest font-bold px-2 py-0.5 rounded-md ${
                  log.status === 'SUCCESS'
                    ? 'bg-success/10 text-success'
                    : log.status === 'FAILED'
                      ? 'bg-error/10 text-error'
                      : 'bg-warning/10 text-warning'
                }`}
              >
                {log.status}
              </span>
            </div>
            <p className="text-xs text-on-surface-dim mt-0.5">
              by {log.performedBy} -- {new Date(log.performedAt).toLocaleString()}
            </p>
          </div>
        </div>
      </div>

      {/* Metadata Chips */}
      <div className="flex items-center gap-2 mb-2 flex-wrap">
        {log.objectId && (
          <span className="text-xs bg-surface-high px-2.5 py-1 rounded-md font-mono text-on-surface-dim">
            {log.objectId.slice(0, 12)}...
          </span>
        )}
        <span className="text-xs bg-surface-high px-2.5 py-1 rounded-md text-on-surface-dim">
          Latency: 42ms
        </span>
        <span className="text-xs bg-surface-high px-2.5 py-1 rounded-md text-on-surface-dim">
          v1.0
        </span>
        <span className={`text-xs px-2.5 py-1 rounded-md ${
          log.status === 'SUCCESS' ? 'bg-success/10 text-success' : 'bg-error/10 text-error'
        }`}>
          {log.status === 'SUCCESS' ? 'Low Impact' : 'High Impact'}
        </span>
      </div>

      {/* JSON Diff */}
      <JsonDiffPanel beforeState={log.beforeState} afterState={log.afterState} />

      {/* Action buttons */}
      <div className="flex items-center gap-2 mt-4 pt-3 border-t border-outline-variant/10">
        <button className="flex items-center gap-1.5 px-3 py-1.5 text-xs bg-surface-high rounded-lg text-on-surface-dim hover:text-on-surface hover:bg-surface-highest transition-colors">
          <RotateCcw size={12} />
          Rollback Version
        </button>
        <button className="flex items-center gap-1.5 px-3 py-1.5 text-xs bg-surface-high rounded-lg text-on-surface-dim hover:text-on-surface hover:bg-surface-highest transition-colors">
          <Copy size={12} />
          Copy Raw JSON
        </button>
      </div>
    </div>
  )
}

/* ---------- Main Page ---------- */
export function ActionCenter() {
  const [limit] = useState(50)
  const [page, setPage] = useState(1)
  const perPage = 15
  const { data, loading } = useQuery(GET_ACTION_LOG, { variables: { limit } })
  const { data: atData } = useQuery(GET_ACTION_TYPES)

  const logs: ActionLogEntry[] = data?.getActionLog ?? []
  const failedCount = logs.filter((l) => l.status === 'FAILED').length

  const totalPages = Math.max(1, Math.ceil(logs.length / perPage))
  const pagedLogs = logs.slice((page - 1) * perPage, page * perPage)

  return (
    <div className="p-8 max-w-5xl">
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-3xl font-display font-extrabold tracking-tight text-on-surface mb-1">
          Action Center
        </h1>
        <p className="text-on-surface-dim">
          Audit trail, governance monitor, and action timeline
        </p>
      </div>

      {/* Stats Row */}
      <div className="grid grid-cols-2 gap-4 mb-6">
        <div className="bg-surface-container rounded-xl p-5">
          <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold mb-2">
            Actions (24h)
          </p>
          <p className="text-3xl font-display font-semibold text-on-surface">
            {logs.length.toLocaleString()}
          </p>
          <p className="text-xs text-success mt-1">{logs.length - failedCount} successful</p>
        </div>
        <div className="bg-surface-container rounded-xl p-5">
          <div className="flex items-center gap-2 mb-2">
            <AlertTriangle size={14} className="text-error" />
            <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold">
              Anomalies
            </p>
          </div>
          <p className="text-3xl font-display font-semibold text-on-surface">
            {failedCount}
          </p>
          <p className="text-xs text-error mt-1">
            {failedCount > 0 ? 'Requires attention' : 'No issues'}
          </p>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="flex items-center gap-3 mb-6">
        <button className="flex items-center gap-2 px-3 py-2.5 bg-surface-container rounded-lg text-sm text-on-surface-dim hover:bg-surface-high transition-colors">
          All Users
          <ChevronDown size={14} />
        </button>
        <button className="flex items-center gap-2 px-3 py-2.5 bg-surface-container rounded-lg text-sm text-on-surface-dim hover:bg-surface-high transition-colors">
          All Action Types
          {atData?.getActionTypes && (
            <span className="text-xs ml-1">({atData.getActionTypes.length})</span>
          )}
          <ChevronDown size={14} />
        </button>
        <button className="flex items-center gap-2 px-3 py-2.5 bg-surface-container rounded-lg text-sm text-on-surface-dim hover:bg-surface-high transition-colors">
          Last 7 Days
          <ChevronDown size={14} />
        </button>
        <div className="ml-auto">
          <button className="flex items-center gap-2 px-4 py-2.5 bg-primary text-on-primary text-sm font-bold rounded-lg hover:brightness-110 transition-all">
            <Download size={15} />
            Export Audit
          </button>
        </div>
      </div>

      {/* Timeline */}
      <div className="space-y-4">
        {pagedLogs.map((log) => (
          <TimelineEntry key={log.id} log={log} />
        ))}

        {loading && (
          <div className="p-8 text-center text-on-surface-dim">Loading...</div>
        )}
        {!loading && logs.length === 0 && (
          <div className="text-center py-16 text-on-surface-dim">
            <Zap size={48} className="mx-auto mb-4 opacity-20" />
            <p className="text-lg font-medium mb-1">No actions recorded yet</p>
            <p className="text-sm">Actions will appear here as they occur</p>
          </div>
        )}
      </div>

      {/* Pagination */}
      {logs.length > 0 && (
        <div className="flex items-center justify-between mt-6 pt-4">
          <p className="text-xs text-on-surface-dim">
            Showing {(page - 1) * perPage + 1}-{Math.min(page * perPage, logs.length)} of {logs.length.toLocaleString()} actions
          </p>
          <div className="flex items-center gap-1">
            <button
              onClick={() => setPage(Math.max(1, page - 1))}
              disabled={page === 1}
              className="p-1.5 rounded-lg bg-surface-container text-on-surface-dim hover:bg-surface-high disabled:opacity-30 transition-colors"
            >
              <ChevronLeft size={14} />
            </button>
            {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => i + 1).map((p) => (
              <button
                key={p}
                onClick={() => setPage(p)}
                className={`w-8 h-8 rounded-lg text-xs font-medium transition-colors ${
                  page === p
                    ? 'bg-primary text-on-primary'
                    : 'bg-surface-container text-on-surface-dim hover:bg-surface-high'
                }`}
              >
                {p}
              </button>
            ))}
            <button
              onClick={() => setPage(Math.min(totalPages, page + 1))}
              disabled={page === totalPages}
              className="p-1.5 rounded-lg bg-surface-container text-on-surface-dim hover:bg-surface-high disabled:opacity-30 transition-colors"
            >
              <ChevronRight size={14} />
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
