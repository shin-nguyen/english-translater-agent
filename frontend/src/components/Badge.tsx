import type { ReactNode } from 'react'

type BadgeColor = 'indigo' | 'emerald' | 'amber' | 'gray' | 'rose'

const COLOR_CLASSES: Record<BadgeColor, string> = {
  indigo: 'bg-indigo-50 text-indigo-700 dark:bg-indigo-500/10 dark:text-indigo-300',
  emerald: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300',
  amber: 'bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300',
  gray: 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300',
  rose: 'bg-rose-50 text-rose-700 dark:bg-rose-500/10 dark:text-rose-300',
}

export function Badge({ children, color = 'gray' }: { children: ReactNode; color?: BadgeColor }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${COLOR_CLASSES[color]}`}>
      {children}
    </span>
  )
}
