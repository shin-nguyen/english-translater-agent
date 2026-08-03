import { useEffect, useState } from 'react'
import { Search } from 'lucide-react'
import { notesApi } from '../api/notes'
import { rolesApi } from '../api/roles'
import { contextsApi } from '../api/contexts'
import type { Context, NoteSummary, Page, Role } from '../api/types'
import { NoteCard } from '../components/NoteCard'
import { RoleContextSelect } from '../components/RoleContextSelect'
import { Spinner } from '../components/Spinner'
import { useDebounce } from '../hooks/useDebounce'
import { useToast } from '../components/ToastProvider'

const PAGE_SIZE = 12

export function NotesPage() {
  const { showToast } = useToast()
  const [roles, setRoles] = useState<Role[]>([])
  const [contexts, setContexts] = useState<Context[]>([])
  const [roleId, setRoleId] = useState<number | null>(null)
  const [contextId, setContextId] = useState<number | null>(null)
  const [keyword, setKeyword] = useState('')
  const debouncedKeyword = useDebounce(keyword, 350)
  const [page, setPage] = useState(0)

  const [notes, setNotes] = useState<Page<NoteSummary> | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    rolesApi.list().then(setRoles).catch(() => undefined)
    contextsApi.list().then(setContexts).catch(() => undefined)
  }, [])

  useEffect(() => {
    setPage(0)
  }, [roleId, contextId, debouncedKeyword])

  useEffect(() => {
    setLoading(true)
    notesApi
      .search({ roleId, contextId, keyword: debouncedKeyword, page, size: PAGE_SIZE })
      .then(setNotes)
      .catch(() => showToast('Could not load notes', 'error'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roleId, contextId, debouncedKeyword, page])

  return (
    <div className="flex flex-col gap-6">
      <div className="rounded-2xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-800 dark:bg-gray-900 sm:p-5">
        <div className="relative">
          <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-gray-400" />
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="Search notes by title or content..."
            className="w-full rounded-xl border border-gray-200 bg-white py-2.5 pr-3 pl-9 text-sm text-gray-900 shadow-sm transition focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
          />
        </div>
        <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
          <RoleContextSelect label="Role" value={roleId} options={roles} onChange={setRoleId} placeholder="All roles" />
          <RoleContextSelect label="Context" value={contextId} options={contexts} onChange={setContextId} placeholder="All contexts" />
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center py-16">
          <Spinner className="h-6 w-6" />
        </div>
      ) : !notes || notes.content.length === 0 ? (
        <p className="py-16 text-center text-sm text-gray-400">No notes found.</p>
      ) : (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {notes.content.map((note) => (
              <NoteCard key={note.id} note={note} />
            ))}
          </div>

          {notes.totalPages > 1 && (
            <div className="flex items-center justify-center gap-3 text-sm text-gray-500 dark:text-gray-400">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={notes.first}
                className="rounded-lg px-3 py-1.5 font-medium transition hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40 dark:hover:bg-gray-800"
              >
                Previous
              </button>
              <span>
                Page {notes.number + 1} of {notes.totalPages}
              </span>
              <button
                onClick={() => setPage((p) => p + 1)}
                disabled={notes.last}
                className="rounded-lg px-3 py-1.5 font-medium transition hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40 dark:hover:bg-gray-800"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
