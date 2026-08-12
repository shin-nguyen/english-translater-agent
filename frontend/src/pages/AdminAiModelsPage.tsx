import { useEffect, useState } from 'react'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { aiModelsApi } from '../api/aiModels'
import { ApiError } from '../api/client'
import type { AiModelConfigAdmin, AiProviderType } from '../api/types'
import { Modal } from '../components/Modal'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { Spinner } from '../components/Spinner'
import { Badge } from '../components/Badge'
import { useToast } from '../components/ToastProvider'

const PROVIDER_LABELS: Record<AiProviderType, string> = {
  ANTHROPIC: 'Anthropic Messages API',
  OPENAI_COMPATIBLE: 'OpenAI-Compatible Chat Completions',
}

interface FormState {
  label: string
  provider: AiProviderType
  baseUrl: string
  apiKey: string
  modelIdentifier: string
  apiVersion: string
  maxTokens: string
  timeoutSeconds: string
  enabled: boolean
  isDefault: boolean
}

const EMPTY_FORM: FormState = {
  label: '',
  provider: 'OPENAI_COMPATIBLE',
  baseUrl: '',
  apiKey: '',
  modelIdentifier: '',
  apiVersion: '',
  maxTokens: '1536',
  timeoutSeconds: '20',
  enabled: true,
  isDefault: false,
}

export function AdminAiModelsPage() {
  const { showToast } = useToast()

  const [configs, setConfigs] = useState<AiModelConfigAdmin[]>([])
  const [loading, setLoading] = useState(true)

  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<AiModelConfigAdmin | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [submitting, setSubmitting] = useState(false)

  const [deleteTarget, setDeleteTarget] = useState<AiModelConfigAdmin | null>(null)

  const load = () => {
    setLoading(true)
    aiModelsApi
      .list()
      .then(setConfigs)
      .catch(() => showToast('Could not load AI models', 'error'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    setEditing(null)
    setForm(EMPTY_FORM)
    setModalOpen(true)
  }

  const openEdit = (config: AiModelConfigAdmin) => {
    setEditing(config)
    setForm({
      label: config.label,
      provider: config.provider,
      baseUrl: config.baseUrl,
      apiKey: '',
      modelIdentifier: config.modelIdentifier,
      apiVersion: config.apiVersion ?? '',
      maxTokens: String(config.maxTokens),
      timeoutSeconds: String(config.timeoutSeconds),
      enabled: config.enabled,
      isDefault: config.isDefault,
    })
    setModalOpen(true)
  }

  const submit = async () => {
    if (!form.label.trim() || !form.baseUrl.trim() || !form.modelIdentifier.trim()) {
      showToast('Label, base URL, and model identifier are required', 'error')
      return
    }
    if (!editing && !form.apiKey.trim()) {
      showToast('API key is required when creating a new model', 'error')
      return
    }
    const payload = {
      label: form.label.trim(),
      provider: form.provider,
      baseUrl: form.baseUrl.trim(),
      apiKey: form.apiKey,
      modelIdentifier: form.modelIdentifier.trim(),
      apiVersion: form.provider === 'ANTHROPIC' ? form.apiVersion.trim() || null : null,
      maxTokens: Number(form.maxTokens) || 1536,
      timeoutSeconds: Number(form.timeoutSeconds) || 20,
      enabled: form.enabled,
      isDefault: form.isDefault,
    }
    setSubmitting(true)
    try {
      if (editing) {
        await aiModelsApi.update(editing.id, payload)
        showToast('AI model updated', 'success')
      } else {
        await aiModelsApi.create(payload)
        showToast('AI model created', 'success')
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
      await aiModelsApi.remove(deleteTarget.id)
      showToast('AI model deleted', 'success')
      setDeleteTarget(null)
      load()
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Could not delete.'
      showToast(message, 'error')
    }
  }

  return (
    <div className="mx-auto max-w-4xl">
      <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900 sm:p-6">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">AI Models</h2>
          <button
            onClick={openCreate}
            className="flex items-center gap-1.5 rounded-lg bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white transition hover:bg-indigo-700"
          >
            <Plus className="h-4 w-4" /> Add model
          </button>
        </div>

        {loading ? (
          <div className="flex justify-center py-8">
            <Spinner />
          </div>
        ) : configs.length === 0 ? (
          <p className="py-6 text-center text-sm text-gray-400">
            No AI models configured yet — add one so users can translate.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-gray-100 text-gray-500 dark:border-gray-800 dark:text-gray-400">
                  <th className="py-2 pr-4 font-medium">Label</th>
                  <th className="py-2 pr-4 font-medium">Provider</th>
                  <th className="py-2 pr-4 font-medium">Model</th>
                  <th className="py-2 pr-4 font-medium">Status</th>
                  <th className="w-20 py-2" />
                </tr>
              </thead>
              <tbody>
                {configs.map((config) => (
                  <tr key={config.id} className="border-b border-gray-50 last:border-0 dark:border-gray-800/60">
                    <td className="py-2.5 pr-4 font-medium text-gray-800 dark:text-gray-200">{config.label}</td>
                    <td className="py-2.5 pr-4 text-gray-500 dark:text-gray-400">{PROVIDER_LABELS[config.provider]}</td>
                    <td className="py-2.5 pr-4 text-gray-500 dark:text-gray-400">{config.modelIdentifier}</td>
                    <td className="py-2.5 pr-4">
                      <div className="flex flex-wrap gap-1">
                        <Badge color={config.enabled ? 'emerald' : 'rose'}>
                          {config.enabled ? 'Enabled' : 'Disabled'}
                        </Badge>
                        {config.isDefault && <Badge color="indigo">Default</Badge>}
                      </div>
                    </td>
                    <td className="py-2.5">
                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => openEdit(config)}
                          className="rounded-lg p-1.5 text-gray-400 transition hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200"
                          aria-label="Edit"
                        >
                          <Pencil className="h-4 w-4" />
                        </button>
                        <button
                          onClick={() => setDeleteTarget(config)}
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
        title={editing ? 'Edit AI model' : 'Add AI model'}
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
        <div className="flex max-h-[60vh] flex-col gap-3 overflow-y-auto pr-1">
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Label</span>
            <input
              value={form.label}
              onChange={(e) => setForm({ ...form, label: e.target.value })}
              className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Provider</span>
            <select
              value={form.provider}
              onChange={(e) => setForm({ ...form, provider: e.target.value as AiProviderType })}
              className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
            >
              <option value="OPENAI_COMPATIBLE">{PROVIDER_LABELS.OPENAI_COMPATIBLE}</option>
              <option value="ANTHROPIC">{PROVIDER_LABELS.ANTHROPIC}</option>
            </select>
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Base URL</span>
            <input
              value={form.baseUrl}
              onChange={(e) => setForm({ ...form, baseUrl: e.target.value })}
              placeholder="https://openrouter.ai/api/v1"
              className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-gray-700 dark:text-gray-300">API key</span>
            <input
              type="password"
              value={form.apiKey}
              onChange={(e) => setForm({ ...form, apiKey: e.target.value })}
              placeholder={editing ? 'Leave blank to keep existing key' : ''}
              className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Model identifier</span>
            <input
              value={form.modelIdentifier}
              onChange={(e) => setForm({ ...form, modelIdentifier: e.target.value })}
              placeholder="e.g. openai/gpt-oss-20b:free"
              className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>
          {form.provider === 'ANTHROPIC' && (
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium text-gray-700 dark:text-gray-300">API version</span>
              <input
                value={form.apiVersion}
                onChange={(e) => setForm({ ...form, apiVersion: e.target.value })}
                placeholder="2023-06-01"
                className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
              />
            </label>
          )}
          <div className="grid grid-cols-2 gap-3">
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Max tokens</span>
              <input
                type="number"
                min={1}
                value={form.maxTokens}
                onChange={(e) => setForm({ ...form, maxTokens: e.target.value })}
                className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
              />
            </label>
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Timeout (s)</span>
              <input
                type="number"
                min={1}
                value={form.timeoutSeconds}
                onChange={(e) => setForm({ ...form, timeoutSeconds: e.target.value })}
                className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
              />
            </label>
          </div>
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={form.enabled}
              onChange={(e) => setForm({ ...form, enabled: e.target.checked })}
              className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
            />
            <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
              Enabled (visible to users in the model picker)
            </span>
          </label>
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={form.isDefault}
              onChange={(e) => setForm({ ...form, isDefault: e.target.checked })}
              className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
            />
            <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Default model</span>
          </label>
        </div>
      </Modal>

      <ConfirmDialog
        open={!!deleteTarget}
        title="Delete AI model"
        message={`Are you sure you want to delete "${deleteTarget?.label}"? This cannot be undone.`}
        confirmLabel="Delete"
        onConfirm={confirmDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  )
}
