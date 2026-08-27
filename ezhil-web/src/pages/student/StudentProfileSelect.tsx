import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { TopAppBar } from '../../components/layout/TopAppBar';
import { db, LocalStudent } from '../../db/db';
import { useLiveQuery } from 'dexie-react-hooks';
import { SyncManager } from '../../services/syncManager';

const AVATAR_COLORS = ['#EF4444', '#F59E0B', '#62F9EE', '#A78BFA', '#FB923C', '#34D399'];

export const StudentProfileSelect: React.FC = () => {
  const navigate = useNavigate();
  const { session, loginStudent } = useAuth();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  
  // Modal states for adding student
  const [showAddModal, setShowAddModal] = useState(false);
  const [newName, setNewName] = useState('');
  const [newDob, setNewDob] = useState('');
  const [addError, setAddError] = useState('');
  const [syncing, setSyncing] = useState(false);

  // Sync roster on mount
  useEffect(() => {
    const triggerSync = async () => {
      setSyncing(true);
      try {
        await SyncManager.sync();
      } catch (err) {
        console.error('Initial sync failed:', err);
      } finally {
        setSyncing(false);
      }
    };
    triggerSync();
  }, []);

  // Live query students belonging to this teacher
  const students = useLiveQuery(async () => {
    const teacherId = session?.userId;
    if (!teacherId) return [];
    return await db.students.where('teacherId').equals(teacherId).toArray();
  }, [session?.userId]) || [];

  const handleSelectStudent = (id: string) => {
    setSelectedId(id);
  };

  const handleAddStudent = async (e: React.FormEvent) => {
    e.preventDefault();
    setAddError('');

    if (!newName.trim()) {
      setAddError('பெயர் தேவை / Name is required');
      return;
    }

    // Tamil Unicode character validation (U+0B80 - U+0BFF) plus optional whitespace
    const tamilRegex = /^[\u0B80-\u0BFF\s]+$/;
    if (!tamilRegex.test(newName.trim())) {
      setAddError('தமிழ் எழுத்துக்கள் மட்டுமே அனுமதிக்கப்படும் / Tamil characters only');
      return;
    }

    const teacherId = session?.userId;
    if (!teacherId) {
      setAddError('ஆசிரியர் அமர்வு இல்லை / No teacher session');
      return;
    }

    // Duplicate check
    const count = await db.students
      .where('name')
      .equals(newName.trim())
      .filter(s => s.teacherId === teacherId)
      .count();

    if (count > 0) {
      setAddError('பெயர் ஏற்கனவே உள்ளது / Name already exists');
      return;
    }

    try {
      const studentId = `student-${crypto.randomUUID()}`;
      const newStudent: LocalStudent = {
        id: studentId,
        teacherId: teacherId,
        name: newName.trim(),
        dob: newDob || undefined,
        riskLevel: 'unscreened',
        streakDays: 0,
        syncStatus: 'pending',
        createdAt: new Date().toISOString()
      };
      
      await db.students.put(newStudent);
      
      // Reset & close
      setNewName('');
      setNewDob('');
      setShowAddModal(false);
      
      // Auto-trigger sync to push new student
      SyncManager.sync().catch(err => console.error('Sync failed after student add:', err));
    } catch (err) {
      setAddError('மாணவரைச் சேமிப்பதில் பிழை / Error saving student');
    }
  };

  const handleContinue = async () => {
    if (!selectedId) return;

    const student = await db.students.get(selectedId);
    if (!student) return;

    // Update streak logic
    const today = new Date().toISOString().split('T')[0]; // YYYY-MM-DD
    let currentStreak = student.streakDays;
    
    if (student.lastActive !== today) {
      currentStreak += 1;
      await db.students.update(selectedId, {
        streakDays: currentStreak,
        lastActive: today,
        syncStatus: 'pending' // Flag for sync update
      });
    }

    // Set active student context
    loginStudent(
      session?.accessToken || '',
      student.name,
      session?.userId || '',
      student.id,
      session?.schoolCode || '',
      session?.schoolName || '',
      session?.teacherId || '',
      session?.teacherName || ''
    );
    
    // Attempt sync in background
    SyncManager.sync().catch(err => console.error('Background sync failed:', err));
    
    navigate('/student/home');
  };

  const getInitial = (name: string) => {
    return name.trim().charAt(0) || '🎓';
  };

  const getColorForIndex = (index: number) => {
    return AVATAR_COLORS[index % AVATAR_COLORS.length];
  };

  return (
    <div className="bg-bg-deep text-on-surface font-body-tamil min-h-dvh flex flex-col">
      <TopAppBar showBack title="எழில் | Ezhil" />

      {/* Header */}
      <div className="px-5 pt-6 pb-4 animate-fade-in">
        <h1 className="font-display-tamil text-3xl font-bold heading-display-accent leading-tight">
          உங்கள் பெயரை தொடுக
        </h1>
        <p className="font-bilingual-sub text-text-muted text-sm mt-1">
          Tap your name to continue · {session?.schoolCode || 'Classroom'}
        </p>
      </div>

      {/* Student list — scrolls independently */}
      <div className="flex-1 overflow-y-auto px-5 pb-40 space-y-3 stagger-children">
        {students.map((s, idx) => {
          const color = getColorForIndex(idx);
          const isSelected = selectedId === s.id;
          return (
            <button key={s.id} onClick={() => handleSelectStudent(s.id)} style={{ width: '100%' }}
              className={`flex items-center gap-4 h-[72px] r-chip px-4 transition-all active:scale-95 text-left ${
                isSelected
                  ? 'border-2 border-primary-fixed shadow-[0_0_16px_rgba(98,249,238,0.15)] bg-primary-fixed/5'
                  : 'border border-white/10 bg-bg-surface hover:border-accent-teal/40'
              }`}>
              {/* Avatar */}
              <div className="w-12 h-12 rounded-full flex items-center justify-center text-white font-bold text-xl flex-shrink-0"
                style={{ backgroundColor: color + '33', border: `2px solid ${color}66`, color: color }}>
                {getInitial(s.name)}
              </div>
              {/* Name */}
              <div className="flex-1 min-w-0">
                <p className="font-dashboard-title text-lg text-text-primary truncate">{s.name}</p>
                <p className="text-text-muted text-xs truncate capitalize">{s.riskLevel} Risk</p>
              </div>
              {/* Streak */}
              <div className="flex items-center gap-1 flex-shrink-0">
                <span className={`material-symbols-outlined text-lg ${s.streakDays > 0 ? 'text-amber-400' : 'text-text-muted'}`}
                  style={{ fontVariationSettings: "'FILL' 1" }}>local_fire_department</span>
                <span className={`text-sm font-bold ${s.streakDays > 0 ? 'text-amber-400' : 'text-text-muted'}`}>
                  {s.streakDays}
                </span>
              </div>
              {/* Selection indicator */}
              {isSelected && (
                <span className="material-symbols-outlined text-primary-fixed flex-shrink-0 text-xl"
                  style={{ fontVariationSettings: "'FILL' 1" }}>check_circle</span>
              )}
            </button>
          );
        })}

        {/* Add new student button */}
        <button onClick={() => setShowAddModal(true)} style={{ width: '100%' }}
          className="flex items-center justify-center gap-3 h-[72px] r-chip border-2 border-dashed border-white/20 hover:bg-white/5 transition-all text-left">
          <span className="material-symbols-outlined text-primary-fixed">add_circle</span>
          <span className="font-dashboard-title text-primary-fixed text-sm">புதிய மாணவர் / Add New Student</span>
        </button>
      </div>

      {/* Fixed bottom bar */}
      <div className="fixed bottom-0 left-0 right-0 z-40">
        <div className="h-9 bg-bg-surface flex items-center justify-center gap-2 border-t border-accent-teal/20">
          <span className={`material-symbols-outlined text-base ${syncing ? 'text-primary-fixed animate-spin' : 'text-success'}`}>
            {syncing ? 'sync' : 'cloud_done'}
          </span>
          <span className={`font-mono-metadata text-xs ${syncing ? 'text-primary-fixed' : 'text-success'}`}>
            {syncing ? 'Syncing roster...' : 'Cloud Synced · ' + (session?.schoolCode || 'PRIMARY SCHOOL')}
          </span>
        </div>
        <div className="bg-bg-deep px-5 pt-3 pb-8 shadow-[0_-8px_24px_rgba(0,0,0,0.6)]">
          <button onClick={handleContinue} disabled={!selectedId}
            className="w-full h-14 bg-primary-fixed text-bg-deep r-chip font-dashboard-title text-lg flex items-center justify-center gap-2 active:scale-95 transition-all disabled:opacity-50">
            <span>தொடர்க</span>
            <span className="material-symbols-outlined">arrow_forward</span>
            <span className="font-bilingual-sub font-normal text-sm ml-1">Continue</span>
          </button>
        </div>
      </div>

      {/* Add Student Modal */}
      {showAddModal && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="glass-panel-heavy r-card w-full max-w-sm border border-white/10 p-6 space-y-4">
            <div className="flex justify-between items-center pb-2 border-b border-white/10">
              <h3 className="text-white font-bold text-lg font-display-tamil">புதிய மாணவர் சேர்க்கை</h3>
              <button onClick={() => { setShowAddModal(false); setAddError(''); }} className="text-text-muted hover:text-white">
                <span className="material-symbols-outlined">close</span>
              </button>
            </div>
            
            {addError && (
              <div className="p-3 bg-error/10 border border-error/30 r-chip text-error text-xs text-center">
                {addError}
              </div>
            )}
            
            <form onSubmit={handleAddStudent} className="space-y-4">
              <div className="space-y-1">
                <label className="text-text-muted text-xs uppercase">Tamil Name / தமிழ் பெயர்</label>
                <input required type="text" value={newName} onChange={e => setNewName(e.target.value)}
                  className="w-full h-11 bg-black/40 border border-white/10 r-chip px-4 text-white focus:border-primary-fixed outline-none text-sm placeholder:text-outline/40"
                  placeholder="எ.கா. கவின்" />
              </div>

              <div className="space-y-1">
                <label className="text-text-muted text-xs uppercase">Date of Birth / பிறந்த தேதி (Optional)</label>
                <input type="date" value={newDob} onChange={e => setNewDob(e.target.value)}
                  className="w-full h-11 bg-black/40 border border-white/10 r-chip px-4 text-white focus:border-primary-fixed outline-none text-sm" />
              </div>

              <button type="submit"
                className="w-full h-12 bg-primary-fixed text-bg-deep font-bold r-chip shadow-lg hover:shadow-xl active:scale-[0.98] transition-all flex items-center justify-center gap-2">
                <span className="material-symbols-outlined text-sm">save</span>
                <span>சேமி / Save</span>
              </button>
            </form>
          </div>
        </div>
      )}

      {/* Background glows */}
      <div className="fixed inset-0 pointer-events-none -z-10 overflow-hidden">
        <div className="absolute -top-1/4 -right-1/4 w-[600px] h-[600px] bg-accent-teal/5 rounded-full blur-[120px]" />
        <div className="absolute -bottom-1/4 -left-1/4 w-[500px] h-[500px] bg-studio-purple/5 rounded-full blur-[100px]" />
      </div>
    </div>
  );
};
