import { useState } from 'react'
import { useQuery } from '@apollo/client'
import { GET_ALL_OBJECT_TYPES, SEARCH_OBJECTS } from '@/api/graphql/queries'
import { BookOpen, Search, FileText, Database, GitFork, Zap, ChevronRight } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import type { ObjectTypeSchema } from '@/types'

function StatCard({ icon: Icon, label, value, color }: {
  icon: React.ElementType; label: string; value: number; color: string
}) {
  return (
    <div className="bg-surface-container rounded-xl p-5">
      <div className="flex items-center gap-3 mb-3">
        <div className={`p-2 rounded-lg ${color}`}>
          <Icon size={16} />
        </div>
        <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold">{label}</p>
      </div>
      <p className="text-2xl font-display font-semibold text-on-surface">{value}</p>
    </div>
  )
}

export function KnowledgeBase() {
  const navigate = useNavigate()
  const [searchQuery, setSearchQuery] = useState('')
  const { data: otData } = useQuery(GET_ALL_OBJECT_TYPES)

  const objectTypes: ObjectTypeSchema[] = otData?.getAllObjectTypes ?? []
  const totalProps = objectTypes.reduce((sum, ot) => sum + ot.properties.length, 0)
  const totalLinks = objectTypes.reduce((sum, ot) => sum + ot.linkTypes.length, 0)

  const filteredTypes = searchQuery
    ? objectTypes.filter(ot =>
        ot.displayName.toLowerCase().includes(searchQuery.toLowerCase()) ||
        ot.apiName.toLowerCase().includes(searchQuery.toLowerCase())
      )
    : objectTypes

  return (
    <div className="p-8 max-w-5xl">
      {/* Header */}
      <div className="mb-8">
        <div className="flex items-center gap-3 mb-2">
          <div className="p-2.5 rounded-xl bg-primary/15">
            <BookOpen size={20} className="text-primary" />
          </div>
          <div>
            <h1 className="text-2xl font-display font-extrabold tracking-tight text-on-surface">
              Knowledge Base
            </h1>
            <p className="text-sm text-on-surface-dim">
              Browse and explore your ontology schema, object types, and relationships
            </p>
          </div>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-4 mb-8">
        <StatCard icon={Database} label="Object Types" value={objectTypes.length} color="bg-accent/15 text-accent" />
        <StatCard icon={FileText} label="Properties" value={totalProps} color="bg-tertiary/15 text-tertiary" />
        <StatCard icon={GitFork} label="Relationships" value={totalLinks} color="bg-primary/15 text-primary" />
      </div>

      {/* Search */}
      <div className="relative mb-6">
        <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-on-surface-dim" />
        <input
          className="w-full bg-surface-container rounded-xl pl-10 pr-4 py-3 text-sm outline-none focus:ring-1 focus:ring-primary/30 text-on-surface placeholder:text-on-surface-dim"
          placeholder="Search object types, properties, relationships..."
          value={searchQuery}
          onChange={e => setSearchQuery(e.target.value)}
        />
      </div>

      {/* Object Type Catalog */}
      <div className="space-y-3">
        <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold">
          Schema Catalog ({filteredTypes.length})
        </p>

        {filteredTypes.map(ot => (
          <div
            key={ot.apiName}
            onClick={() => navigate(`/objects/${ot.apiName}`)}
            className="bg-surface-container rounded-xl p-5 cursor-pointer hover:bg-surface-high transition-colors group"
          >
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-3">
                <div className="p-2 rounded-lg bg-accent/15">
                  <Database size={16} className="text-accent" />
                </div>
                <div>
                  <h3 className="text-sm font-semibold text-on-surface">{ot.displayName}</h3>
                  <p className="text-xs text-on-surface-dim font-mono">{ot.apiName}</p>
                </div>
              </div>
              <ChevronRight size={16} className="text-on-surface-dim opacity-0 group-hover:opacity-100 transition-opacity" />
            </div>

            {ot.description && (
              <p className="text-xs text-on-surface-dim mb-3">{ot.description}</p>
            )}

            <div className="flex gap-2 flex-wrap">
              {ot.properties.map(p => (
                <span key={p.apiName} className="text-[10px] bg-surface-high px-2 py-0.5 rounded text-on-surface-dim font-mono">
                  {p.apiName}: {p.dataType}
                </span>
              ))}
              {ot.properties.length === 0 && (
                <span className="text-[10px] text-on-surface-dim">No properties defined</span>
              )}
            </div>

            {ot.linkTypes.length > 0 && (
              <div className="flex gap-2 mt-2 flex-wrap">
                {ot.linkTypes.map(lt => (
                  <span key={lt.apiName} className="text-[10px] bg-primary/10 text-primary px-2 py-0.5 rounded">
                    {lt.displayName} → {lt.targetObjectType}
                  </span>
                ))}
              </div>
            )}
          </div>
        ))}

        {filteredTypes.length === 0 && (
          <div className="text-center py-16 text-on-surface-dim">
            <BookOpen size={48} className="mx-auto mb-4 opacity-20" />
            <p className="text-sm">
              {searchQuery ? 'No results found' : 'No object types in your ontology yet'}
            </p>
          </div>
        )}
      </div>
    </div>
  )
}
