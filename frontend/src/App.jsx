import { useEffect, useState } from 'react'
import './App.css'

function App() {
  const [status, setStatus] = useState('loading...')

  useEffect(() => {
    fetch('/api/health')
      .then((r) => r.text())
      .then(setStatus)
      .catch(() => setStatus('error'))
  }, [])

  return (
    <div>
      <h1>FDP</h1>
      <p>Backend status: {status}</p>
    </div>
  )
}

export default App
