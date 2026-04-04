import { Sidebar } from './Sidebar'
import { TopBar } from './TopBar'

export function Layout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-surface w-full">
      <Sidebar />
      <TopBar />
      <main className="ml-64 pt-16 min-h-screen overflow-auto">
        {children}
      </main>
    </div>
  )
}
