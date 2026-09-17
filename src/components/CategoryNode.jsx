import { Handle, Position } from '@xyflow/react'

export default function CategoryNode({ data }) {
    const { label, color } = data

    return (
        <div
            className="relative px-4 sm:px-5 py-2 sm:py-2.5 transition-all duration-300 cursor-pointer flex items-center justify-center min-w-[90px] sm:min-w-[120px] rounded-lg"
            style={{
                background: 'rgba(15, 25, 60, 0.8)',
                backdropFilter: 'blur(8px)',
                border: `2px solid ${color.border}`,
                boxShadow: `0 0 24px ${color.border}40, 0 4px 12px rgba(0,0,0,0.3)`,
            }}
        >
            <Handle type="target" position={Position.Left} className="!bg-transparent !border-0 !w-0 !h-0" />

            <h3 className="text-xs sm:text-sm font-bold tracking-widest uppercase" style={{ color: color.border, textShadow: `0 0 12px ${color.border}60` }}>
                {label}
            </h3>

            <Handle type="source" position={Position.Right} className="!bg-transparent !border-0 !w-0 !h-0" />
        </div>
    )
}
