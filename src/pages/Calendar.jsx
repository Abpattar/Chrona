import { useState, useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import CalendarMindmap from '../components/CalendarMindmap'
import CalendarView from '../components/CalendarView'
import RepairPanel from '../components/RepairPanel'

export default function Calendar({ initialView }) {
  const location = useLocation()
  const [viewMode, setViewMode] = useState(() => {
    if (initialView) return initialView
    if (location.pathname === '/week') return 'timeline'
    return 'mindmap'
  })
  const [repairTask, setRepairTask] = useState(null)

  useEffect(() => {
    if (location.pathname === '/week') {
      setViewMode('timeline')
    } else if (location.pathname === '/calendar') {
      setViewMode('mindmap')
    }
  }, [location.pathname])

  return (
    <div className="flex-1 w-full h-full relative overflow-hidden min-h-0">
      {/* Pure Canvas View — Zero extra nav rows */}
      {viewMode === 'mindmap' ? (
        <CalendarMindmap onOpenRepair={(task) => setRepairTask(task)} />
      ) : (
        <div className="w-full h-full overflow-y-auto">
          <CalendarView />
        </div>
      )}

      {/* Repair Panel Drawer */}
      {repairTask && (
        <RepairPanel
          task={repairTask}
          onClose={() => setRepairTask(null)}
          onRepaired={() => setRepairTask(null)}
        />
      )}
    </div>
  )
}
