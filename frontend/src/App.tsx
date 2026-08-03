import { Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { TranslatePage } from './pages/TranslatePage'
import { NotesPage } from './pages/NotesPage'
import { NoteDetailPage } from './pages/NoteDetailPage'
import { RolesContextsPage } from './pages/RolesContextsPage'

function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<TranslatePage />} />
        <Route path="/notes" element={<NotesPage />} />
        <Route path="/notes/:id" element={<NoteDetailPage />} />
        <Route path="/roles-contexts" element={<RolesContextsPage />} />
      </Route>
    </Routes>
  )
}

export default App
