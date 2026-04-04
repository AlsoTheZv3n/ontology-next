import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@apollo/client'
import { GET_ALL_OBJECT_TYPES, GET_LINK_TYPES } from '@/api/graphql/queries'
import { useNavigate } from 'react-router-dom'
import cytoscape from 'cytoscape'
import {
  ZoomIn,
  ZoomOut,
  Maximize2,
  Play,
  Boxes,
} from 'lucide-react'
import type { ObjectTypeSchema, LinkTypeSchema } from '@/types'

export function GraphView() {
  const containerRef = useRef<HTMLDivElement>(null)
  const cyRef = useRef<cytoscape.Core | null>(null)
  const navigate = useNavigate()
  const [selectedNode, setSelectedNode] = useState<ObjectTypeSchema | null>(null)
  const [activeTab, setActiveTab] = useState<'types' | 'queries' | 'mutations'>('types')
  const [queryText, setQueryText] = useState(
    '{\n  getAllObjectTypes {\n    apiName\n    displayName\n  }\n}'
  )

  const { data: otData } = useQuery(GET_ALL_OBJECT_TYPES)
  const { data: ltData } = useQuery(GET_LINK_TYPES)

  const objectTypes: ObjectTypeSchema[] = otData?.getAllObjectTypes ?? []
  const linkTypes: LinkTypeSchema[] = ltData?.getLinkTypes ?? []

  useEffect(() => {
    if (!containerRef.current || objectTypes.length === 0) return

    const nodes = objectTypes.map((ot) => ({
      data: {
        id: ot.apiName,
        label: ot.displayName,
        propCount: ot.properties.length,
      },
    }))

    const edges = linkTypes.map((lt) => ({
      data: {
        id: lt.apiName,
        source:
          objectTypes.find((ot) =>
            ot.linkTypes.some((l) => l.apiName === lt.apiName)
          )?.apiName ?? lt.apiName.split('_')[0],
        target: lt.targetObjectType,
        label: lt.displayName,
      },
    }))

    if (cyRef.current) {
      cyRef.current.destroy()
    }

    cyRef.current = cytoscape({
      container: containerRef.current,
      elements: [...nodes, ...edges],
      style: [
        {
          selector: 'node',
          style: {
            'background-color': '#1e293b',
            label: 'data(label)',
            color: '#dae2fd',
            'text-valign': 'bottom',
            'text-margin-y': 10,
            'font-size': '11px',
            'font-family': 'Inter, sans-serif',
            width: 56,
            height: 56,
            'border-width': 2,
            'border-color': '#3b82f6',
          },
        },
        {
          selector: 'edge',
          style: {
            width: 1.5,
            'line-color': '#2d3449',
            'target-arrow-color': '#8892a8',
            'target-arrow-shape': 'triangle',
            'curve-style': 'bezier',
            label: 'data(label)',
            'font-size': '9px',
            color: '#8892a8',
            'text-rotation': 'autorotate',
            'font-family': 'Inter, sans-serif',
          },
        },
        {
          selector: 'node:active',
          style: { 'overlay-opacity': 0 },
        },
        {
          selector: 'node:selected',
          style: {
            'background-color': '#ddc39d',
            'border-color': '#ddc39d',
          },
        },
      ],
      layout: {
        name: objectTypes.length > 1 ? 'cose' : 'grid',
        idealEdgeLength: () => 200,
        nodeRepulsion: () => 8000,
        animate: true,
        animationDuration: 500,
      } as cytoscape.LayoutOptions,
    })

    cyRef.current.on('tap', 'node', (evt) => {
      const apiName = evt.target.id()
      const ot = objectTypes.find((o) => o.apiName === apiName)
      if (ot) setSelectedNode(ot)
    })

    cyRef.current.on('tap', (evt) => {
      if (evt.target === cyRef.current) setSelectedNode(null)
    })

    return () => {
      cyRef.current?.destroy()
    }
  }, [objectTypes, linkTypes, navigate])

  const handleZoomIn = () => cyRef.current?.zoom(cyRef.current.zoom() * 1.3)
  const handleZoomOut = () => cyRef.current?.zoom(cyRef.current.zoom() * 0.7)
  const handleFit = () => cyRef.current?.fit(undefined, 40)

  const tabs = [
    { key: 'types' as const, label: 'Types' },
    { key: 'queries' as const, label: 'Queries' },
    { key: 'mutations' as const, label: 'Mutations' },
  ]

  return (
    <div className="h-[calc(100vh-4rem)] flex flex-col">
      {/* Top Header */}
      <div className="px-8 py-4 bg-surface-low flex items-center justify-between shrink-0">
        <div>
          <h1 className="text-2xl font-display font-semibold tracking-tight text-on-surface">
            Graph View
          </h1>
          <p className="text-sm text-on-surface-dim">
            {objectTypes.length} types -- {linkTypes.length} relationships -- Click a node
            to inspect
          </p>
        </div>
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2 text-xs text-on-surface-dim">
            <span className="inline-block w-2.5 h-2.5 rounded-full bg-accent" />
            Nodes: {objectTypes.length}
          </div>
          <div className="flex items-center gap-2 text-xs text-on-surface-dim">
            <span className="inline-block w-2.5 h-2.5 rounded-full bg-surface-bright" />
            Edges: {linkTypes.length}
          </div>
        </div>
      </div>

      <div className="flex-1 flex overflow-hidden">
        {/* Main Graph Area */}
        <div className="flex-1 relative">
          {/* Graph Canvas */}
          <div
            ref={containerRef}
            className="absolute inset-0"
            style={{
              backgroundImage:
                'radial-gradient(circle, rgba(255,255,255,0.03) 1px, transparent 1px)',
              backgroundSize: '40px 40px',
              backgroundColor: '#0b1326',
            }}
          />

          {/* Zoom Controls - top left, glass morphism */}
          <div className="absolute top-4 left-4 flex flex-col gap-1 z-10">
            {[
              { icon: ZoomIn, handler: handleZoomIn },
              { icon: ZoomOut, handler: handleZoomOut },
              { icon: Maximize2, handler: handleFit },
            ].map(({ icon: Icon, handler }, i) => (
              <button
                key={i}
                onClick={handler}
                className="p-2 rounded-lg text-on-surface-dim hover:text-on-surface transition-colors"
                style={{
                  background: 'rgba(23, 31, 51, 0.8)',
                  backdropFilter: 'blur(12px)',
                }}
              >
                <Icon size={16} />
              </button>
            ))}
          </div>

          {/* Node Detail Panel - bottom left */}
          {selectedNode && (
            <div className="glass-panel absolute bottom-4 left-4 w-72 rounded-xl p-5 z-10">
              <div className="flex items-center gap-3 mb-3">
                <div className="p-2 rounded-lg bg-accent/15">
                  <Boxes size={16} className="text-accent" />
                </div>
                <div>
                  <h3 className="text-sm font-semibold text-on-surface">
                    {selectedNode.displayName}
                  </h3>
                  <p className="text-xs text-on-surface-dim font-mono">
                    {selectedNode.apiName}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-2 mb-3">
                <span className="text-[10px] uppercase tracking-widest font-bold px-2 py-0.5 rounded-md bg-success/15 text-success">
                  Active
                </span>
                <span className="text-xs text-on-surface-dim">
                  ID: {selectedNode.id.slice(0, 8)}...
                </span>
              </div>
              <div className="space-y-1">
                {selectedNode.properties.map((prop) => (
                  <div
                    key={prop.apiName}
                    className="flex items-center justify-between text-xs py-1"
                  >
                    <span className="font-mono text-on-surface-variant">
                      {prop.apiName}
                    </span>
                    <span className="font-mono text-on-surface-dim">{prop.dataType}</span>
                  </div>
                ))}
              </div>
              <button
                onClick={() => navigate(`/objects/${selectedNode.apiName}`)}
                className="mt-3 w-full text-xs text-center py-2 bg-surface-high rounded-lg text-on-surface-dim hover:text-on-surface transition-colors"
              >
                Open in Object Explorer
              </button>
            </div>
          )}
        </div>

        {/* Right Panel - Schema Explorer */}
        <div className="w-96 bg-surface-low flex flex-col overflow-hidden shrink-0">
          <div className="p-5 pb-3">
            <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold">
              Schema Explorer
            </p>
          </div>

          {/* Tabs */}
          <div className="flex px-5">
            {tabs.map((tab) => (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                className={`px-3 py-2.5 text-xs font-medium transition-colors ${
                  activeTab === tab.key
                    ? 'text-on-surface bg-surface-high rounded-t-lg'
                    : 'text-on-surface-dim hover:text-on-surface'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {/* Tab Content */}
          <div className="flex-1 overflow-y-auto p-5">
            {activeTab === 'types' && (
              <div className="space-y-3">
                {objectTypes.map((ot) => (
                  <div
                    key={ot.apiName}
                    className="bg-surface-container rounded-lg p-3"
                  >
                    <p className="text-xs font-mono mb-1.5">
                      <span className="text-primary">type</span>{' '}
                      <span className="text-on-surface">{ot.apiName}</span>{' '}
                      <span className="text-on-surface-dim">{'{'}</span>
                    </p>
                    <div className="pl-4 space-y-0.5">
                      {ot.properties.map((prop) => (
                        <p key={prop.apiName} className="text-xs font-mono">
                          <span className="text-on-surface-variant">{prop.apiName}</span>
                          <span className="text-on-surface-dim">: </span>
                          <span className="text-tertiary">{prop.dataType}</span>
                          {prop.isRequired && (
                            <span className="text-error">!</span>
                          )}
                        </p>
                      ))}
                    </div>
                    <p className="text-xs font-mono text-on-surface-dim">{'}'}</p>
                  </div>
                ))}
                {objectTypes.length === 0 && (
                  <p className="text-sm text-on-surface-dim">No types defined yet</p>
                )}
              </div>
            )}
            {activeTab === 'queries' && (
              <div className="space-y-2">
                {['getAllObjectTypes', 'searchObjects', 'getObject', 'getLinkTypes', 'getActionLog', 'getActionTypes'].map(
                  (q) => (
                    <div
                      key={q}
                      className="bg-surface-container rounded-lg p-3"
                    >
                      <p className="text-xs font-mono">
                        <span className="text-primary">query</span>{' '}
                        <span className="text-on-surface">{q}</span>
                      </p>
                    </div>
                  )
                )}
              </div>
            )}
            {activeTab === 'mutations' && (
              <div className="space-y-2">
                {['createObject', 'deleteObject', 'executeAction', 'registerActionType', 'agentChat'].map(
                  (m) => (
                    <div
                      key={m}
                      className="bg-surface-container rounded-lg p-3"
                    >
                      <p className="text-xs font-mono">
                        <span className="text-tertiary">mutation</span>{' '}
                        <span className="text-on-surface">{m}</span>
                      </p>
                    </div>
                  )
                )}
              </div>
            )}
          </div>

          {/* Bottom Query Editor */}
          <div className="p-4 shrink-0">
            <div className="flex items-center justify-between mb-2">
              <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold">
                GraphQL Query
              </p>
              <button className="flex items-center gap-1.5 px-3 py-1.5 text-xs bg-primary text-on-primary rounded-lg font-bold hover:brightness-110 transition-all">
                <Play size={12} />
                Execute
              </button>
            </div>
            <textarea
              className="w-full bg-surface-dim rounded-lg p-3 text-xs font-mono text-on-surface-variant outline-none resize-none focus:ring-1 focus:ring-primary/30"
              rows={5}
              value={queryText}
              onChange={(e) => setQueryText(e.target.value)}
            />
          </div>
        </div>
      </div>
    </div>
  )
}
