import { useEffect, useState } from 'react'
import { Check, Copy, Save, Sparkles } from 'lucide-react'
import { rolesApi } from '../api/roles'
import { contextsApi } from '../api/contexts'
import { translateApi } from '../api/translate'
import { notesApi } from '../api/notes'
import { aiModelsApi } from '../api/aiModels'
import { ApiError } from '../api/client'
import type { AiModelOption, Context, DetectedLanguage, Role, TranslateResponse } from '../api/types'
import { RoleContextSelect } from '../components/RoleContextSelect'
import { Spinner } from '../components/Spinner'
import { Badge } from '../components/Badge'
import { AnalysisList } from '../components/AnalysisList'
import { AlternativesTabs } from '../components/AlternativesTabs'
import { useToast } from '../components/ToastProvider'

const MAX_LENGTH = 2000

const LANGUAGE_LABEL: Record<DetectedLanguage, string> = {
  vi: 'Vietnamese detected',
  en: 'English detected',
  mixed: 'Mixed VI/EN detected',
}

const LANGUAGE_COLOR: Record<DetectedLanguage, 'amber' | 'emerald' | 'indigo'> = {
  vi: 'amber',
  en: 'emerald',
  mixed: 'indigo',
}

const PLACEHOLDER = `Ví dụ: "task này chắc phải mai mới xong được, hôm nay em bị vướng cái bug bên payment"\nhoặc: "Can we push the demo to next week, I still need more time to test"`

export function TranslatePage() {
  const { showToast } = useToast()

  const [roles, setRoles] = useState<Role[]>([])
  const [contexts, setContexts] = useState<Context[]>([])
  const [models, setModels] = useState<AiModelOption[]>([])
  const [roleId, setRoleId] = useState<number | null>(null)
  const [contextId, setContextId] = useState<number | null>(null)
  const [modelConfigId, setModelConfigId] = useState<number | null>(null)
  const [text, setText] = useState('')

  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<TranslateResponse | null>(null)
  const [title, setTitle] = useState('')
  const [copied, setCopied] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    rolesApi.list().then(setRoles).catch(() => showToast('Could not load roles', 'error'))
    contextsApi.list().then(setContexts).catch(() => showToast('Could not load contexts', 'error'))
    aiModelsApi
      .listEnabled()
      .then(setModels)
      .catch(() => showToast('Could not load AI models', 'error'))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleTranslate = async () => {
    const trimmed = text.trim()
    if (!trimmed) {
      showToast('Please enter some text first', 'error')
      return
    }
    if (!modelConfigId) {
      showToast('Please select an AI model first', 'error')
      return
    }
    setLoading(true)
    setSaved(false)
    try {
      const response = await translateApi.translate({ text: trimmed, roleId, contextId, modelConfigId })
      setResult(response)
      setTitle(response.suggestedTitle)
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Translation failed. Please try again.'
      showToast(message, 'error')
    } finally {
      setLoading(false)
    }
  }

  const handleCopy = async () => {
    if (!result) return
    await navigator.clipboard.writeText(result.mainResult)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  const handleSave = async () => {
    if (!result) return
    setSaving(true)
    try {
      await notesApi.create({
        title: title.trim() || result.suggestedTitle,
        originalText: text.trim(),
        detectedLanguage: result.detectedLanguage,
        englishResult: result.mainResult,
        alternatives: result.alternatives,
        analysis: result.analysis,
        roleId,
        contextId,
      })
      setSaved(true)
      showToast('Note saved', 'success')
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Could not save note.'
      showToast(message, 'error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900 sm:p-6">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <RoleContextSelect label="Role" value={roleId} options={roles} onChange={setRoleId} />
          <RoleContextSelect label="Context" value={contextId} options={contexts} onChange={setContextId} />
          <RoleContextSelect
            label="AI Model"
            value={modelConfigId}
            options={models.map((m) => ({ id: m.id, name: m.label }))}
            onChange={setModelConfigId}
            placeholder="Select a model..."
          />
        </div>

        {models.length === 0 && (
          <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700 dark:bg-amber-500/10 dark:text-amber-300">
            No AI models configured yet — ask an admin to add one in AI Models.
          </p>
        )}

        <div className="mt-4">
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value.slice(0, MAX_LENGTH))}
            placeholder={PLACEHOLDER}
            rows={5}
            className="w-full resize-y rounded-xl border border-gray-200 bg-white px-3 py-2.5 text-sm text-gray-900 shadow-sm transition placeholder:text-gray-400 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100 dark:placeholder:text-gray-600"
          />
          <div className="mt-1 text-right text-xs text-gray-400">
            {text.length} / {MAX_LENGTH}
          </div>
        </div>

        <button
          onClick={handleTranslate}
          disabled={loading || !text.trim() || !modelConfigId}
          className="mt-2 flex w-full items-center justify-center gap-2 rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {loading ? <Spinner className="h-4 w-4" /> : <Sparkles className="h-4 w-4" />}
          {loading ? 'Translating...' : 'Dịch'}
        </button>
      </div>

      {result && (
        <div className="animate-fade-in-up flex flex-col gap-5 rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900 sm:p-6">
          <div className="flex items-center justify-between gap-2">
            <Badge color={LANGUAGE_COLOR[result.detectedLanguage]}>{LANGUAGE_LABEL[result.detectedLanguage]}</Badge>
            <button
              onClick={handleCopy}
              className="flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm font-medium text-gray-600 transition hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
            >
              {copied ? <Check className="h-4 w-4 text-emerald-500" /> : <Copy className="h-4 w-4" />}
              {copied ? 'Copied' : 'Copy'}
            </button>
          </div>

          <p className="text-lg leading-relaxed font-medium text-gray-900 dark:text-gray-100">{result.mainResult}</p>

          {result.alternatives.length > 0 && (
            <div>
              <h3 className="mb-2 text-sm font-semibold text-gray-700 dark:text-gray-300">Alternatives</h3>
              <AlternativesTabs alternatives={result.alternatives} />
            </div>
          )}

          {result.analysis.length > 0 && (
            <div>
              <h3 className="mb-2 text-sm font-semibold text-gray-700 dark:text-gray-300">Analysis & suggestions</h3>
              <AnalysisList items={result.analysis} />
            </div>
          )}

          <div className="border-t border-gray-100 pt-4 dark:border-gray-800">
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Note title</span>
              <input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm transition focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
              />
            </label>
            <button
              onClick={handleSave}
              disabled={saving}
              className="mt-3 flex w-full items-center justify-center gap-2 rounded-xl bg-gray-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-indigo-600 dark:hover:bg-indigo-700 sm:w-auto"
            >
              {saving ? <Spinner className="h-4 w-4" /> : <Save className="h-4 w-4" />}
              {saved ? 'Saved as note' : 'Save as note'}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
