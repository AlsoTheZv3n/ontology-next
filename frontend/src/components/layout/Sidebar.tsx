import { NavLink } from 'react-router-dom'
import {
  GitFork,
  Database,
  BookOpen,
  Bot,
  Search,
  Zap,
  Settings,
  HelpCircle,
  Plus,
  LayoutDashboard,
} from 'lucide-react'

const navItems = [
  { to: '/', icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/connectors', icon: Database, label: 'Data Sources' },
  { to: '/knowledge', icon: BookOpen, label: 'Knowledge Base' },
  { to: '/chat', icon: Bot, label: 'AI Chat' },
  { to: '/ontology', icon: GitFork, label: 'Ontology Builder' },
  { to: '/objects', icon: Search, label: 'Object Explorer' },
  { to: '/actions', icon: Zap, label: 'Action Center' },
]

export function Sidebar() {
  return (
    <aside className="fixed left-0 top-0 w-64 h-screen bg-surface-low flex flex-col z-30">
      {/* Logo */}
      <div className="px-5 pt-6 pb-4">
        <div className="flex items-center gap-3 mb-1">
          <div className="p-2 rounded-lg bg-primary-container">
            <GitFork size={18} className="text-primary" />
          </div>
          <div>
            <h1 className="font-display text-base font-bold text-primary leading-tight">
              NextOntology
            </h1>
            <p className="text-[10px] uppercase tracking-widest text-on-surface-dim leading-tight">
              Engineering Workspace
            </p>
          </div>
        </div>
      </div>

      {/* New Project Button */}
      <div className="px-4 mb-4">
        <button className="w-full flex items-center justify-center gap-2 px-4 py-2.5 bg-linear-to-r from-primary-container to-surface-high text-primary text-sm font-medium rounded-lg hover:brightness-110 transition-all">
          <Plus size={16} />
          New Project
        </button>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 space-y-0.5 overflow-y-auto">
        {navItems.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2.5 rounded-lg text-[13px] font-medium transition-colors ${
                isActive
                  ? 'bg-surface-high text-on-surface border-l-2 border-primary -ml-0.5 pl-3.5'
                  : 'text-slate-400 hover:bg-slate-800/40 hover:text-on-surface'
              }`
            }
          >
            <Icon size={17} />
            {label}
          </NavLink>
        ))}
      </nav>

      {/* Bottom Links */}
      <div className="px-3 py-3 space-y-0.5">
        <NavLink
          to="/settings"
          className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-[13px] font-medium text-slate-400 hover:bg-slate-800/40 hover:text-on-surface transition-colors"
        >
          <Settings size={17} />
          Settings
        </NavLink>
        <NavLink
          to="/support"
          className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-[13px] font-medium text-slate-400 hover:bg-slate-800/40 hover:text-on-surface transition-colors"
        >
          <HelpCircle size={17} />
          Support
        </NavLink>
      </div>

      {/* User Card */}
      <div className="px-4 pb-5 pt-2">
        <div className="flex items-center gap-3 px-3 py-3 rounded-lg bg-surface-container">
          <div className="w-8 h-8 rounded-full bg-primary-container flex items-center justify-center text-xs font-semibold text-primary">
            JD
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-on-surface truncate">Jane Doe</p>
            <p className="text-[11px] text-on-surface-dim truncate">Platform Engineer</p>
          </div>
        </div>
      </div>
    </aside>
  )
}
