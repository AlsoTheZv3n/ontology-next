import { useState } from 'react'
import { useQuery } from '@apollo/client'
import { GET_ALL_OBJECT_TYPES } from '@/api/graphql/queries'
import { Plus, X, Key, Asterisk, Link2, Search as SearchIcon, ToggleLeft, ToggleRight } from 'lucide-react'
import type { ObjectTypeSchema, PropertyTypeSchema } from '@/types'

const typeColors: Record<string, string> = {
  ENTITY: 'bg-accent/15 text-accent',
  EVENT: 'bg-tertiary/15 text-tertiary',
}

function PropertyRow({ prop }: { prop: PropertyTypeSchema }) {
  return (
    <div className="flex items-center justify-between py-2 px-3 rounded-lg bg-surface-low hover:bg-surface-high transition-colors">
      <div className="flex items-center gap-2.5">
        <div className="flex items-center gap-1">
          {prop.isPrimaryKey && <Key size={13} className="text-tertiary" />}
          {prop.isRequired && <Asterisk size={13} className="text-accent" />}
        </div>
        <div>
          <p className="text-sm font-medium text-on-surface">{prop.displayName}</p>
          <p className="text-xs text-on-surface-dim font-mono">{prop.apiName}</p>
        </div>
      </div>
      <span className="text-xs bg-surface-high px-2 py-0.5 rounded-md text-on-surface-dim font-mono">
        {prop.dataType}
      </span>
    </div>
  )
}

function CreateObjectTypeForm({ onClose }: { onClose: () => void }) {
  const [apiName, setApiName] = useState('')
  const [displayName, setDisplayName] = useState('')

  const handleSubmit = async () => {
    await fetch('/api/v1/ontology/object-types', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ apiName, displayName, properties: [] }),
    })
    onClose()
    window.location.reload()
  }

  const inputClass =
    'w-full bg-surface-highest rounded-lg px-3 py-2.5 text-sm outline-none focus:ring-1 focus:ring-primary/30 text-on-surface placeholder:text-on-surface-dim'

  return (
    <div className="bg-surface-container rounded-xl p-5 mb-4">
      <h3 className="text-sm font-semibold mb-3 text-on-surface">New Object Type</h3>
      <div className="space-y-3">
        <input
          className={inputClass}
          placeholder="API Name (e.g. Customer)"
          value={apiName}
          onChange={(e) => setApiName(e.target.value)}
        />
        <input
          className={inputClass}
          placeholder="Display Name"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
        />
        <div className="flex gap-2">
          <button
            onClick={handleSubmit}
            className="px-4 py-2 bg-primary text-on-primary text-sm font-medium rounded-lg hover:brightness-110 transition-all"
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

export function OntologyBuilder() {
  const { data, loading } = useQuery(GET_ALL_OBJECT_TYPES)
  const [selectedType, setSelectedType] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const [detailName, setDetailName] = useState('')
  const [detailDesc, setDetailDesc] = useState('')
  const [elasticEnabled, setElasticEnabled] = useState(false)
  const [vectorEnabled, setVectorEnabled] = useState(false)

  const objectTypes: ObjectTypeSchema[] = data?.getAllObjectTypes ?? []
  const selected = objectTypes.find((ot) => ot.apiName === selectedType)

  const handleSelect = (apiName: string) => {
    setSelectedType(apiName)
    const ot = objectTypes.find((o) => o.apiName === apiName)
    if (ot) {
      setDetailName(ot.apiName)
      setDetailDesc(ot.description ?? '')
    }
  }

  return (
    <div className="flex h-[calc(100vh-4rem)]">
      {/* LEFT PANEL - Object Palette */}
      <div className="w-72 bg-surface-low flex flex-col overflow-y-auto shrink-0">
        <div className="p-5 pb-3">
          <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold mb-4">
            Object Palette
          </p>

          <button
            onClick={() => setCreating(true)}
            className="w-full flex items-center justify-center gap-2 px-4 py-2.5 bg-primary text-on-primary text-sm font-bold rounded-lg hover:brightness-110 transition-all mb-2"
          >
            <Plus size={15} />
            Add Object Type
          </button>
          <button className="w-full flex items-center justify-center gap-2 px-4 py-2.5 bg-tertiary/15 text-tertiary text-sm font-medium rounded-lg hover:bg-tertiary/25 transition-colors">
            <Link2 size={15} />
            Define Link Type
          </button>
        </div>

        {creating && (
          <div className="px-5">
            <CreateObjectTypeForm onClose={() => setCreating(false)} />
          </div>
        )}

        <div className="px-5 pt-4 flex-1">
          <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold mb-3">
            Draft Definitions
          </p>
          <div className="space-y-1">
            {objectTypes.map((ot, i) => (
              <div
                key={ot.apiName}
                onClick={() => handleSelect(ot.apiName)}
                className={`p-3 rounded-lg cursor-pointer transition-colors border-l-2 ${
                  selectedType === ot.apiName
                    ? 'bg-surface-high border-primary'
                    : 'border-transparent hover:bg-surface-container'
                }`}
              >
                <div className="flex items-center justify-between">
                  <p className="text-sm font-medium text-on-surface">{ot.displayName}</p>
                  <span
                    className={`text-[10px] uppercase tracking-widest font-bold px-1.5 py-0.5 rounded ${
                      i % 2 === 0 ? typeColors.ENTITY : typeColors.EVENT
                    }`}
                  >
                    {i % 2 === 0 ? 'Entity' : 'Event'}
                  </span>
                </div>
                <p className="text-xs text-on-surface-dim mt-0.5">
                  {ot.apiName} -- {ot.properties.length} props
                </p>
              </div>
            ))}
          </div>
          {loading && (
            <p className="text-sm text-on-surface-dim mt-4">Loading...</p>
          )}
        </div>
      </div>

      {/* CENTER - Canvas Area */}
      <div
        className="flex-1 overflow-auto relative"
        style={{
          backgroundImage:
            'radial-gradient(circle, rgba(255,255,255,0.04) 1px, transparent 1px)',
          backgroundSize: '32px 32px',
        }}
      >
        {objectTypes.length > 0 ? (
          <div className="p-10 flex flex-wrap gap-6">
            {objectTypes.map((ot, i) => (
              <div
                key={ot.apiName}
                onClick={() => handleSelect(ot.apiName)}
                className={`bg-surface-container rounded-xl border border-white/5 shadow-2xl p-5 w-64 cursor-pointer transition-all hover:border-primary/30 ${
                  selectedType === ot.apiName ? 'ring-1 ring-primary/40' : ''
                }`}
                style={{
                  transform: `translate(${(i % 3) * 20}px, ${Math.floor(i / 3) * 10}px)`,
                }}
              >
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <div className="p-1.5 rounded-lg bg-accent/15">
                      <SearchIcon size={13} className="text-accent" />
                    </div>
                    <h3 className="text-sm font-semibold text-on-surface">
                      {ot.displayName}
                    </h3>
                  </div>
                  <span className={`text-[10px] uppercase tracking-widest font-bold px-1.5 py-0.5 rounded ${
                    i % 2 === 0 ? 'bg-accent/15 text-accent' : 'bg-tertiary/15 text-tertiary'
                  }`}>
                    {i % 2 === 0 ? 'Entity' : 'Event'}
                  </span>
                </div>
                <div className="space-y-1">
                  {ot.properties.slice(0, 5).map((prop) => (
                    <div
                      key={prop.apiName}
                      className="flex items-center justify-between text-xs py-1 px-2 rounded bg-surface-low"
                    >
                      <span className="text-on-surface-variant font-mono">
                        {prop.apiName}
                      </span>
                      <span className="text-on-surface-dim font-mono">{prop.dataType}</span>
                    </div>
                  ))}
                  {ot.properties.length > 5 && (
                    <p className="text-xs text-on-surface-dim px-2">
                      +{ot.properties.length - 5} more
                    </p>
                  )}
                </div>
                {ot.linkTypes.length > 0 && (
                  <div className="mt-3 pt-2 border-t border-white/5">
                    {ot.linkTypes.map((lt) => (
                      <p key={lt.apiName} className="text-xs text-on-surface-dim">
                        <span className="text-tertiary">--</span> {lt.displayName}{' '}
                        <span className="text-on-surface-dim">
                          {lt.targetObjectType}
                        </span>
                      </p>
                    ))}
                  </div>
                )}
                <div className="mt-3 pt-2 border-t border-white/5">
                  <button className="w-full text-xs text-on-surface-dim hover:text-primary transition-colors py-1 flex items-center justify-center gap-1">
                    <Plus size={12} />
                    Add Property
                  </button>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="flex items-center justify-center h-full text-on-surface-dim">
            <p>Add your first Object Type to begin</p>
          </div>
        )}
      </div>

      {/* RIGHT PANEL - Object Details */}
      {selected && (
        <div className="w-80 bg-surface-low overflow-y-auto shrink-0">
          <div className="p-5">
            <div className="flex items-center justify-between mb-5">
              <h2 className="text-sm font-display font-semibold text-on-surface">
                Object Details
              </h2>
              <button
                onClick={() => setSelectedType(null)}
                className="p-1 rounded-lg hover:bg-surface-high transition-colors text-on-surface-dim"
              >
                <X size={16} />
              </button>
            </div>

            <div className="space-y-4">
              <div>
                <label className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold block mb-1.5">
                  Technical Name
                </label>
                <input
                  className="w-full bg-surface-highest rounded-lg px-3 py-2.5 text-sm outline-none focus:ring-1 focus:ring-primary/30 text-on-surface font-mono"
                  value={detailName}
                  onChange={(e) => setDetailName(e.target.value)}
                />
              </div>

              <div>
                <label className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold block mb-1.5">
                  Description
                </label>
                <textarea
                  className="w-full bg-surface-highest rounded-lg px-3 py-2.5 text-sm outline-none focus:ring-1 focus:ring-primary/30 text-on-surface resize-none"
                  rows={3}
                  value={detailDesc}
                  onChange={(e) => setDetailDesc(e.target.value)}
                  placeholder="Describe this object type..."
                />
              </div>

              <div>
                <label className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold block mb-1.5">
                  Metadata Tags
                </label>
                <div className="flex flex-wrap gap-1.5">
                  <span className="text-xs bg-surface-high px-2.5 py-1 rounded-md text-on-surface-dim">
                    ontology
                  </span>
                  <span className="text-xs bg-surface-high px-2.5 py-1 rounded-md text-on-surface-dim">
                    {selected.apiName}
                  </span>
                </div>
              </div>

              {/* Toggles */}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-sm text-on-surface">Elastic Search Link</span>
                  <button
                    onClick={() => setElasticEnabled(!elasticEnabled)}
                    className="text-primary"
                  >
                    {elasticEnabled ? <ToggleRight size={24} /> : <ToggleLeft size={24} className="text-on-surface-dim" />}
                  </button>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-on-surface">Vector Embeddings</span>
                  <button
                    onClick={() => setVectorEnabled(!vectorEnabled)}
                    className="text-primary"
                  >
                    {vectorEnabled ? <ToggleRight size={24} /> : <ToggleLeft size={24} className="text-on-surface-dim" />}
                  </button>
                </div>
              </div>

              {/* Properties */}
              <div>
                <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold mb-2">
                  Properties ({selected.properties.length})
                </p>
                <div className="space-y-1">
                  {selected.properties.map((prop) => (
                    <PropertyRow key={prop.apiName} prop={prop} />
                  ))}
                </div>
              </div>

              {/* Link Types */}
              {selected.linkTypes.length > 0 && (
                <div>
                  <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold mb-2">
                    Link Types ({selected.linkTypes.length})
                  </p>
                  <div className="space-y-1">
                    {selected.linkTypes.map((lt) => (
                      <div
                        key={lt.apiName}
                        className="flex items-center justify-between py-2 px-3 rounded-lg bg-surface-low"
                      >
                        <div>
                          <p className="text-sm font-medium text-on-surface">
                            {lt.displayName}
                          </p>
                          <p className="text-xs text-on-surface-dim font-mono">
                            {lt.apiName}
                          </p>
                        </div>
                        <span className="text-xs bg-accent/10 text-accent px-2 py-0.5 rounded-md">
                          {lt.targetObjectType}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              <button className="w-full mt-2 px-4 py-2.5 bg-primary text-on-primary text-sm font-bold rounded-lg hover:brightness-110 transition-all">
                Validate Definition
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
