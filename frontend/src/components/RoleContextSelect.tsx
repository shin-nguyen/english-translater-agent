interface Option {
  id: number
  name: string
}

interface RoleContextSelectProps {
  label: string
  value: number | null
  options: Option[]
  onChange: (value: number | null) => void
  placeholder?: string
}

export function RoleContextSelect({ label, value, options, onChange, placeholder = 'General (none)' }: RoleContextSelectProps) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-sm font-medium text-gray-700 dark:text-gray-300">{label}</span>
      <select
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value ? Number(e.target.value) : null)}
        className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm transition focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
      >
        <option value="">{placeholder}</option>
        {options.map((o) => (
          <option key={o.id} value={o.id}>
            {o.name}
          </option>
        ))}
      </select>
    </label>
  )
}
