import { HelpCircle, BookOpen, MessageCircle, ExternalLink, Github, Mail } from 'lucide-react'

function SupportCard({ icon: Icon, title, description, action, href }: {
  icon: React.ElementType; title: string; description: string; action: string; href?: string
}) {
  return (
    <a
      href={href ?? '#'}
      target={href ? '_blank' : undefined}
      rel="noopener noreferrer"
      className="bg-surface-container rounded-xl p-6 hover:bg-surface-high transition-colors group block"
    >
      <div className="flex items-start gap-4">
        <div className="p-2.5 rounded-xl bg-surface-high group-hover:bg-surface-bright transition-colors">
          <Icon size={20} className="text-on-surface-dim" />
        </div>
        <div className="flex-1">
          <h3 className="text-sm font-semibold text-on-surface mb-1">{title}</h3>
          <p className="text-xs text-on-surface-dim mb-3">{description}</p>
          <span className="text-xs text-primary font-medium flex items-center gap-1">
            {action}
            {href && <ExternalLink size={10} />}
          </span>
        </div>
      </div>
    </a>
  )
}

export function SupportPage() {
  return (
    <div className="p-8 max-w-3xl">
      <div className="mb-8">
        <div className="flex items-center gap-3 mb-2">
          <div className="p-2.5 rounded-xl bg-surface-high">
            <HelpCircle size={20} className="text-on-surface-dim" />
          </div>
          <div>
            <h1 className="text-2xl font-display font-extrabold tracking-tight text-on-surface">Support</h1>
            <p className="text-sm text-on-surface-dim">Get help, browse documentation, and contact our team</p>
          </div>
        </div>
      </div>

      <div className="space-y-4">
        <SupportCard
          icon={BookOpen}
          title="Documentation"
          description="Browse the complete NEXO Ontology Engine documentation, API reference, and guides."
          action="Open Docs"
          href="https://github.com/AlsoTheZv3n/ontology-next"
        />

        <SupportCard
          icon={Github}
          title="GitHub Repository"
          description="View source code, report issues, and contribute to the project."
          action="View on GitHub"
          href="https://github.com/AlsoTheZv3n/ontology-next"
        />

        <SupportCard
          icon={MessageCircle}
          title="GraphiQL Playground"
          description="Explore the GraphQL API interactively. Test queries and mutations."
          action="Open GraphiQL"
          href="http://localhost:8081/graphiql"
        />

        <SupportCard
          icon={Mail}
          title="Contact Support"
          description="Need help with something specific? Reach out to the engineering team."
          action="Send Email"
          href="mailto:support@nexoai.ch"
        />
      </div>

      {/* System Info */}
      <div className="mt-8 bg-surface-container rounded-xl p-6">
        <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold mb-4">System Info</p>
        <div className="space-y-2 text-xs">
          <div className="flex justify-between">
            <span className="text-on-surface-dim">Version</span>
            <span className="text-on-surface font-mono">0.1.0-SNAPSHOT</span>
          </div>
          <div className="flex justify-between">
            <span className="text-on-surface-dim">Backend</span>
            <span className="text-on-surface font-mono">Spring Boot 3.4 / Java 21</span>
          </div>
          <div className="flex justify-between">
            <span className="text-on-surface-dim">Frontend</span>
            <span className="text-on-surface font-mono">React 19 / Vite / Tailwind v4</span>
          </div>
          <div className="flex justify-between">
            <span className="text-on-surface-dim">Database</span>
            <span className="text-on-surface font-mono">PostgreSQL 16 + pgvector</span>
          </div>
          <div className="flex justify-between">
            <span className="text-on-surface-dim">GraphQL</span>
            <a href="http://localhost:8081/graphiql" target="_blank" className="text-primary font-mono hover:underline">
              localhost:8081/graphiql
            </a>
          </div>
          <div className="flex justify-between">
            <span className="text-on-surface-dim">Health</span>
            <a href="http://localhost:8081/actuator/health" target="_blank" className="text-primary font-mono hover:underline">
              localhost:8081/actuator/health
            </a>
          </div>
        </div>
      </div>
    </div>
  )
}
