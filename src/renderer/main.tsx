import React from 'react'
import { createRoot } from 'react-dom/client'

import { App } from './App'
import './styles.css'

// Mark the native surface before React mounts. The overlay window is only a
// few pixels larger than the pill, so even one canvas-colored first frame is
// visible as a square behind the rounded control on Windows.
const rendererWindow = new URLSearchParams(window.location.search).get('window') === 'overlay' ? 'overlay' : 'main'
document.documentElement.dataset.window = rendererWindow
document.body.dataset.window = rendererWindow

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
