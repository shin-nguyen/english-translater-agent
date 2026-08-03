import { useState } from 'react'
import { Check, Copy } from 'lucide-react'
import type { Alternative } from '../api/types'

export function AlternativesTabs({ alternatives }: { alternatives: Alternative[] }) {
  const [active, setActive] = useState(0)
  const [copied, setCopied] = useState(false)

  if (alternatives.length === 0) {
    return null
  }

  const current = alternatives[active]

  const copy = async () => {
    await navigator.clipboard.writeText(current.text)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <div>
      <div className="flex gap-1 border-b border-gray-200 dark:border-gray-800">
        {alternatives.map((alt, index) => (
          <button
            key={index}
            onClick={() => setActive(index)}
            className={`-mb-px border-b-2 px-3 py-1.5 text-sm font-medium transition ${
              active === index
                ? 'border-indigo-600 text-indigo-700 dark:text-indigo-300'
                : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400'
            }`}
          >
            {alt.style || `Option ${index + 1}`}
          </button>
        ))}
      </div>
      <div className="mt-3 flex items-start justify-between gap-3 rounded-xl bg-gray-50 p-3 dark:bg-gray-900">
        <div>
          <p className="text-sm text-gray-700 dark:text-gray-200">{current.text}</p>
          {current.reason && (
            <p className="mt-1.5 text-xs text-gray-500 dark:text-gray-400">{current.reason}</p>
          )}
        </div>
        <button
          onClick={copy}
          className="shrink-0 text-gray-400 transition hover:text-indigo-600 dark:hover:text-indigo-400"
          aria-label="Copy alternative"
        >
          {copied ? <Check className="h-4 w-4 text-emerald-500" /> : <Copy className="h-4 w-4" />}
        </button>
      </div>
    </div>
  )
}
