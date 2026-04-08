// ============================================================
// UpcomingView.jsx — Elegant Daily/Weekly Planner View
// Styled like a Google Teams Day view but in Analog theme
// ============================================================

'use client';

/** Mock Priority Hub Stats */
const STATS = [
  { label: 'TOTAL EVENTS', value: '1', icon: 'event' },
  { label: 'CRITICAL', value: '1', icon: 'priority_high' },
  { label: 'TODAY', value: '1', icon: 'push_pin' },
  { label: 'CONFLICTS', value: '0', icon: 'warning' }
];

/** 
 * Mock Timeline Items for the "Google Teams" Grid 
 * In a real app, this would be computed from task dates
 */
const TIMELINE_BLOCKS = [
  { 
    id: 't1', 
    title: 'MATH EXAM', 
    time: '12:58 AM → 2:28 AM', 
    category: 'EXAM', 
    top: 50, // pixel offset
    height: 120, 
    color: '#ad170c',
    icon: 'rocket_launch'
  },
  { 
    id: 't2', 
    title: 'BREAK: Rest and recharge', 
    time: '2:28 AM → 2:43 AM', 
    category: 'BREAK', 
    top: 180, 
    height: 60, 
    color: '#656464',
    icon: 'coffee'
  },
  { 
    id: 't3', 
    title: 'INNOVATEX 4.0 GRAB-O-TRON', 
    time: '9:00 AM → 10:00 AM', 
    category: 'HACKATHON', 
    top: 300, 
    height: 100, 
    color: '#ad170c',
    icon: 'rocket_launch'
  }
];

const HOURS = ['12 AM', '1 AM', '2 AM', '3 AM', '4 AM', '5 AM', '6 AM', '7 AM', '8 AM', '9 AM', '10 AM'];

export default function UpcomingView() {
  return (
    <div className="ml-64 pt-24 px-12 min-h-screen bg-surface-dim/20 pb-20 overflow-y-auto">
      
      {/* 1. Priority Hub Header Stats */}
      <div className="grid grid-cols-4 gap-6 mb-12 animate-in fade-in slide-in-from-top-4 duration-500">
        {STATS.map((stat) => (
          <div 
            key={stat.label}
            className="paper-slip p-6 flex flex-col items-center gap-2 border-b-2 border-primary/10 shadow-sm"
          >
            <span className="material-symbols-outlined text-primary/40 text-3xl opacity-80">{stat.icon}</span>
            <div className="text-4xl font-black" style={{ fontFamily: 'Space Grotesk, sans-serif' }}>
              {stat.value}
            </div>
            <div className="text-[10px] font-bold tracking-[0.2em] opacity-40 uppercase">
              {stat.label}
            </div>
          </div>
        ))}
      </div>

      {/* 2. Rescheduling Alert Card */}
      <div 
        className="ruled-paper p-6 mb-12 border-l-4 border-primary shadow-lg animate-in fade-in duration-700 delay-200"
        style={{ backgroundColor: '#fff' }}
      >
        <div className="flex items-center gap-4">
          <span className="material-symbols-outlined text-primary">sync</span>
          <div>
            <div className="font-bold text-sm tracking-widest uppercase" style={{ color: '#ad170c' }}>Rescheduling Opportunity</div>
            <div className="text-xs opacity-60 mt-1 italic" style={{ fontFamily: 'Newsreader, serif' }}>
              0 lower-priority tasks could be rescheduled before "INNOVATEX 4.0 GRAB-O-TRON"
            </div>
          </div>
        </div>
      </div>

      {/* 3. Planner Grid (Day View Style) */}
      <div className="animate-in fade-in zoom-in-95 duration-1000 delay-300">
        <h3 
          className="text-2xl mb-8 uppercase tracking-widest"
          style={{ fontFamily: 'Space Grotesk, sans-serif' }}
        >
          Priority <span className="text-primary italic">Hub</span>
        </h3>

        {/* The Grid */}
        <div className="relative flex">
          
          {/* Times column */}
          <div className="w-20 pr-4 text-right flex flex-col gap-[60px] pt-2">
            {HOURS.map(hour => (
              <div 
                key={hour} 
                className="text-[10px] font-bold opacity-30 uppercase tracking-tighter"
              >
                {hour}
              </div>
            ))}
          </div>

          {/* Grid Content Column */}
          <div className="flex-1 relative border-l border-black/5 min-h-[800px]">
            {/* Horizontal Grid Lines */}
            <div className="absolute inset-0 ruled-paper opacity-30 pointer-events-none" style={{ backgroundSize: '100% 70px' }} />

            {/* Current Time Indicator (Visual Polish) */}
            <div className="absolute w-full h-[2px] bg-primary/20 z-10 top-[240px]">
              <div className="absolute left-0 -top-1 w-2 h-2 rounded-full bg-primary" />
            </div>

            {/* Task Blocks (Sticky Notes) */}
            {TIMELINE_BLOCKS.map(block => (
              <div 
                key={block.id}
                className="absolute left-4 right-4 paper-slip shadow-md p-4 transition-transform hover:scale-[1.01] hover:shadow-xl cursor-default border-l-4 overflow-hidden"
                style={{
                  top: block.top,
                  height: block.height,
                  borderLeftColor: block.color,
                  backgroundColor: '#fff'
                }}
              >
                {/* Check/Action Icons from Stat Screen */}
                <div className="absolute top-2 right-2 flex gap-1 opacity-20 hover:opacity-100 transition-opacity">
                   <button className="material-symbols-outlined text-[16px] p-1 rounded-sm hover:bg-green-100 text-green-600">check_circle</button>
                   <button className="material-symbols-outlined text-[16px] p-1 rounded-sm hover:bg-gray-100">sync</button>
                   <button className="material-symbols-outlined text-[16px] p-1 rounded-sm hover:bg-red-100 text-red-600">close</button>
                </div>

                <div className="flex gap-4 items-start">
                  <div 
                    className="w-10 h-10 rounded-full bg-primary/5 flex items-center justify-center text-primary"
                    style={{ backgroundColor: 'rgba(173,23,12,0.05)' }}
                  >
                    <span className="material-symbols-outlined">{block.icon}</span>
                  </div>
                  <div>
                    <div className="text-[10px] font-bold uppercase tracking-[0.2em] opacity-40 mb-1">
                      {block.time}
                    </div>
                    <div 
                      className="font-bold text-sm leading-tight uppercase"
                      style={{ fontFamily: 'Work Sans, sans-serif' }}
                    >
                      {block.title}
                    </div>
                    <div className="flex gap-2 mt-2">
                       <span className="text-[9px] font-bold px-2 py-1 bg-black/5 rounded-sm opacity-60 uppercase">{block.category}</span>
                       {block.height > 80 && <span className="text-[9px] font-bold px-2 py-1 bg-primary/5 text-primary rounded-sm uppercase tracking-widest italic">Personal-Peak</span>}
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>

        </div>
      </div>

    </div>
  );
}
