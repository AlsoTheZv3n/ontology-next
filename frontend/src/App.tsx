import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { ApolloProvider } from '@apollo/client'
import { apolloClient } from '@/api/graphql/client'
import { Layout } from '@/components/layout/Layout'
import { ErrorBoundary } from '@/components/shared/ErrorBoundary'
import { Dashboard } from '@/pages/Dashboard'
import { OntologyBuilder } from '@/pages/OntologyBuilder'
import { ObjectExplorer } from '@/pages/ObjectExplorer'
import { GraphView } from '@/pages/GraphView'
import { ConnectorManager } from '@/pages/ConnectorManager'
import { ActionCenter } from '@/pages/ActionCenter'
import { AiChat } from '@/pages/AiChat'
import { KnowledgeBase } from '@/pages/KnowledgeBase'
import { SettingsPage } from '@/pages/SettingsPage'
import { SupportPage } from '@/pages/SupportPage'

export default function App() {
  return (
    <ApolloProvider client={apolloClient}>
      <BrowserRouter>
        <Layout>
          <ErrorBoundary>
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/ontology" element={<OntologyBuilder />} />
              <Route path="/objects/:objectType?" element={<ObjectExplorer />} />
              <Route path="/graph" element={<GraphView />} />
              <Route path="/connectors" element={<ConnectorManager />} />
              <Route path="/actions" element={<ActionCenter />} />
              <Route path="/chat" element={<AiChat />} />
              <Route path="/knowledge" element={<KnowledgeBase />} />
              <Route path="/settings" element={<SettingsPage />} />
              <Route path="/support" element={<SupportPage />} />
            </Routes>
          </ErrorBoundary>
        </Layout>
      </BrowserRouter>
    </ApolloProvider>
  )
}
