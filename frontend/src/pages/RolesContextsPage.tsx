import { useEffect, useState } from 'react'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { rolesApi } from '../api/roles'
import { contextsApi } from '../api/contexts'
import { ApiError } from '../api/client'
import { Modal } from '../components/Modal'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { Spinner } from '../components/Spinner'
import { useToast } from '../components/ToastProvider'

interface EntityItem {
  id: number
  name: string
  description: string | null
}

interface EntityApi {
  list: () => Promise<EntityItem[]>
  create: (payload: { name: string; description: string | null }) => Promise<EntityItem>
  update: (id: number, payload: { name: string; description: string | null }) => Promise<EntityItem>
  remove: (id: number) => Promise<void>
}

function EntitySection({ title, api }: { title: string; api: EntityApi }) {
  const { showToast } = useToast()
  const singular = title.slice(0, -1)

  const [items, setItems] = useState<EntityItem[]>([])
  const [loading, setLoading] = useState(true)

  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<EntityItem | null>(null)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const [deleteTarget, setDeleteTarget] = useState<EntityItem | null>(null)

  const load = () => {
    setLoading(true)
    api
      .list()
      .then(setItems)
      .catch(() => showToast(`Could not load ${title.toLowerCase()}`, 'error'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    setEditing(null)
    setName('')
    setDescription('')
    setModalOpen(true)
  }

  const openEdit = (item: EntityItem) => {
    setEditing(item)
    setName(item.name)
    setDescription(item.description ?? '')
    setModalOpen(true)
  }

  const submit = async () => {
    if (!name.trim()) {
      showToast('Name is required', 'error')
      return
    }
    setSubmitting(true)
    try {
      if (editing) {
        await api.update(editing.id, { name: name.trim(), description: description.trim() || null })
        showToast(`${singular} updated`, 'success')
      } else {
        await api.create({ name: name.trim(), description: description.trim() || null })
        showToast(`${singular} created`, 'success')
      }
      setModalOpen(false)
      load()
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Something went wrong.'
      showToast(message, 'error')
    } finally {
      setSubmitting(false)
    }
  }

  const confirmDelete = async () => {
    if (!deleteTarget) return
    try {
      await api.remove(deleteTarget.id)
      showToast(`${singular} deleted`, 'success')
      setDeleteTarget(null)
      load()
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Could not delete.'
      showToast(message, 'error')
    }
  }

  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900 sm:p-6">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">{title}</h2>
        <button
          onClick={openCreate}
          className="flex items-center gap-1.5 rounded-lg bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white transition hover:bg-indigo-700"
        >
          <Plus className="h-4 w-4" /> Add
        </button>
      </div>

      {loading ? (
        <div className="flex justify-center py-8">
          <Spinner />
        </div>
      ) : items.length === 0 ? (
        <p className="py-6 text-center text-sm text-gray-400">No {title.toLowerCase()} yet.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-gray-100 text-gray-500 dark:border-gray-800 dark:text-gray-400">
                <th className="py-2 pr-4 font-medium">Name</th>
                <th className="py-2 pr-4 font-medium">Description</th>
                <th className="w-20 py-2" />
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id} className="border-b border-gray-50 last:border-0 dark:border-gray-800/60">
                  <td className="py-2.5 pr-4 font-medium text-gray-800 dark:text-gray-200">{item.name}</td>
                  <td className="py-2.5 pr-4 text-gray-500 dark:text-gray-400">{item.description}</td>
                  <td className="py-2.5">
                    <div className="flex items-center gap-1">
                      <button
                        onClick={() => openEdit(item)}
                        className="rounded-lg p-1.5 text-gray-400 transition hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200"
                        aria-label="Edit"
                      >
                        <Pencil className="h-4 w-4" />
                      </button>
                      <button
                        onClick={() => setDeleteTarget(item)}
                        className="rounded-lg p-1.5 text-gray-400 transition hover:bg-rose-50 hover:text-rose-600 dark:hover:bg-rose-500/10"
                        aria-label="Delete"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal
        open={modalOpen}
        title={editing ? `Edit ${singular}` : `Add ${singular}`}
        onClose={() => setModalOpen(false)}
        footer={
          <>
            <button
              onClick={() => setModalOpen(false)}
              className="rounded-lg px-4 py-2 text-sm font-medium text-gray-600 transition hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
            >
              Cancel
            </button>
            <button
              onClick={submit}
              disabled={submitting}
              className="flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-indigo-700 disabled:opacity-50"
            >
              {submitting && <Spinner className="h-4 w-4" />} Save
            </button>
          </>
        }
      >
        <div className="flex flex-col gap-3">
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Name</span>
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Description</span>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
              className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>
        </div>
      </Modal>

      <ConfirmDialog
        open={!!deleteTarget}
        title={`Delete ${singular}`}
        message={`Are you sure you want to delete "${deleteTarget?.name}"? This cannot be undone.`}
        confirmLabel="Delete"
        onConfirm={confirmDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  )
}

export function RolesContextsPage() {
  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">
      <EntitySection title="Roles" api={rolesApi} />
      <EntitySection title="Contexts" api={contextsApi} />
    </div>
  )
}
