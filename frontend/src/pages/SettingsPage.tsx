import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { Settings, User, Shield, Bell, Database, Key, Globe, Moon } from 'lucide-react'

function SettingSection({ icon: Icon, title, description, children }: {
  icon: React.ElementType; title: string; description: string; children: React.ReactNode
}) {
  return (
    <div className="bg-surface-container rounded-xl p-6">
      <div className="flex items-center gap-3 mb-4">
        <div className="p-2 rounded-lg bg-surface-high">
          <Icon size={16} className="text-on-surface-dim" />
        </div>
        <div>
          <h3 className="text-sm font-semibold text-on-surface">{title}</h3>
          <p className="text-xs text-on-surface-dim">{description}</p>
        </div>
      </div>
      {children}
    </div>
  )
}

function ToggleRow({ label, description, defaultOn = false }: {
  label: string; description: string; defaultOn?: boolean
}) {
  const [on, setOn] = useState(defaultOn)
  return (
    <div className="flex items-center justify-between py-3">
      <div>
        <p className="text-sm text-on-surface">{label}</p>
        <p className="text-xs text-on-surface-dim">{description}</p>
      </div>
      <button
        onClick={() => setOn(!on)}
        className={`w-10 h-5 rounded-full transition-colors relative ${on ? 'bg-accent' : 'bg-surface-bright'}`}
      >
        <span className={`absolute top-0.5 w-4 h-4 rounded-full bg-white transition-transform ${on ? 'left-5' : 'left-0.5'}`} />
      </button>
    </div>
  )
}

export function SettingsPage() {
  const [tenantInfo, setTenantInfo] = useState<{ apiName: string; displayName: string; plan: string } | null>(null)

  useEffect(() => {
    fetch('/api/admin/tenants')
      .then(r => r.json())
      .then(data => {
        if (Array.isArray(data) && data.length > 0) {
          setTenantInfo({ apiName: data[0].apiName, displayName: data[0].displayName, plan: data[0].plan })
        }
      })
      .catch(() => {})
  }, [])

  return (
    <div className="p-8 max-w-3xl">
      <div className="mb-8">
        <h1 className="text-2xl font-display font-extrabold tracking-tight text-on-surface mb-1">Settings</h1>
        <p className="text-sm text-on-surface-dim">Manage your workspace, account, and preferences</p>
      </div>

      <div className="space-y-4">
        {/* Profile */}
        <SettingSection icon={User} title="Profile" description="Your account information">
          <div className="space-y-3">
            <div>
              <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold mb-1">Name</p>
              <input className="w-full bg-surface-high rounded-lg px-3 py-2.5 text-sm text-on-surface outline-none focus:ring-1 focus:ring-primary/30" defaultValue="Jane Doe" />
            </div>
            <div>
              <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold mb-1">Email</p>
              <input className="w-full bg-surface-high rounded-lg px-3 py-2.5 text-sm text-on-surface outline-none focus:ring-1 focus:ring-primary/30" defaultValue="admin@nexo.ai" />
            </div>
            <div>
              <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold mb-1">Role</p>
              <p className="text-sm text-on-surface bg-surface-high rounded-lg px-3 py-2.5">Platform Engineer</p>
            </div>
          </div>
        </SettingSection>

        {/* Workspace */}
        <SettingSection icon={Globe} title="Workspace" description="Tenant and organization settings">
          <div className="space-y-3">
            <div className="flex justify-between items-center">
              <p className="text-sm text-on-surface-dim">Tenant</p>
              <p className="text-sm text-on-surface font-mono">{tenantInfo?.apiName ?? 'default'}</p>
            </div>
            <div className="flex justify-between items-center">
              <p className="text-sm text-on-surface-dim">Display Name</p>
              <p className="text-sm text-on-surface">{tenantInfo?.displayName ?? 'Default Tenant'}</p>
            </div>
            <div className="flex justify-between items-center">
              <p className="text-sm text-on-surface-dim">Plan</p>
              <span className="text-[10px] uppercase tracking-widest font-bold px-2 py-0.5 rounded bg-tertiary/15 text-tertiary">
                {tenantInfo?.plan ?? 'ENTERPRISE'}
              </span>
            </div>
          </div>
        </SettingSection>

        {/* Notifications */}
        <SettingSection icon={Bell} title="Notifications" description="Configure how you receive alerts">
          <div className="divide-y divide-outline-variant/10">
            <ToggleRow label="Sync Failures" description="Get notified when a data source sync fails" defaultOn />
            <ToggleRow label="Schema Changes" description="Alert on breaking schema changes" defaultOn />
            <ToggleRow label="Agent Actions" description="Notify when AI agent requests approval" defaultOn />
            <ToggleRow label="Weekly Digest" description="Receive a weekly summary email" />
          </div>
        </SettingSection>

        {/* Security */}
        <SettingSection icon={Shield} title="Security" description="Authentication and access control">
          <div className="space-y-3">
            <button className="w-full text-left px-4 py-3 bg-surface-high rounded-lg text-sm text-on-surface hover:bg-surface-bright transition-colors">
              Change Password
            </button>
            <Link to="/settings/api-keys" className="block w-full text-left px-4 py-3 bg-surface-high rounded-lg text-sm text-on-surface hover:bg-surface-bright transition-colors">
              Manage API Keys
            </Link>
            <button className="w-full text-left px-4 py-3 bg-surface-high rounded-lg text-sm text-on-surface hover:bg-surface-bright transition-colors">
              Active Sessions
            </button>
          </div>
        </SettingSection>

        {/* Appearance */}
        <SettingSection icon={Moon} title="Appearance" description="Customize the interface">
          <div className="divide-y divide-outline-variant/10">
            <ToggleRow label="Dark Mode" description="Always use dark theme" defaultOn />
            <ToggleRow label="Compact View" description="Reduce spacing in tables and lists" />
          </div>
        </SettingSection>
      </div>
    </div>
  )
}
