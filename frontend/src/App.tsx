import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { BaseLayout } from './layouts/BaseLayout.tsx'
import { Autorizacoes } from './pages/Autorizacoes'
import { Operacoes } from './pages/Operacoes'
import './App.css'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<BaseLayout />}>
          <Route index element={<Navigate to="/autorizacoes" replace />} />
          <Route path="autorizacoes" element={<Autorizacoes />} />
          <Route path="operacoes" element={<Operacoes />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
