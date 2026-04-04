import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { ApolloProvider } from '@apollo/client'
import { apolloClient } from '@/api/graphql/client'
import { Layout } from '@/components/layout/Layout'
import { Dashboard } from '@/pages/Dashboard'
import { OntologyBuilder } from '@/pages/OntologyBuilder'
import { ObjectExplorer } from '@/pages/ObjectExplorer'
import { GraphView } from '@/pages/GraphView'
import { ConnectorManager } from '@/pages/ConnectorManager'
import { ActionCenter } from '@/pages/ActionCenter'
import { AiChat } from '@/pages/AiChat'

function Placeholder({ title }: { title: string }) {
  return (
    <div className="flex items-center justify-center h-[calc(100vh-4rem)] text-on-surface-dim">
      <div className="text-center">
        <p className="text-2xl font-display font-semibold text-on-surface mb-2">{title}</p>
        <p className="text-sm">Coming soon</p>
      </div>
    </div>
  )
}

export default function App() {
  return (
    <ApolloProvider client={apolloClient}>
      <BrowserRouter>
        <Layout>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/ontology" element={<OntologyBuilder />} />
            <Route path="/objects/:objectType?" element={<ObjectExplorer />} />
            <Route path="/graph" element={<GraphView />} />
            <Route path="/connectors" element={<ConnectorManager />} />
            <Route path="/actions" element={<ActionCenter />} />
            <Route path="/chat" element={<AiChat />} />
            <Route path="/knowledge" element={<Placeholder title="Knowledge Base" />} />
            <Route path="/settings" element={<Placeholder title="Settings" />} />
          </Routes>
        </Layout>
      </BrowserRouter>
    </ApolloProvider>
  )
}
