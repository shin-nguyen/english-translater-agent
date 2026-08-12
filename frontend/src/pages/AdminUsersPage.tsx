import { useEffect, useState } from 'react'
import { Plus, ShieldCheck, ShieldOff, Trash2, UserCheck, UserX } from 'lucide-react'
import { usersApi } from '../api/users'
import { ApiError } from '../api/client'
import type { AppRole, UserSummary } from '../api/types'
import { Modal } from '../components/Modal'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { Spinner } from '../components/Spinner'
import { Badge } from '../components/Badge'
import { useToast } from '../components/ToastProvider'

export function AdminUsersPage() {
  const { showToast } = useToast()

  const [users, setUsers] = useState<UserSummary[]>([])
  const [loading, setLoading] = useState(true)

  const [modalOpen, setModalOpen] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [appRole, setAppRole] = useState<AppRole>('USER')
  const [submitting, setSubmitting] = useState(false)

  const [deleteTarget, setDeleteTarget] = useState<UserSummary | null>(null)

  const load = () => {
    setLoading(true)
    usersApi
      .list()
      .then(setUsers)
      .catch(() => showToast('Could not load users', 'error'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    setEmail('')
    setPassword('')
    setDisplayName('')
    setAppRole('USER')
    setModalOpen(true)
  }

  const submitCreate = async () => {
    if (!email.trim() || !displayName.trim() || password.length < 8) {
      showToast('Fill in email, display name, and an 8+ character password', 'error')
      return
    }
    setSubmitting(true)
    try {
      await usersApi.create({ email: email.trim(), password, displayName: displayName.trim(), appRole })
      showToast('User created', 'success')
      setModalOpen(false)
      load()
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Could not create user.'
      showToast(message, 'error')
    } finally {
      setSubmitting(false)
    }
  }

  const toggleRole = async (user: UserSummary) => {
    const nextRole: AppRole = user.appRole === 'ADMIN' ? 'USER' : 'ADMIN'
    try {
      await usersApi.setRole(user.id, nextRole)
      showToast(`${user.displayName} is now ${nextRole}`, 'success')
      load()
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Could not update role.'
      showToast(message, 'error')
    }
  }

  const toggleEnabled = async (user: UserSummary) => {
    try {
      await usersApi.setEnabled(user.id, !user.enabled)
      showToast(`${user.displayName} ${user.enabled ? 'disabled' : 're-enabled'}`, 'success')
      load()
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Could not update status.'
      showToast(message, 'error')
    }
  }

  const confirmDelete = async () => {
    if (!deleteTarget) return
    try {
      await usersApi.remove(deleteTarget.id)
      showToast('User deleted', 'success')
      setDeleteTarget(null)
      load()
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Could not delete user.'
      showToast(message, 'error')
    }
  }

  return (
    <div className="mx-auto max-w-4xl">
      <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900 sm:p-6">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">Users</h2>
          <button
            onClick={openCreate}
            className="flex items-center gap-1.5 rounded-lg bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white transition hover:bg-indigo-700"
          >
            <Plus className="h-4 w-4" /> Add user
          </button>
        </div>

        {loading ? (
          <div className="flex justify-center py-8">
            <Spinner />
          </div>
        ) : users.length === 0 ? (
          <p className="py-6 text-center text-sm text-gray-400">No users yet.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-gray-100 text-gray-500 dark:border-gray-800 dark:text-gray-400">
                  <th className="py-2 pr-4 font-medium">Name</th>
                  <th className="py-2 pr-4 font-medium">Email</th>
                  <th className="py-2 pr-4 font-medium">Role</th>
                  <th className="py-2 pr-4 font-medium">Status</th>
                  <th className="w-32 py-2" />
                </tr>
              </thead>
              <tbody>
                {users.map((user) => (
                  <tr key={user.id} className="border-b border-gray-50 last:border-0 dark:border-gray-800/60">
                    <td className="py-2.5 pr-4 font-medium text-gray-800 dark:text-gray-200">{user.displayName}</td>
                    <td className="py-2.5 pr-4 text-gray-500 dark:text-gray-400">{user.email}</td>
                    <td className="py-2.5 pr-4">
                      <Badge color={user.appRole === 'ADMIN' ? 'indigo' : 'gray'}>{user.appRole}</Badge>
                    </td>
                    <td className="py-2.5 pr-4">
                      <Badge color={user.enabled ? 'emerald' : 'rose'}>{user.enabled ? 'Enabled' : 'Disabled'}</Badge>
                    </td>
                    <td className="py-2.5">
                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => toggleRole(user)}
                          className="rounded-lg p-1.5 text-gray-400 transition hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200"
                          aria-label={user.appRole === 'ADMIN' ? 'Demote to user' : 'Promote to admin'}
                          title={user.appRole === 'ADMIN' ? 'Demote to user' : 'Promote to admin'}
                        >
                          {user.appRole === 'ADMIN' ? (
                            <ShieldOff className="h-4 w-4" />
                          ) : (
                            <ShieldCheck className="h-4 w-4" />
                          )}
                        </button>
                        <button
                          onClick={() => toggleEnabled(user)}
                          className="rounded-lg p-1.5 text-gray-400 transition hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200"
                          aria-label={user.enabled ? 'Disable user' : 'Enable user'}
                          title={user.enabled ? 'Disable user' : 'Enable user'}
                        >
                          {user.enabled ? <UserX className="h-4 w-4" /> : <UserCheck className="h-4 w-4" />}
                        </button>
                        <button
                          onClick={() => setDeleteTarget(user)}
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
      </div>

      <Modal
        open={modalOpen}
        title="Add user"
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
              onClick={submitCreate}
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
            <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Display name</span>
            <input
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Email</span>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Password</span>
            <input
              type="password"
              minLength={8}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Role</span>
            <select
              value={appRole}
              onChange={(e) => setAppRole(e.target.value as AppRole)}
              className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
            >
              <option value="USER">User</option>
              <option value="ADMIN">Admin</option>
            </select>
          </label>
        </div>
      </Modal>

      <ConfirmDialog
        open={!!deleteTarget}
        title="Delete user"
        message={`Are you sure you want to delete "${deleteTarget?.displayName}"? This also deletes all of their notes and cannot be undone.`}
        confirmLabel="Delete"
        onConfirm={confirmDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  )
}
