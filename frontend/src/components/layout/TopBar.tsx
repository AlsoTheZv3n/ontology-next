import { Search, Cloud, Bell, Settings } from 'lucide-react'

export function TopBar() {
  return (
    <header className="fixed top-0 left-64 right-0 h-16 bg-surface-low flex items-center justify-between px-6 z-20 border-b border-outline-variant/10">
      {/* Left: Search */}
      <div className="relative w-80">
        <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-dim" />
        <input
          type="text"
          placeholder="Search..."
          className="w-full bg-surface-highest rounded-lg pl-9 pr-4 py-2 text-sm text-on-surface placeholder:text-on-surface-dim outline-none focus:ring-1 focus:ring-primary/30"
        />
      </div>

      {/* Right: Actions + Avatar */}
      <div className="flex items-center gap-2">
        <button className="p-2 rounded-lg text-on-surface-dim hover:text-on-surface hover:bg-surface-high transition-colors">
          <Cloud size={18} />
        </button>
        <button className="p-2 rounded-lg text-on-surface-dim hover:text-on-surface hover:bg-surface-high transition-colors relative">
          <Bell size={18} />
        </button>
        <button className="p-2 rounded-lg text-on-surface-dim hover:text-on-surface hover:bg-surface-high transition-colors">
          <Settings size={18} />
        </button>
        <div className="ml-2 w-8 h-8 rounded-full bg-primary-container flex items-center justify-center text-xs font-semibold text-primary cursor-pointer">
          JD
        </div>
      </div>
    </header>
  )
}
