import { useQuery } from '@apollo/client'
import { GET_ALL_OBJECT_TYPES, GET_ACTION_LOG } from '@/api/graphql/queries'
import { Boxes, Database, Activity, GitFork } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import type { ObjectTypeSchema, ActionLogEntry } from '@/types'

const iconStyles: Record<string, string> = {
  'Object Types': 'bg-accent/15 text-accent',
  Properties: 'bg-tertiary/15 text-tertiary',
  'Link Types': 'bg-primary/15 text-primary',
  'Recent Actions': 'bg-success/15 text-success',
}

function StatCard({
  icon: Icon,
  label,
  value,
  onClick,
}: {
  icon: React.ElementType
  label: string
  value: string | number
  onClick?: () => void
}) {
  return (
    <div
      onClick={onClick}
      className={`bg-surface-container rounded-xl p-6 ${
        onClick ? 'cursor-pointer hover:bg-surface-high transition-colors' : ''
      }`}
    >
      <div className="flex items-center gap-3 mb-4">
        <div className={`p-2.5 rounded-full ${iconStyles[label] ?? 'bg-surface-high text-on-surface-dim'}`}>
          <Icon size={18} />
        </div>
        <span className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold">
          {label}
        </span>
      </div>
      <p className="text-3xl font-display font-semibold tracking-tight text-on-surface">
        {value}
      </p>
    </div>
  )
}

export function Dashboard() {
  const { data: otData } = useQuery(GET_ALL_OBJECT_TYPES)
  const { data: logData } = useQuery(GET_ACTION_LOG, { variables: { limit: 10 } })
  const navigate = useNavigate()

  const objectTypes: ObjectTypeSchema[] = otData?.getAllObjectTypes ?? []
  const logs: ActionLogEntry[] = logData?.getActionLog ?? []
  const totalProps = objectTypes.reduce((sum, ot) => sum + ot.properties.length, 0)
  const totalLinks = objectTypes.reduce((sum, ot) => sum + ot.linkTypes.length, 0)

  return (
    <div className="p-8 max-w-6xl">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-display font-extrabold tracking-tight text-on-surface mb-1">
          Dashboard
        </h1>
        <p className="text-on-surface-dim">
          Ontology Engine overview and workspace metrics
        </p>
      </div>

      {/* Stat Cards */}
      <div className="grid grid-cols-4 gap-4 mb-8">
        <StatCard
          icon={Boxes}
          label="Object Types"
          value={objectTypes.length}
          onClick={() => navigate('/ontology')}
        />
        <StatCard icon={Database} label="Properties" value={totalProps} />
        <StatCard
          icon={GitFork}
          label="Link Types"
          value={totalLinks}
          onClick={() => navigate('/graph')}
        />
        <StatCard
          icon={Activity}
          label="Recent Actions"
          value={logs.length}
          onClick={() => navigate('/actions')}
        />
      </div>

      {/* Two-column detail */}
      <div className="grid grid-cols-2 gap-6">
        {/* Object Types List */}
        <div className="bg-surface-container rounded-xl p-6">
          <h2 className="text-lg font-display font-semibold mb-4 text-on-surface">
            Object Types
          </h2>
          <div className="space-y-2">
            {objectTypes.map((ot) => (
              <div
                key={ot.apiName}
                onClick={() => navigate(`/objects/${ot.apiName}`)}
                className="flex items-center justify-between p-3 rounded-lg bg-surface-low hover:bg-surface-high cursor-pointer transition-colors"
              >
                <div>
                  <p className="font-medium text-sm text-on-surface">{ot.displayName}</p>
                  <p className="text-xs text-on-surface-dim">{ot.apiName}</p>
                </div>
                <span className="text-xs bg-surface-high px-2.5 py-1 rounded-md text-on-surface-dim">
                  {ot.properties.length} props
                </span>
              </div>
            ))}
            {objectTypes.length === 0 && (
              <p className="text-sm text-on-surface-dim py-4">
                No object types yet. Start building your ontology.
              </p>
            )}
          </div>
        </div>

        {/* Recent Actions List */}
        <div className="bg-surface-container rounded-xl p-6">
          <h2 className="text-lg font-display font-semibold mb-4 text-on-surface">
            Recent Actions
          </h2>
          <div className="space-y-2">
            {logs.map((log) => (
              <div
                key={log.id}
                className="flex items-center justify-between p-3 rounded-lg bg-surface-low hover:bg-surface-high transition-colors"
              >
                <div>
                  <p className="font-medium text-sm text-on-surface">{log.actionType}</p>
                  <p className="text-xs text-on-surface-dim">{log.performedBy}</p>
                </div>
                <span
                  className={`text-[10px] uppercase tracking-widest font-bold px-2.5 py-1 rounded-md ${
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
            ))}
            {logs.length === 0 && (
              <p className="text-sm text-on-surface-dim py-4">No actions recorded yet.</p>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
