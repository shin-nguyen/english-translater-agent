import { Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { RequireAdmin, RequireAuth } from './components/RequireAuth'
import { TranslatePage } from './pages/TranslatePage'
import { NotesPage } from './pages/NotesPage'
import { NoteDetailPage } from './pages/NoteDetailPage'
import { RolesContextsPage } from './pages/RolesContextsPage'
import { LoginPage } from './pages/LoginPage'
import { SignupPage } from './pages/SignupPage'
import { AccountPage } from './pages/AccountPage'
import { AdminUsersPage } from './pages/AdminUsersPage'
import { AdminAiModelsPage } from './pages/AdminAiModelsPage'

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route path="/" element={<TranslatePage />} />
          <Route path="/notes" element={<NotesPage />} />
          <Route path="/notes/:id" element={<NoteDetailPage />} />
          <Route path="/roles-contexts" element={<RolesContextsPage />} />
          <Route path="/account" element={<AccountPage />} />
          <Route element={<RequireAdmin />}>
            <Route path="/admin/users" element={<AdminUsersPage />} />
            <Route path="/admin/ai-models" element={<AdminAiModelsPage />} />
          </Route>
        </Route>
      </Route>
    </Routes>
  )
}

export default App
