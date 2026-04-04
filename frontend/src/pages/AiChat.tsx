import { useState, useRef, useEffect } from 'react'
import { useMutation } from '@apollo/client'
import { AGENT_CHAT } from '@/api/graphql/mutations'
import {
  RefreshCw,
  Paperclip,
  Code2,
  Image,
  Send,
  AlertTriangle,
} from 'lucide-react'

interface ChatMessage {
  id: string
  role: 'user' | 'ai'
  content: string
  thinking?: boolean
  insights?: boolean
}

export function AiChat() {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: '1',
      role: 'ai',
      content:
        'Welcome to the Next Ontology Engine. I can help you explore your schema, analyze relationships, run queries, and surface insights from your data. What would you like to investigate?',
    },
  ])
  const [input, setInput] = useState('')
  const [isThinking, setIsThinking] = useState(false)
  const chatEndRef = useRef<HTMLDivElement>(null)

  const [agentChat] = useMutation(AGENT_CHAT, {
    onError: () => {
      setIsThinking(false)
      setMessages((prev) => [
        ...prev.filter((m) => !m.thinking),
        {
          id: Date.now().toString(),
          role: 'ai',
          content: 'I encountered an issue processing your request. The backend agent may not be available. Please try again.',
        },
      ])
    },
  })

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSend = async () => {
    const trimmed = input.trim()
    if (!trimmed || isThinking) return

    const userMsg: ChatMessage = {
      id: Date.now().toString(),
      role: 'user',
      content: trimmed,
    }

    const thinkingMsg: ChatMessage = {
      id: 'thinking',
      role: 'ai',
      content: '',
      thinking: true,
    }

    setMessages((prev) => [...prev, userMsg, thinkingMsg])
    setInput('')
    setIsThinking(true)

    try {
      const { data } = await agentChat({ variables: { message: trimmed } })
      const reply = data?.agentChat?.reply ?? 'No response received.'

      setMessages((prev) => [
        ...prev.filter((m) => !m.thinking),
        {
          id: (Date.now() + 1).toString(),
          role: 'ai',
          content: reply,
          insights: reply.length > 100,
        },
      ])
    } catch {
      // handled by onError
    } finally {
      setIsThinking(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  return (
    <div className="flex flex-col h-[calc(100vh-4rem)]">
      {/* Chat Messages Area */}
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-4xl mx-auto px-6 py-8 space-y-6">
          {messages.map((msg) => (
            <div key={msg.id}>
              {msg.role === 'user' ? (
                <UserMessage content={msg.content} />
              ) : msg.thinking ? (
                <ThinkingMessage />
              ) : (
                <AiMessage content={msg.content} showInsights={msg.insights} />
              )}
            </div>
          ))}
          <div ref={chatEndRef} />
        </div>
      </div>

      {/* Footer Input */}
      <div
        className="shrink-0 px-6 py-4"
        style={{
          background: 'linear-gradient(to top, #0b1326 0%, rgba(11,19,38,0.95) 60%, transparent 100%)',
        }}
      >
        <div className="max-w-4xl mx-auto">
          <div className="relative bg-surface-highest rounded-2xl shadow-2xl">
            <textarea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Ask about your ontology, run queries, or explore relationships..."
              rows={2}
              className="w-full bg-transparent rounded-2xl pl-14 pr-24 py-4 text-sm text-on-surface placeholder:text-on-surface-dim outline-none resize-none"
            />
            {/* Left icons */}
            <div className="absolute left-3 bottom-3.5 flex items-center gap-1">
              <button className="p-1.5 rounded-lg text-on-surface-dim hover:text-on-surface hover:bg-surface-high transition-colors">
                <Paperclip size={16} />
              </button>
              <button className="p-1.5 rounded-lg text-on-surface-dim hover:text-on-surface hover:bg-surface-high transition-colors">
                <Code2 size={16} />
              </button>
              <button className="p-1.5 rounded-lg text-on-surface-dim hover:text-on-surface hover:bg-surface-high transition-colors">
                <Image size={16} />
              </button>
            </div>
            {/* Right: Execute button */}
            <div className="absolute right-3 bottom-3">
              <button
                onClick={handleSend}
                disabled={isThinking || !input.trim()}
                className="flex items-center gap-2 px-4 py-2 bg-primary text-on-primary text-sm font-bold rounded-xl hover:brightness-110 transition-all disabled:opacity-40"
              >
                <Send size={14} />
                Execute
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

/* ---------- Message Components ---------- */

function UserMessage({ content }: { content: string }) {
  return (
    <div className="flex flex-col items-end">
      <div className="flex items-center gap-2 mb-1.5">
        <span className="text-xs font-medium text-on-surface-dim">You</span>
        <span className="w-2 h-2 rounded-full bg-secondary" />
      </div>
      <div className="bg-surface-low rounded-2xl rounded-tr-none px-5 py-4 max-w-[85%]">
        <p className="text-sm text-on-surface leading-relaxed whitespace-pre-wrap">{content}</p>
      </div>
    </div>
  )
}

function ThinkingMessage() {
  return (
    <div className="flex flex-col items-start">
      <div className="flex items-center gap-2 mb-1.5">
        <span className="w-2 h-2 rounded-full bg-tertiary" />
        <span className="text-xs font-medium text-on-surface-dim">Next Ontology Engine</span>
      </div>
      <div className="thinking-gradient rounded-2xl rounded-tl-none px-5 py-4 max-w-[85%] w-80">
        <div className="flex items-center gap-3 mb-3">
          <RefreshCw size={14} className="text-tertiary animate-spin" />
          <span className="text-xs text-on-surface-dim">Processing query...</span>
        </div>
        <div className="space-y-2">
          <div className="h-1.5 bg-surface-high rounded-full overflow-hidden">
            <div className="h-full bg-primary/40 rounded-full animate-pulse" style={{ width: '65%' }} />
          </div>
          <div className="h-1.5 bg-surface-high rounded-full overflow-hidden">
            <div className="h-full bg-tertiary/40 rounded-full animate-pulse" style={{ width: '40%', animationDelay: '0.3s' }} />
          </div>
          <div className="h-1.5 bg-surface-high rounded-full overflow-hidden">
            <div className="h-full bg-primary/40 rounded-full animate-pulse" style={{ width: '80%', animationDelay: '0.6s' }} />
          </div>
        </div>
      </div>
    </div>
  )
}

function AiMessage({ content, showInsights }: { content: string; showInsights?: boolean }) {
  return (
    <div className="flex flex-col items-start">
      <div className="flex items-center gap-2 mb-1.5">
        <span className="w-2 h-2 rounded-full bg-tertiary" />
        <span className="text-xs font-medium text-on-surface-dim">Next Ontology Engine</span>
      </div>
      <div className="bg-surface-container rounded-2xl rounded-tl-none px-5 py-4 max-w-[85%]">
        <p className="text-sm text-on-surface leading-relaxed whitespace-pre-wrap">{content}</p>
      </div>

      {/* Bento Insights Grid */}
      {showInsights && (
        <div className="grid grid-cols-12 gap-3 mt-3 max-w-[85%] w-full">
          {/* Active Node Distribution */}
          <div className="col-span-7 bg-surface-container rounded-xl p-5">
            <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold mb-3">
              Active Node Distribution
            </p>
            <div className="flex items-end gap-4">
              <p className="text-4xl font-display font-extrabold text-primary">78%</p>
              <div className="flex-1">
                <div className="h-2 bg-surface-high rounded-full mb-1.5">
                  <div className="h-full bg-primary/60 rounded-full" style={{ width: '78%' }} />
                </div>
                <p className="text-xs text-on-surface-dim">of nodes actively connected</p>
              </div>
            </div>
          </div>

          {/* Anomalies Detected */}
          <div className="col-span-5 bg-surface-container rounded-xl p-5">
            <p className="text-[11px] uppercase tracking-widest text-on-surface-dim font-bold mb-3">
              Anomalies Detected
            </p>
            <div className="flex items-center gap-2 mb-2">
              <AlertTriangle size={14} className="text-warning" />
              <span className="text-sm font-medium text-warning">Low Risk</span>
            </div>
            <div className="h-2 bg-surface-high rounded-full">
              <div className="h-full bg-warning/50 rounded-full" style={{ width: '12%' }} />
            </div>
            <p className="text-xs text-on-surface-dim mt-1.5">2 potential issues flagged</p>
          </div>
        </div>
      )}
    </div>
  )
}
