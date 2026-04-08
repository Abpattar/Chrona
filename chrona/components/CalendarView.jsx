// ============================================================
// CalendarView.jsx — Simple monthly calendar view
// ============================================================

'use client';

import { useState, useMemo } from 'react';

const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTHS = ['January','February','March','April','May','June','July','August','September','October','November','December'];

export default function CalendarView({ tasks, categories }) {
  const today = new Date();
  const [year, setYear] = useState(today.getFullYear());
  const [month, setMonth] = useState(today.getMonth());

  const firstDay = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();

  // Parse task dates into day numbers for this month
  const taskDays = useMemo(() => {
    const daySet = new Set();
    tasks.forEach((t) => {
      if (t.date === 'TBD') return;
      const d = new Date(t.date);
      if (!isNaN(d) && d.getMonth() === month && d.getFullYear() === year) {
        daySet.add(d.getDate());
      }
    });
    return daySet;
  }, [tasks, month, year]);

  const prevMonth = () => {
    if (month === 0) { setMonth(11); setYear(year - 1); }
    else setMonth(month - 1);
  };
  const nextMonth = () => {
    if (month === 11) { setMonth(0); setYear(year + 1); }
    else setMonth(month + 1);
  };

  // Build calendar cells
  const cells = [];
  for (let i = 0; i < firstDay; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(d);

  const isToday = (d) =>
    d === today.getDate() && month === today.getMonth() && year === today.getFullYear();

  return (
    <div className="ml-64 pt-16 min-h-screen flex items-start justify-center p-12">
      <div
        className="w-full max-w-2xl p-8"
        style={{
          backgroundColor: '#ffffff',
          boxShadow: '5px 8px 30px rgba(28,28,24,0.08)',
        }}
      >
        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <button
            onClick={prevMonth}
            className="material-symbols-outlined"
            style={{ border: 'none', background: 'none', cursor: 'pointer', color: '#ad170c' }}
          >
            chevron_left
          </button>
          <h2
            className="text-xl font-bold uppercase tracking-widest"
            style={{ fontFamily: 'Space Grotesk, sans-serif', color: '#1c1c18' }}
          >
            {MONTHS[month]} {year}
          </h2>
          <button
            onClick={nextMonth}
            className="material-symbols-outlined"
            style={{ border: 'none', background: 'none', cursor: 'pointer', color: '#ad170c' }}
          >
            chevron_right
          </button>
        </div>

        {/* Day headers */}
        <div className="calendar-grid mb-2">
          {DAYS.map((d) => (
            <div
              key={d}
              className="text-center text-xs uppercase font-bold py-2"
              style={{ fontFamily: 'Work Sans, sans-serif', color: '#916f6e' }}
            >
              {d}
            </div>
          ))}
        </div>

        {/* Day cells */}
        <div className="calendar-grid">
          {cells.map((day, i) => (
            <div
              key={i}
              className={`calendar-cell ${day && isToday(day) ? 'calendar-cell-today' : ''}`}
              style={{ minHeight: '48px' }}
            >
              {day && (
                <>
                  <span style={{ color: isToday(day) ? '#ad170c' : '#1c1c18' }}>{day}</span>
                  {taskDays.has(day) && <div className="calendar-dot" />}
                </>
              )}
            </div>
          ))}
        </div>

        {/* Task list for the month */}
        <div className="mt-8">
          <h3
            className="text-sm uppercase tracking-widest mb-3 font-bold"
            style={{ fontFamily: 'Space Grotesk, sans-serif', color: '#916f6e' }}
          >
            Tasks This Month
          </h3>
          {tasks.filter((t) => {
            if (t.date === 'TBD') return true;
            const d = new Date(t.date);
            return !isNaN(d) && d.getMonth() === month && d.getFullYear() === year;
          }).length === 0 && (
            <p className="italic text-sm" style={{ fontFamily: 'Newsreader, serif', color: '#916f6e' }}>
              No tasks this month.
            </p>
          )}
          <div className="flex flex-col gap-2">
            {tasks
              .filter((t) => {
                if (t.date === 'TBD') return false;
                const d = new Date(t.date);
                return !isNaN(d) && d.getMonth() === month && d.getFullYear() === year;
              })
              .map((task) => {
                const cat = categories.find((c) => c.id === task.categoryId);
                return (
                  <div
                    key={task.id}
                    className="flex items-center justify-between py-2"
                    style={{ borderBottom: '1px solid rgba(28,28,24,0.06)' }}
                  >
                    <div>
                      <span
                        className="text-sm font-bold uppercase"
                        style={{ fontFamily: 'Space Grotesk, sans-serif' }}
                      >
                        {task.title}
                      </span>
                      <span
                        className="ml-2 text-xs italic"
                        style={{ fontFamily: 'Newsreader, serif', color: 'rgba(28,28,24,0.6)' }}
                      >
                        {task.date}
                      </span>
                    </div>
                    {cat && (
                      <span
                        className="text-xs uppercase font-bold px-2 py-0.5"
                        style={{
                          fontFamily: 'Work Sans, sans-serif',
                          fontSize: '9px',
                          backgroundColor: 'rgba(173,23,12,0.1)',
                          color: '#ad170c',
                        }}
                      >
                        {cat.name}
                      </span>
                    )}
                  </div>
                );
              })}
          </div>
        </div>
      </div>
    </div>
  );
}
