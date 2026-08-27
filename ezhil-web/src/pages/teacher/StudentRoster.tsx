import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { db } from '../../db/db';
import { useAuth } from '../../contexts/AuthContext';
import { useLiveQuery } from 'dexie-react-hooks';
import { IdDisplay } from '../../components/ui/IdDisplay';
import { EmptyState } from '../../components/ui/EmptyState';

interface StudentData {
  id: string;
  ta: string;
  en: string;
  risk: 'unscreened' | 'low' | 'medium' | 'high';
  last: string;
  dob?: string;
  lessonsDone: number;
  lessonsTotal: number;
  initials: string;
}

export const StudentRoster: React.FC = () => {
  const navigate = useNavigate();
  const { session } = useAuth();
  const teacherId = session?.userId || '';

  const [filter, setFilter] = useState<string>('all');
  const [search, setSearch] = useState('');

  // Live query student roster for this teacher
  const students = useLiveQuery(async () => {
    if (!teacherId) return [] as StudentData[];
    const list = await db.students.where('teacherId').equals(teacherId).toArray();
    
    const mapped = [];
    for (const s of list) {
      // Find latest assessment
      const assessments = await db.assessments
        .where('studentId')
        .equals(s.id)
        .sortBy('conductedAt');
      const lastAss = assessments[assessments.length - 1];
      
      let lastStr = '--';
      if (lastAss && lastAss.conductedAt) {
        const date = new Date(lastAss.conductedAt);
        lastStr = date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
      }

      // Query completed lessons count
      const doneCount = await db.lesson_progress
        .where('studentId')
        .equals(s.id)
        .filter(lp => !!lp.completedAt)
        .count();
      
      const totalLessonsCount = await db.lessons.count();
      const lessonsTotal = totalLessonsCount > 0 ? totalLessonsCount : 20;

      mapped.push({
        id: s.id,
        ta: s.name,
        en: s.name, // Display name
        risk: s.riskLevel,
        last: lastStr,
        dob: s.dob,
        lessonsDone: doneCount,
        lessonsTotal: lessonsTotal,
        initials: s.name.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase()
      });
    }
    return mapped;
  }, [teacherId]) || [];

  const visible = students.filter(s =>
    (filter === 'all' || s.risk === filter) &&
    (s.ta.includes(search) || s.en.toLowerCase().includes(search.toLowerCase()))
  );

  const openProfile = (s: StudentData) => {
    navigate('/teacher/student-profile', { state: { studentId: s.id } });
  };

  const handleDelete = async (e: React.MouseEvent, id: string, name: string) => {
    e.stopPropagation(); // Avoid navigating
    if (window.confirm(`Are you sure you want to delete ${name}? / ${name} மாணவரை நீக்க வேண்டுமா?`)) {
      try {
        await db.students.delete(id);
        // Clear related assessments & progress
        await db.assessments.where('studentId').equals(id).delete();
        await db.lesson_progress.where('studentId').equals(id).delete();
        await db.game_sessions.where('studentId').equals(id).delete();
      } catch (err) {
        console.error('Error deleting student:', err);
      }
    }
  };

  const highRiskCount = students.filter(s => s.risk === 'high').length;
  const mediumRiskCount = students.filter(s => s.risk === 'medium').length;
  const lowRiskCount = students.filter(s => s.risk === 'low').length;
  const unscreenedCount = students.filter(s => s.risk === 'unscreened').length;

  return (
    <div className="space-y-lg animate-fade-in font-body-tamil select-none">
      {/* Header title */}
      <section className="flex flex-col md:flex-row md:items-center justify-between gap-md pb-md border-b border-outline-variant/10">
        <div className="flex items-center gap-3">
          <span className="w-11 h-11 r-card bg-secondary/10 border border-secondary/25 flex items-center justify-center shadow-[0_0_14px_rgba(255,185,85,0.18)] flex-shrink-0">
            <span className="material-symbols-outlined text-secondary" style={{ fontVariationSettings: "'FILL' 1" }}>group</span>
          </span>
          <div>
            <h1 className="font-tamil-reader text-3xl font-bold heading-display">மாணவர் பட்டியல்</h1>
            <p className="text-text-muted text-sm mt-1">Student Roster · {students.length} students enrolled</p>
          </div>
        </div>
        <div className="flex items-center gap-sm">
          <button 
            onClick={() => navigate('/teacher/add-student')}
            className="flex items-center gap-sm bg-primary-fixed text-bg-deep px-lg py-sm r-chip font-bold text-xs uppercase shadow-[0_0_16px_rgba(98,249,238,0.25)] hover:shadow-[0_0_24px_rgba(98,249,238,0.4)] hover:-translate-y-0.5 transition-all duration-200 cursor-pointer"
          >
            <span className="material-symbols-outlined text-base">person_add</span>
            <span>+ Add Student</span>
          </button>
        </div>
      </section>

      {/* Roster Search Bar */}
      <section className="relative">
        <span className="material-symbols-outlined absolute left-4 top-1/2 -translate-y-1/2 text-on-surface-variant/40">search</span>
        <input 
          value={search} 
          onChange={e => setSearch(e.target.value)}
          className="w-full h-12 pl-12 pr-4 bg-surface-container-low border border-outline-variant/30 r-chip text-on-surface placeholder:text-on-surface-variant/40 focus:ring-2 focus:ring-primary-fixed outline-none transition-colors"
          placeholder="Search students... / மாணவர் தேடல்..." 
        />
      </section>

      {/* Filter Pills */}
      <section className="flex flex-wrap items-center gap-sm border-b border-outline-variant/10 pb-md">
        <span className="text-caption font-caption text-on-surface-variant font-bold uppercase tracking-wider mr-sm">Filter by Risk:</span>
        <button 
          onClick={() => setFilter('all')}
          className={`px-md py-1.5 rounded-full text-xs font-bold border transition-all cursor-pointer ${
            filter === 'all' ? 'bg-secondary border-secondary text-on-secondary shadow-md' : 'bg-surface-container-low border-outline-variant/30 text-on-surface-variant hover:border-secondary'
          }`}
        >
          All
        </button>
        <button 
          onClick={() => setFilter('high')}
          className={`px-md py-1.5 rounded-full text-xs font-bold border flex items-center gap-sm transition-all cursor-pointer ${
            filter === 'high' ? 'bg-risk-high border-risk-high text-white shadow-md' : 'bg-surface-container-low border-outline-variant/30 text-on-surface-variant hover:border-risk-high'
          }`}
        >
          <span className="w-1.5 h-1.5 rounded-full bg-risk-high"></span>
          High Risk ({highRiskCount})
        </button>
        <button 
          onClick={() => setFilter('medium')}
          className={`px-md py-1.5 rounded-full text-xs font-bold border flex items-center gap-sm transition-all cursor-pointer ${
            filter === 'medium' ? 'bg-risk-medium border-risk-medium text-white shadow-md' : 'bg-surface-container-low border-outline-variant/30 text-on-surface-variant hover:border-risk-medium'
          }`}
        >
          <span className="w-1.5 h-1.5 rounded-full bg-risk-medium"></span>
          Medium Risk ({mediumRiskCount})
        </button>
        <button 
          onClick={() => setFilter('low')}
          className={`px-md py-1.5 rounded-full text-xs font-bold border flex items-center gap-sm transition-all cursor-pointer ${
            filter === 'low' ? 'bg-risk-low border-risk-low text-white shadow-md' : 'bg-surface-container-low border-outline-variant/30 text-on-surface-variant hover:border-risk-low'
          }`}
        >
          <span className="w-1.5 h-1.5 rounded-full bg-risk-low"></span>
          Low Risk ({lowRiskCount})
        </button>
        <button 
          onClick={() => setFilter('unscreened')}
          className={`px-md py-1.5 rounded-full text-xs font-bold border flex items-center gap-sm transition-all cursor-pointer ${
            filter === 'unscreened' ? 'bg-risk-unscreened border-risk-unscreened text-bg-deep shadow-md' : 'bg-surface-container-low border-outline-variant/30 text-on-surface-variant hover:border-risk-unscreened'
          }`}
        >
          <span className="w-1.5 h-1.5 rounded-full bg-risk-unscreened"></span>
          Unscreened ({unscreenedCount})
        </button>
      </section>

      {/* Roster Grid Summary cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-md items-stretch stagger-children">
        {[
          { label: 'Total Enrolled', count: students.length,       color: 'text-white',       glow: 'bg-primary-fixed/8' },
          { label: 'High Risk / கவனம்',   count: highRiskCount,         color: 'text-risk-high',   glow: 'bg-risk-high/10' },
          { label: 'Medium Risk / பயிற்சி',  count: mediumRiskCount,       color: 'text-risk-medium', glow: 'bg-risk-medium/10' },
          { label: 'Unscreened / சோதனை',   count: unscreenedCount,       color: 'text-text-muted',  glow: 'bg-white/5' },
        ].map(stat => (
          <div key={stat.label} className="glass-card card-lift r-card p-md text-center border border-outline-variant/15 flex flex-col justify-between h-full relative overflow-hidden">
            <div className={`absolute -top-8 -right-8 w-20 h-20 ${stat.glow} rounded-full blur-2xl`} />
            <p className={`text-3xl font-bold leading-tight relative ${stat.color}`}>{stat.count}</p>
            <p className="text-text-muted text-xs uppercase font-bold mt-2 mt-auto relative">{stat.label}</p>
          </div>
        ))}
      </div>

      {/* Roster Data Table Container */}
      <div className="bg-surface-container-low r-chip border border-outline-variant/20 overflow-hidden shadow-lg overflow-x-auto">
        <table className="w-full text-left border-collapse min-w-[700px]">
          <thead className="bg-surface-container-high border-b border-outline-variant/30">
            <tr>
              <th className="px-lg py-md text-xs font-bold text-on-surface-variant uppercase">Avatar & Name / பெயர்</th>
              <th className="px-lg py-md text-xs font-bold text-on-surface-variant uppercase tracking-wider">Risk Level</th>
              <th className="px-lg py-md text-xs font-bold text-on-surface-variant uppercase tracking-wider text-center">Last Assessment</th>
              <th className="px-lg py-md text-xs font-bold text-on-surface-variant uppercase tracking-wider text-center">Lesson Progress</th>
              <th className="px-lg py-md text-xs font-bold text-on-surface-variant uppercase tracking-wider text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-outline-variant/10">
            {visible.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-lg py-xl text-center text-text-muted">
                  <EmptyState art="students" title="மாணவர்கள் இல்லை" subtitle="No students match your search filter." />
                </td>
              </tr>
            ) : (
              visible.map(s => {
                const isHigh = s.risk === 'high';
                const isMed = s.risk === 'medium';
                const isLow = s.risk === 'low';
                
                let badgeClass = 'bg-risk-unscreened/10 text-risk-unscreened border-risk-unscreened/20';
                let badgeIcon = 'help_outline';
                let badgeLabel = 'Unscreened';
                if (isHigh) {
                  badgeClass = 'bg-risk-high/10 text-risk-high border-risk-high/20';
                  badgeIcon = 'error';
                  badgeLabel = 'High Risk';
                } else if (isMed) {
                  badgeClass = 'bg-risk-medium/10 text-risk-medium border-risk-medium/20';
                  badgeIcon = 'warning';
                  badgeLabel = 'Medium Risk';
                } else if (isLow) {
                  badgeClass = 'bg-risk-low/10 text-risk-low border-risk-low/20';
                  badgeIcon = 'check_circle';
                  badgeLabel = 'Low Risk';
                }

                const progressPercent = s.lessonsTotal > 0 ? Math.round((s.lessonsDone / s.lessonsTotal) * 100) : 0;

                return (
                  <tr 
                    key={s.id} 
                    onClick={() => openProfile(s)}
                    className="hover:bg-surface-container transition-colors group cursor-pointer"
                  >
                    {/* Name column */}
                    <td className="px-lg py-md">
                      <div className="flex items-center gap-md">
                        <div className={`w-10 h-10 rounded-full border bg-bg-deep flex items-center justify-center font-bold text-sm ${
                          isHigh ? 'border-risk-high text-risk-high' : isMed ? 'border-risk-medium text-risk-medium' : isLow ? 'border-risk-low text-risk-low' : 'border-outline-variant text-text-muted'
                        } group-hover:scale-105 transition-transform`}>
                          {s.initials}
                        </div>
                        <div>
                          <p className="font-body-sm font-bold text-white group-hover:text-primary-fixed transition-colors">{s.en}</p>
                          <div className="flex items-center gap-1.5 font-tamil-body text-xs text-text-muted mt-0.5" onClick={e => e.stopPropagation()}>
                            <span>மாணவர் ஐடி:</span>
                            <IdDisplay id={s.id} maxLength={6} />
                          </div>
                        </div>
                      </div>
                    </td>
                    
                    {/* Risk Badge column */}
                    <td className="px-lg py-md">
                      <span className={`inline-flex items-center gap-xs px-md py-1 rounded-full text-xs font-bold border ${badgeClass}`}>
                        <span className="material-symbols-outlined text-sm" style={{ fontVariationSettings: "'FILL' 1" }}>
                          {badgeIcon}
                        </span>
                        {badgeLabel}
                      </span>
                    </td>

                    {/* Last assessment date column */}
                    <td className="px-lg py-md text-center text-on-surface-variant font-mono text-xs">
                      {s.last}
                    </td>

                    {/* Lesson progress bar column */}
                    <td className="px-lg py-md text-center">
                      <div className="flex flex-col items-center gap-xs">
                        <span className="font-mono text-xs font-bold text-white">{s.lessonsDone}/{s.lessonsTotal}</span>
                        <div className="w-24 h-1.5 bg-surface-container-highest rounded-full overflow-hidden">
                          <div className="h-full bg-accent-cyan transition-all duration-500" style={{ width: `${progressPercent}%` }}></div>
                        </div>
                      </div>
                    </td>

                    {/* Action buttons column */}
                    <td className="px-lg py-md text-right">
                      <div className="flex items-center justify-end gap-xs md:opacity-0 group-hover:opacity-100 transition-opacity">
                        <button 
                          onClick={(e) => { e.stopPropagation(); openProfile(s); }}
                          className="p-sm text-on-surface-variant hover:text-accent-cyan rounded-full hover:bg-white/5 transition-all" 
                          title="View Profile"
                        >
                          <span className="material-symbols-outlined text-lg">visibility</span>
                        </button>
                        <button 
                          onClick={(e) => handleDelete(e, s.id, s.en)}
                          className="p-sm text-on-surface-variant hover:text-error-text rounded-full hover:bg-white/5 transition-all" 
                          title="Delete"
                        >
                          <span className="material-symbols-outlined text-lg">delete</span>
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
