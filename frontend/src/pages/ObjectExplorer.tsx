import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@apollo/client'
import { SEARCH_OBJECTS, GET_ALL_OBJECT_TYPES } from '@/api/graphql/queries'
import {
  X,
  ChevronDown,
  SlidersHorizontal,
  Download,
  Boxes,
  Activity,
  FileText,
  Share2,
  Edit3,
} from 'lucide-react'
import type { OntologyObject, ObjectTypeSchema } from '@/types'

/* ---------- Right Drawer ---------- */
function ObjectDetailDrawer({
  object,
  onClose,
}: {
  object: OntologyObject
  onClose: () => void
}) {
  const [activeTab, setActiveTab] = useState<'properties' | 'linked' | 'audit'>('properties')
  const props =
    typeof object.properties === 'string'
      ? JSON.parse(object.properties)
      : object.properties

  const tabs = [
    { key: 'properties' as const, label: 'Properties' },
    { key: 'linked' as const, label: 'Linked Objects' },
    { key: 'audit' as const, label: 'Audit Log' },
  ]

  return (
    <div className="w-105 bg-surface-low overflow-y-auto flex flex-col shrink-0">
      {/* Entity Header */}
      <div className="p-6">
        <div className="flex items-start justify-between mb-3">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-accent/15">
              <Boxes size={18} className="text-accent" />
            </div>
            <div>
              <h2 className="text-base font-display font-semibold text-on-surface">
                {object.objectType}
              </h2>
              <p className="text-xs text-on-surface-dim font-mono">{object.id.slice(0, 16)}...</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg hover:bg-surface-high transition-colors text-on-surface-dim"
          >
            <X size={16} />
          </button>
        </div>

        {/* Object Health Donut */}
        <div className="flex items-center gap-4 mt-5 p-4 bg-surface-container rounded-xl">
          <div className="relative w-16 h-16 shrink-0">
            <svg viewBox="0 0 36 36" className="w-16 h-16 -rotate-90">
              <circle
                cx="18"
                cy="18"
                r="15.5"
                fill="none"
                stroke="currentColor"
                strokeWidth="3"
                className="text-surface-high"
              />
              <circle
                cx="18"
                cy="18"
                r="15.5"
                fill="none"
                stroke="currentColor"
                strokeWidth="3"
                strokeDasharray="91.7 100"
                strokeLinecap="round"
                className="text-success"
              />
            </svg>
            <span className="absolute inset-0 flex items-center justify-center text-sm font-bold text-on-surface">
              94%
            </span>
          </div>
          <div>
            <p className="text-xs text-on-surface-dim mb-0.5">Object Health</p>
            <p className="text-lg font-display font-bold text-success">Excellent</p>
          </div>
        </div>

        {/* Relational Nodes */}
        <div className="mt-5">
          <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold mb-3">
            Relational Nodes
          </p>
          <div className="flex gap-2">
            {['AB', 'CD', 'EF'].map((initials) => (
              <div
                key={initials}
                className="w-9 h-9 rounded-full bg-primary-container flex items-center justify-center text-xs font-semibold text-primary"
              >
                {initials}
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex px-6">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`flex-1 py-3 text-xs font-medium transition-colors ${
              activeTab === tab.key
                ? 'text-on-surface border-b-2 border-primary'
                : 'text-on-surface-dim hover:text-on-surface border-b-2 border-transparent'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Tab Content */}
      <div className="flex-1 p-6">
        {activeTab === 'properties' && (
          <div className="space-y-3">
            <div className="bg-surface-container rounded-lg p-3">
              <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold mb-1">
                ID
              </p>
              <p className="text-sm font-mono text-on-surface break-all">{object.id}</p>
            </div>
            <div className="bg-surface-container rounded-lg p-3">
              <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold mb-1">
                Type
              </p>
              <p className="text-sm text-on-surface">{object.objectType}</p>
            </div>
            <div className="bg-surface-container rounded-lg p-3">
              <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold mb-1">
                Created
              </p>
              <p className="text-sm text-on-surface">
                {object.createdAt ? new Date(object.createdAt).toLocaleString() : '-'}
              </p>
            </div>
            {Object.entries(props).map(([key, value]) => (
              <div key={key} className="bg-surface-container rounded-lg p-3">
                <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold mb-1">
                  {key}
                </p>
                <p className="text-sm text-on-surface wrap-break-word">
                  {typeof value === 'object' ? JSON.stringify(value) : String(value)}
                </p>
              </div>
            ))}
          </div>
        )}
        {activeTab === 'linked' && (
          <div className="flex flex-col items-center justify-center py-10 text-on-surface-dim">
            <Activity size={24} className="mb-2 opacity-40" />
            <p className="text-sm">No linked objects found</p>
          </div>
        )}
        {activeTab === 'audit' && (
          <div className="flex flex-col items-center justify-center py-10 text-on-surface-dim">
            <FileText size={24} className="mb-2 opacity-40" />
            <p className="text-sm">No audit entries</p>
          </div>
        )}
      </div>

      {/* Footer Buttons */}
      <div className="p-6 pt-0 flex gap-3">
        <button className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 bg-primary text-on-primary text-sm font-bold rounded-lg hover:brightness-110 transition-all">
          <Edit3 size={14} />
          Edit Entity
        </button>
        <button className="p-2.5 bg-surface-high rounded-lg text-on-surface-dim hover:text-on-surface hover:bg-surface-highest transition-colors">
          <Share2 size={16} />
        </button>
      </div>
    </div>
  )
}

/* ---------- Main Page ---------- */
export function ObjectExplorer() {
  const { objectType } = useParams()
  const navigate = useNavigate()
  const [selectedObject, setSelectedObject] = useState<OntologyObject | null>(null)
  const [typeMenuOpen, setTypeMenuOpen] = useState(false)

  const { data: otData } = useQuery(GET_ALL_OBJECT_TYPES)
  const objectTypes: ObjectTypeSchema[] = otData?.getAllObjectTypes ?? []

  const { data, loading } = useQuery(SEARCH_OBJECTS, {
    variables: { objectType: objectType ?? '', pagination: { limit: 50 } },
    skip: !objectType,
  })

  const objects: OntologyObject[] = data?.searchObjects?.items ?? []
  const totalCount = data?.searchObjects?.totalCount ?? 0

  const currentType = objectTypes.find((ot) => ot.apiName === objectType)
  const propertyNames =
    currentType?.properties.map((p) => p.apiName) ??
    Object.keys(objects[0]?.properties ?? {})

  return (
    <div className="flex h-[calc(100vh-4rem)]">
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Header */}
        <div className="px-8 pt-6 pb-5">
          <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold mb-2">
            Workspace &gt; Object Explorer
          </p>
          <div className="flex items-end justify-between">
            <div>
              <h1 className="text-3xl font-display font-extrabold tracking-tight text-on-surface mb-1">
                {currentType?.displayName ?? 'Object Explorer'}
              </h1>
              <p className="text-on-surface-dim text-sm">
                Browse, filter, and inspect ontology objects across all types
              </p>
            </div>
            <div className="flex items-center gap-3">
              <button className="flex items-center gap-2 px-4 py-2.5 bg-surface-container text-on-surface-dim text-sm font-medium rounded-lg hover:bg-surface-high transition-colors">
                <SlidersHorizontal size={15} />
                Advanced Filters
              </button>
              <button className="flex items-center gap-2 px-4 py-2.5 bg-surface-container text-on-surface-dim text-sm font-medium rounded-lg hover:bg-surface-high transition-colors">
                <Download size={15} />
                Export CSV
              </button>
            </div>
          </div>
        </div>

        {/* Filter Bar */}
        <div className="px-8 pb-4">
          <div className="grid grid-cols-4 gap-4">
            {/* Object Type Dropdown */}
            <div className="relative">
              <button
                onClick={() => setTypeMenuOpen(!typeMenuOpen)}
                className="w-full flex items-center justify-between bg-surface-container px-3 py-2.5 rounded-lg text-sm hover:bg-surface-high transition-colors"
              >
                <span className={objectType ? 'text-on-surface' : 'text-on-surface-dim'}>
                  {objectType || 'Select Type'}
                </span>
                <ChevronDown size={14} className="text-on-surface-dim" />
              </button>
              {typeMenuOpen && (
                <div className="absolute top-full left-0 mt-1 w-full bg-surface-bright rounded-lg shadow-lg z-10 py-1">
                  {objectTypes.map((ot) => (
                    <button
                      key={ot.apiName}
                      onClick={() => {
                        navigate(`/objects/${ot.apiName}`)
                        setTypeMenuOpen(false)
                      }}
                      className="w-full text-left px-3 py-2 text-sm hover:bg-surface-high transition-colors text-on-surface"
                    >
                      {ot.displayName}{' '}
                      <span className="text-on-surface-dim">({ot.apiName})</span>
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* Flow Status */}
            <div className="flex items-center gap-2 px-3 py-2.5 bg-surface-container rounded-lg">
              <span className="text-sm text-on-surface-dim">Status:</span>
              <div className="flex gap-1.5">
                <span className="text-[10px] uppercase tracking-widest font-bold px-2 py-0.5 rounded-md bg-tertiary/15 text-tertiary">Transit</span>
                <span className="text-[10px] uppercase tracking-widest font-bold px-2 py-0.5 rounded-md bg-error/15 text-error">Pending</span>
              </div>
            </div>

            {/* Date Range */}
            <div className="flex items-center gap-2 px-3 py-2.5 bg-surface-container rounded-lg">
              <span className="text-sm text-on-surface-dim">Date: All Time</span>
            </div>

            {/* Metric Threshold */}
            <div className="flex items-center gap-2 px-3 py-2.5 bg-surface-container rounded-lg">
              <span className="text-sm text-on-surface-dim">Metric: Any</span>
            </div>
          </div>
        </div>

        {/* Data Table */}
        {objectType ? (
          <div className="flex-1 overflow-auto mx-8 mb-6 rounded-xl">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-surface-container">
                  <th className="text-left px-4 py-3 text-[10px] font-bold text-on-surface-dim uppercase tracking-widest">
                    UID
                  </th>
                  {propertyNames.slice(0, 4).map((name) => (
                    <th
                      key={name}
                      className="text-left px-4 py-3 text-[10px] font-bold text-on-surface-dim uppercase tracking-widest"
                    >
                      {name}
                    </th>
                  ))}
                  <th className="text-left px-4 py-3 text-[10px] font-bold text-on-surface-dim uppercase tracking-widest">
                    Timestamp
                  </th>
                  <th className="text-left px-4 py-3 text-[10px] font-bold text-on-surface-dim uppercase tracking-widest">
                    Status
                  </th>
                </tr>
              </thead>
              <tbody>
                {objects.map((obj) => {
                  const p =
                    typeof obj.properties === 'string'
                      ? JSON.parse(obj.properties)
                      : obj.properties
                  return (
                    <tr
                      key={obj.id}
                      onClick={() => setSelectedObject(obj)}
                      className={`cursor-pointer transition-colors ${
                        selectedObject?.id === obj.id
                          ? 'bg-surface-high'
                          : 'hover:bg-surface-high'
                      }`}
                    >
                      <td className="px-4 py-3 font-mono text-xs text-on-surface-dim">
                        {obj.id.slice(0, 8)}...
                      </td>
                      {propertyNames.slice(0, 4).map((name) => (
                        <td
                          key={name}
                          className="px-4 py-3 max-w-48 truncate text-on-surface"
                        >
                          {typeof p[name] === 'object'
                            ? JSON.stringify(p[name]).slice(0, 50)
                            : String(p[name] ?? '')}
                        </td>
                      ))}
                      <td className="px-4 py-3 text-xs text-on-surface-dim">
                        {obj.createdAt ? new Date(obj.createdAt).toLocaleString() : '-'}
                      </td>
                      <td className="px-4 py-3">
                        <span className="text-[10px] uppercase tracking-widest font-bold px-2 py-0.5 rounded-md bg-success/10 text-success">
                          Active
                        </span>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
            {loading && (
              <div className="p-8 text-center text-on-surface-dim">Loading...</div>
            )}
            {!loading && objects.length === 0 && (
              <div className="p-8 text-center text-on-surface-dim">
                No objects found for {objectType}
              </div>
            )}
            {!loading && objects.length > 0 && (
              <div className="px-4 py-3 bg-surface-container text-xs text-on-surface-dim">
                Showing {objects.length} of {totalCount} objects
              </div>
            )}
          </div>
        ) : (
          <div className="flex-1 flex items-center justify-center text-on-surface-dim">
            <p>Select an Object Type to explore</p>
          </div>
        )}
      </div>

      {/* Right Drawer */}
      {selectedObject && (
        <ObjectDetailDrawer
          object={selectedObject}
          onClose={() => setSelectedObject(null)}
        />
      )}
    </div>
  )
}
