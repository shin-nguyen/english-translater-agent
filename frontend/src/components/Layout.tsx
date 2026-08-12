import { Cpu, LogOut, Languages, Moon, NotebookText, Sun, User, UserCog, Users } from 'lucide-react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useDarkMode } from '../hooks/useDarkMode'
import { useAuth } from './AuthProvider'

const BASE_NAV_ITEMS = [
  { to: '/', label: 'Translate', icon: Languages, end: true },
  { to: '/notes', label: 'Notes', icon: NotebookText, end: false },
  { to: '/roles-contexts', label: 'Roles & Contexts', icon: Users, end: false },
]

const ADMIN_NAV_ITEMS = [
  { to: '/admin/users', label: 'Users', icon: UserCog, end: false },
  { to: '/admin/ai-models', label: 'AI Models', icon: Cpu, end: false },
]

export function Layout() {
  const { dark, toggle } = useDarkMode()
  const { user, isAdmin, logout } = useAuth()
  const navigate = useNavigate()

  const navItems = isAdmin ? [...BASE_NAV_ITEMS, ...ADMIN_NAV_ITEMS] : BASE_NAV_ITEMS

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <header className="sticky top-0 z-30 border-b border-gray-200 bg-white/80 backdrop-blur dark:border-gray-800 dark:bg-gray-900/80">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3 sm:px-6">
          <div className="flex items-center gap-2 font-semibold text-gray-900 dark:text-gray-100">
            <Languages className="h-5 w-5 text-indigo-600 dark:text-indigo-400" />
            <span>EN Translator</span>
          </div>
          <nav className="flex items-center gap-1">
            {navItems.map(({ to, label, icon: Icon, end }) => (
              <NavLink
                key={to}
                to={to}
                end={end}
                className={({ isActive }) =>
                  `flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium transition ${
                    isActive
                      ? 'bg-indigo-50 text-indigo-700 dark:bg-indigo-500/10 dark:text-indigo-300'
                      : 'text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800'
                  }`
                }
              >
                <Icon className="h-4 w-4" />
                <span className="hidden sm:inline">{label}</span>
              </NavLink>
            ))}
            <NavLink
              to="/account"
              className={({ isActive }) =>
                `ml-1 flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium transition ${
                  isActive
                    ? 'bg-indigo-50 text-indigo-700 dark:bg-indigo-500/10 dark:text-indigo-300'
                    : 'text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800'
                }`
              }
            >
              <User className="h-4 w-4" />
              <span className="hidden sm:inline">{user?.displayName}</span>
            </NavLink>
            <button
              onClick={handleLogout}
              className="rounded-lg p-2 text-gray-500 transition hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
              aria-label="Log out"
              title="Log out"
            >
              <LogOut className="h-4 w-4" />
            </button>
            <button
              onClick={toggle}
              className="ml-1 rounded-lg p-2 text-gray-500 transition hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
              aria-label="Toggle dark mode"
            >
              {dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
            </button>
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6">
        <Outlet />
      </main>
    </div>
  )
}
