import { useState } from 'react'
import CalendarMindmap from '../components/CalendarMindmap'
import RepairPanel from '../components/RepairPanel'

export default function Calendar() {
  const [repairTask, setRepairTask] = useState(null)

  return (
    <div className="flex-1 flex flex-col overflow-hidden w-full h-full">
      <CalendarMindmap onOpenRepair={(task) => setRepairTask(task)} />
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
