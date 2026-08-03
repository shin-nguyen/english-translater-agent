import { Link } from 'react-router-dom'
import { Calendar } from 'lucide-react'
import type { NoteSummary } from '../api/types'
import { Badge } from './Badge'

export function NoteCard({ note }: { note: NoteSummary }) {
  const date = new Date(note.createdAt).toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })

  return (
    <Link
      to={`/notes/${note.id}`}
      className="flex flex-col gap-3 rounded-2xl border border-gray-200 bg-white p-4 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md dark:border-gray-800 dark:bg-gray-900"
    >
      <h3 className="line-clamp-2 text-base font-semibold text-gray-900 dark:text-gray-100">{note.title}</h3>
      <div className="space-y-1 text-sm text-gray-500 dark:text-gray-400">
        <p className="line-clamp-2">{note.originalTextExcerpt}</p>
        <p className="line-clamp-2 text-gray-700 dark:text-gray-300">{note.englishResultExcerpt}</p>
      </div>
      <div className="mt-auto flex flex-wrap items-center gap-2 pt-1">
        {note.role && <Badge color="indigo">{note.role.name}</Badge>}
        {note.context && <Badge color="emerald">{note.context.name}</Badge>}
        <span className="ml-auto flex items-center gap-1 text-xs text-gray-400">
          <Calendar className="h-3.5 w-3.5" />
          {date}
        </span>
      </div>
    </Link>
  )
}
