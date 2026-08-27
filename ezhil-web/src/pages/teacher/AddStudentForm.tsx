import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { db, LocalStudent } from '../../db/db';
import { useAuth } from '../../contexts/AuthContext';
import { SyncManager } from '../../services/syncManager';

export const AddStudentForm: React.FC = () => {
  const navigate = useNavigate();
  const { session } = useAuth();
  const [gender, setGender] = useState<'boy' | 'girl' | null>('girl');
  const [name, setName] = useState('');
  const [dob, setDob] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const INPUT = 'w-full h-12 pl-12 pr-4 bg-surface-container-low border border-outline-variant/30 r-chip text-on-surface focus:ring-2 focus:ring-primary-fixed outline-none transition-all placeholder:text-on-surface-variant/40 font-body-tamil';

  const handleSave = async () => {
    setError('');
    const teacherId = session?.userId;
    
    if (!name.trim()) {
      setError('பெயர் தேவை / Name is required');
      return;
    }

    // Tamil character validation (allow only Tamil letters and whitespace)
    const tamilRegex = /^[\u0B80-\u0BFF\s]+$/;
    if (!tamilRegex.test(name.trim())) {
      setError('தமிழ் எழுத்துக்கள் மட்டுமே அனுமதிக்கப்படும் / Tamil characters only');
      return;
    }

    if (!teacherId) {
      setError('ஆசிரியர் அமர்வு இல்லை / No teacher session found');
      return;
    }

    setSaving(true);

    try {
      // Check for duplicate name for this teacher
      const duplicateCount = await db.students
        .where('name')
        .equals(name.trim())
        .filter(s => s.teacherId === teacherId)
        .count();

      if (duplicateCount > 0) {
        setError('பெயர் ஏற்கனவே உள்ளது / Name already exists');
        setSaving(false);
        return;
      }

      const studentId = `student-${crypto.randomUUID()}`;
      const newStudent: LocalStudent = {
        id: studentId,
        teacherId: teacherId,
        name: name.trim(),
        dob: dob || undefined,
        riskLevel: 'unscreened',
        streakDays: 0,
        syncStatus: 'pending',
        createdAt: new Date().toISOString()
      };

      await db.students.put(newStudent);
      
      // Auto-trigger sync in background
      SyncManager.sync().catch(err => console.error('Sync failed after roster add:', err));
      
      navigate('/teacher/roster');
    } catch (err) {
      setError('மாணவரைச் சேமிப்பதில் பிழை / Error saving student');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-lg max-w-lg mx-auto animate-fade-in font-body-tamil select-none">
      {/* Page header */}
      <div className="pb-sm border-b border-outline-variant/10 flex items-center gap-3">
        <span className="w-11 h-11 r-card bg-primary-fixed/12 border border-primary-fixed/25 flex items-center justify-center shadow-[0_0_14px_rgba(98,249,238,0.18)] flex-shrink-0">
          <span className="material-symbols-outlined text-primary-fixed" style={{ fontVariationSettings: "'FILL' 1" }}>person_add</span>
        </span>
        <div>
          <h1 className="font-tamil-reader text-3xl font-bold heading-display">மாணவரை சேர்</h1>
          <p className="text-text-muted text-sm mt-1">Add New Student to classroom roster</p>
        </div>
      </div>

      {error && (
        <div className="p-4 bg-risk-high/10 border border-risk-high/30 r-chip text-error-text text-sm text-center flex items-center justify-center gap-sm animate-shake">
          <span className="material-symbols-outlined text-lg">error</span>
          <span>{error}</span>
        </div>
      )}

      {/* Avatar placeholder with dotted accent */}
      <div className="flex flex-col items-center py-md bg-white/5 r-card border border-outline-variant/20 shadow-sm relative overflow-hidden">
        <div className="absolute top-0 left-0 w-24 h-24 bg-primary-fixed/5 rounded-full blur-xl pointer-events-none" />
        <div className="relative">
          <div className="w-20 h-20 rounded-full bg-surface-container-low border-2 border-dashed border-primary-fixed flex items-center justify-center">
            <span className="material-symbols-outlined text-3xl text-primary-fixed">person</span>
          </div>
          <button aria-label="Add a photo" className="absolute bottom-0 right-0 bg-primary-fixed text-bg-deep w-6 h-6 rounded-full flex items-center justify-center shadow-lg hover:scale-105 active:scale-90 transition-transform">
            <span className="material-symbols-outlined text-xs font-bold">add_a_photo</span>
          </button>
        </div>
        <p className="mt-3 text-text-muted text-xs uppercase font-bold font-mono-metadata">Student Profile Photo</p>
      </div>

      {/* Glass card panel for fields */}
      <div className="glass-card r-card p-6 space-y-5 border border-outline-variant/20 shadow-lg">
        {/* Name input */}
        <div className="space-y-1">
          <label className="block text-xs font-bold uppercase text-primary-fixed pl-1">
            மாணவர் பெயர் / Student Name <span className="text-risk-high">*</span>
          </label>
          <div className="relative">
            <span className="material-symbols-outlined absolute left-4 top-1/2 -translate-y-1/2 text-on-surface-variant/40">person</span>
            <input 
              value={name} 
              onChange={e => setName(e.target.value)} 
              className={INPUT} 
              placeholder="Enter full name (Tamil Unicode only)" 
              required
            />
          </div>
        </div>

        {/* DOB input */}
        <div className="space-y-1">
          <label className="block text-xs font-bold uppercase text-primary-fixed pl-1">
            பிறந்த தேதி / Date of Birth <span className="text-text-muted font-normal lowercase">(Optional)</span>
          </label>
          <div className="relative">
            <span className="material-symbols-outlined absolute left-4 top-1/2 -translate-y-1/2 text-on-surface-variant/40">calendar_today</span>
            <input 
              value={dob} 
              onChange={e => setDob(e.target.value)} 
              type="date" 
              className={`${INPUT} appearance-none pr-10`} 
            />
          </div>
        </div>

        {/* Gender radio buttons */}
        <div className="space-y-2">
          <label className="block text-xs font-bold uppercase text-primary-fixed pl-1">Gender / பாலினம்</label>
          <div className="grid grid-cols-2 gap-4">
            {(['boy', 'girl'] as const).map(g => (
              <div 
                key={g} 
                onClick={() => setGender(g)}
                className={`p-4 r-chip bg-surface-container-low flex flex-col items-center gap-2 cursor-pointer transition-all border ${
                  gender === g 
                    ? 'border-primary-fixed bg-primary-fixed/5 shadow-[0_0_12px_rgba(98,249,238,0.1)]' 
                    : 'border-outline-variant/30 hover:border-primary-fixed/40'
                }`}
              >
                <span className={`material-symbols-outlined text-2xl ${gender === g ? 'text-primary-fixed' : 'text-text-muted'}`}>
                  {g === 'boy' ? 'face' : 'face_3'}
                </span>
                <span className={`text-xs font-bold uppercase ${gender === g ? 'text-primary-fixed' : 'text-text-muted'}`}>
                  {g === 'boy' ? 'மாணவன் / Boy' : 'மாணவி / Girl'}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Save Action */}
      <div className="space-y-3 pt-sm">
        <button 
          onClick={handleSave} 
          disabled={!name.trim() || saving}
          className="w-full h-14 bg-primary-fixed text-bg-deep font-bold text-lg r-chip shadow-[0_4px_16px_rgba(98,249,238,0.2)] hover:shadow-[0_4px_24px_rgba(98,249,238,0.35)] active:scale-95 transition-all disabled:opacity-50 cursor-pointer"
        >
          {saving ? 'சேமிக்கிறது...' : 'வகுப்பில் சேர் / Add to Class'}
        </button>
        <button 
          onClick={() => navigate(-1)} 
          className="w-full py-3 text-text-muted font-bold text-sm text-center hover:text-white transition-colors cursor-pointer"
        >
          ரத்து / Cancel
        </button>
      </div>
    </div>
  );
};
