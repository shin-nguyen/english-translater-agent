import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Check, Copy, Save, Trash2 } from 'lucide-react'
import { notesApi } from '../api/notes'
import { ApiError } from '../api/client'
import type { Alternative, NoteDetail } from '../api/types'
import { Badge } from '../components/Badge'
import { AnalysisList } from '../components/AnalysisList'
import { Spinner } from '../components/Spinner'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { useToast } from '../components/ToastProvider'

const LANGUAGE_COLOR: Record<string, 'amber' | 'emerald' | 'indigo' | 'gray'> = {
  vi: 'amber',
  en: 'emerald',
  mixed: 'indigo',
}

export function NoteDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { showToast } = useToast()

  const [note, setNote] = useState<NoteDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [title, setTitle] = useState('')
  const [englishResult, setEnglishResult] = useState('')
  const [saving, setSaving] = useState(false)
  const [copied, setCopied] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    notesApi
      .get(Number(id))
      .then((n) => {
        setNote(n)
        setTitle(n.title)
        setEnglishResult(n.englishResult)
      })
      .catch(() => showToast('Could not load this note', 'error'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  const useAlternativeAsMain = (alt: Alternative) => {
    if (!note) return
    const newAlternatives = note.alternatives.filter((a) => a !== alt)
    newAlternatives.push({ text: englishResult, style: '', reason: '' })
    setNote({ ...note, alternatives: newAlternatives })
    setEnglishResult(alt.text)
  }

  const handleCopy = async () => {
    await navigator.clipboard.writeText(englishResult)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  const handleSave = async () => {
    if (!note) return
    setSaving(true)
    try {
      const updated = await notesApi.update(note.id, {
        title: title.trim(),
        originalText: note.originalText,
        detectedLanguage: note.detectedLanguage,
        englishResult,
        alternatives: note.alternatives,
        analysis: note.analysis,
        roleId: note.role?.id ?? null,
        contextId: note.context?.id ?? null,
      })
      setNote(updated)
      showToast('Note updated', 'success')
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Could not update note.'
      showToast(message, 'error')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (!note) return
    try {
      await notesApi.remove(note.id)
      showToast('Note deleted', 'success')
      navigate('/notes')
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Could not delete note.'
      showToast(message, 'error')
    }
  }

  if (loading) {
    return (
      <div className="flex justify-center py-16">
        <Spinner className="h-6 w-6" />
      </div>
    )
  }

  if (!note) {
    return <p className="py-16 text-center text-sm text-gray-400">Note not found.</p>
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-5">
      <button
        onClick={() => navigate('/notes')}
        className="flex w-fit items-center gap-1.5 text-sm font-medium text-gray-500 transition hover:text-gray-800 dark:text-gray-400 dark:hover:text-gray-200"
      >
        <ArrowLeft className="h-4 w-4" /> Back to notes
      </button>

      <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900 sm:p-6">
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          className="w-full border-none bg-transparent text-xl font-semibold text-gray-900 outline-none dark:text-gray-100"
        />

        <div className="mt-2 flex flex-wrap items-center gap-2">
          <Badge color={LANGUAGE_COLOR[note.detectedLanguage] ?? 'gray'}>{note.detectedLanguage.toUpperCase()}</Badge>
          {note.role && <Badge color="indigo">{note.role.name}</Badge>}
          {note.context && <Badge color="emerald">{note.context.name}</Badge>}
          <span className="ml-auto text-xs text-gray-400">{new Date(note.createdAt).toLocaleString('vi-VN')}</span>
        </div>

        <div className="mt-5">
          <h3 className="mb-1.5 text-sm font-semibold text-gray-700 dark:text-gray-300">Original</h3>
          <p className="rounded-xl bg-gray-50 p-3 text-sm text-gray-600 dark:bg-gray-800/50 dark:text-gray-300">
            {note.originalText}
          </p>
        </div>

        <div className="mt-5">
          <div className="mb-1.5 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300">English result (editable)</h3>
            <button
              onClick={handleCopy}
              className="flex items-center gap-1.5 text-sm font-medium text-gray-500 transition hover:text-indigo-600 dark:hover:text-indigo-400"
            >
              {copied ? <Check className="h-4 w-4 text-emerald-500" /> : <Copy className="h-4 w-4" />}
              {copied ? 'Copied' : 'Copy'}
            </button>
          </div>
          <textarea
            value={englishResult}
            onChange={(e) => setEnglishResult(e.target.value)}
            rows={4}
            className="w-full resize-y rounded-xl border border-gray-200 bg-white px-3 py-2.5 text-base font-medium text-gray-900 shadow-sm transition focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
          />
        </div>

        {note.alternatives.length > 0 && (
          <div className="mt-5">
            <h3 className="mb-1.5 text-sm font-semibold text-gray-700 dark:text-gray-300">
              Alternatives (click to use as main)
            </h3>
            <div className="flex flex-col gap-2">
              {note.alternatives.map((alt, i) => (
                <button
                  key={i}
                  onClick={() => useAlternativeAsMain(alt)}
                  className="rounded-xl bg-gray-50 p-3 text-left text-sm text-gray-600 transition hover:bg-indigo-50 hover:text-indigo-700 dark:bg-gray-800/50 dark:text-gray-300 dark:hover:bg-indigo-500/10 dark:hover:text-indigo-300"
                >
                  {alt.style && <span className="mr-2 text-xs font-semibold uppercase text-gray-400">{alt.style}</span>}
                  {alt.text}
                  {alt.reason && <span className="mt-1 block text-xs text-gray-400">{alt.reason}</span>}
                </button>
              ))}
            </div>
          </div>
        )}

        {note.analysis.length > 0 && (
          <div className="mt-5">
            <h3 className="mb-2 text-sm font-semibold text-gray-700 dark:text-gray-300">Analysis & suggestions</h3>
            <AnalysisList items={note.analysis} />
          </div>
        )}

        <div className="mt-6 flex items-center justify-between border-t border-gray-100 pt-4 dark:border-gray-800">
          <button
            onClick={() => setConfirmDelete(true)}
            className="flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium text-rose-600 transition hover:bg-rose-50 dark:hover:bg-rose-500/10"
          >
            <Trash2 className="h-4 w-4" /> Delete
          </button>
          <button
            onClick={handleSave}
            disabled={saving}
            className="flex items-center gap-2 rounded-xl bg-gray-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-gray-800 disabled:opacity-50 dark:bg-indigo-600 dark:hover:bg-indigo-700"
          >
            {saving ? <Spinner className="h-4 w-4" /> : <Save className="h-4 w-4" />}
            Save changes
          </button>
        </div>
      </div>

      <ConfirmDialog
        open={confirmDelete}
        title="Delete note"
        message="Are you sure you want to delete this note? This cannot be undone."
        confirmLabel="Delete"
        onConfirm={handleDelete}
        onCancel={() => setConfirmDelete(false)}
      />
    </div>
  )
}
