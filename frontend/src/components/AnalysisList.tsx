import type { AnalysisPoint } from '../api/types'

export function AnalysisList({ items }: { items: AnalysisPoint[] }) {
  if (items.length === 0) {
    return null
  }

  return (
    <ul className="space-y-3">
      {items.map((item, index) => (
        <li
          key={index}
          className="rounded-xl border border-gray-200 bg-gray-50 p-3 dark:border-gray-800 dark:bg-gray-800/50"
        >
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
            <span className="text-gray-500 line-through decoration-rose-400/70 dark:text-gray-400">
              {item.original}
            </span>
            <span className="text-gray-400 dark:text-gray-500">→</span>
            <span className="font-medium text-emerald-700 dark:text-emerald-400">{item.improved}</span>
          </div>
          <p className="mt-1.5 text-sm text-gray-500 dark:text-gray-400">{item.reason}</p>
        </li>
      ))}
    </ul>
  )
}
