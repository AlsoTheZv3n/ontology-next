import { create } from 'zustand'

interface OntologyStore {
  selectedObjectType: string | null
  setSelectedObjectType: (name: string | null) => void
  sidebarOpen: boolean
  toggleSidebar: () => void
}

export const useOntologyStore = create<OntologyStore>((set) => ({
  selectedObjectType: null,
  setSelectedObjectType: (name) => set({ selectedObjectType: name }),
  sidebarOpen: true,
  toggleSidebar: () => set(state => ({ sidebarOpen: !state.sidebarOpen }))
}))
