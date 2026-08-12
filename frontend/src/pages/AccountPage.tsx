import { useState, type FormEvent } from 'react'
import { useAuth } from '../components/AuthProvider'
import { Badge } from '../components/Badge'
import { Spinner } from '../components/Spinner'
import { useToast } from '../components/ToastProvider'
import { ApiError } from '../api/client'

export function AccountPage() {
  const { user, changePassword } = useAuth()
  const { showToast } = useToast()

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (newPassword.length < 8) {
      showToast('New password must be at least 8 characters', 'error')
      return
    }
    if (newPassword !== confirmPassword) {
      showToast('New password and confirmation do not match', 'error')
      return
    }
    setSubmitting(true)
    try {
      await changePassword(currentPassword, newPassword)
      showToast('Password updated', 'success')
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Could not change password.'
      showToast(message, 'error')
    } finally {
      setSubmitting(false)
    }
  }

  if (!user) return null

  return (
    <div className="mx-auto flex max-w-lg flex-col gap-6">
      <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900 sm:p-6">
        <h2 className="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">Account</h2>
        <dl className="flex flex-col gap-2 text-sm">
          <div className="flex justify-between">
            <dt className="text-gray-500 dark:text-gray-400">Display name</dt>
            <dd className="font-medium text-gray-800 dark:text-gray-200">{user.displayName}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-gray-500 dark:text-gray-400">Email</dt>
            <dd className="font-medium text-gray-800 dark:text-gray-200">{user.email}</dd>
          </div>
          <div className="flex items-center justify-between">
            <dt className="text-gray-500 dark:text-gray-400">Role</dt>
            <dd>
              <Badge color={user.appRole === 'ADMIN' ? 'indigo' : 'gray'}>{user.appRole}</Badge>
            </dd>
          </div>
        </dl>
      </div>

      <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900 sm:p-6">
        <h2 className="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">Change password</h2>
        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Current password</span>
            <input
              type="password"
              required
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-gray-700 dark:text-gray-300">New password</span>
            <input
              type="password"
              required
              minLength={8}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Confirm new password</span>
            <input
              type="password"
              required
              minLength={8}
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>
          <button
            type="submit"
            disabled={submitting}
            className="mt-2 flex items-center justify-center gap-2 self-start rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-indigo-700 disabled:opacity-50"
          >
            {submitting && <Spinner className="h-4 w-4" />} Update password
          </button>
        </form>
      </div>
    </div>
  )
}
