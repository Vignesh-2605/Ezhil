import React from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { TeacherBottomNavBar } from './TeacherBottomNavBar';
import { useAuth } from '../../contexts/AuthContext';
import { useLanguage } from '../../contexts/LanguageContext';
import { db } from '../../db/db';
import { useLiveQuery } from 'dexie-react-hooks';
import { RouteTransition } from '../motion/RouteTransition';

const getTitle = (path: string) => {
  if (path.includes('dashboard')) return 'Dashboard / கட்டுப்பாட்டகம்';
  if (path.includes('roster')) return 'Roster / மாணவர் பட்டியல்';
  if (path.includes('student-profile')) return 'Student Profile / மாணவர் விவரம்';
  if (path.includes('add-student')) return 'Add Student / மாணவரைச் சேர்';
  if (path.includes('lesson-studio')) return 'Lesson Studio / பாட Studio';
  if (path.includes('lessons')) return 'Lessons / பாடங்கள்';
  if (path.includes('reports')) return 'Reports / அறிக்கைகள்';
  if (path.includes('profile')) return 'Settings & Profile / அமைப்புகள்';
  return 'Teacher Portal / ஆசிரியர் தளம்';
};

export const TeacherLayout: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { session } = useAuth();
  const { lang, toggle } = useLanguage();

  const title = getTitle(location.pathname);
  const teacherInitials = session?.name ? session.name.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase() : '🧑‍🏫';

  // Monitor pending sync records for top-bar status
  const pendingSyncCount = useLiveQuery(async () => {
    const st = await db.students.where('syncStatus').equals('pending').count();
    const as = await db.assessments.where('syncStatus').equals('pending').count();
    const gs = await db.game_sessions.where('syncStatus').equals('pending').count();
    const lp = await db.lesson_progress.where('syncStatus').equals('pending').count();
    return st + as + gs + lp;
  }, []) || 0;

  return (
    <div className="flex min-h-dvh bg-surface-container-lowest text-on-surface select-none">
      {/* Sidebar Panel - Fixed on Desktop */}
      <Sidebar role="teacher" />

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0 w-full relative">
        {/* Top Header Bar - Fixed on Desktop */}
        <header className="hidden md:flex fixed top-0 right-0 h-topbar-height w-[calc(100%-240px)] bg-bg-deep border-b border-outline-variant/20 shadow-sm justify-between items-center px-xl z-40">
          <div className="flex items-center gap-lg min-w-0 flex-1">
            <h2 className="font-headline-sm text-headline-sm font-bold text-white transition-all duration-300 truncate">
              {title}
            </h2>
            {/* Sync Pill */}
            <div className={`flex items-center gap-sm px-sm py-xs rounded-full border transition-all ${
              pendingSyncCount > 0 
                ? 'bg-secondary/10 border-secondary/30 text-secondary' 
                : 'bg-success/10 border-success/30 text-success'
            }`}>
              <span className={`material-symbols-outlined text-base ${pendingSyncCount > 0 ? 'animate-spin' : ''}`}>
                {pendingSyncCount > 0 ? 'sync' : 'check_circle'}
              </span>
              <span className="font-caption text-caption font-bold">
                {pendingSyncCount > 0 ? 'Changes Pending' : 'Synced'}
              </span>
            </div>
          </div>

          <div className="flex items-center gap-lg flex-shrink-0">
            {/* Language toggle */}
            <button
              onClick={toggle}
              title="Switch language / மொழி மாற்று"
              className="flex items-center gap-1 p-sm text-on-surface-variant hover:text-primary-fixed hover:bg-surface-container-high rounded-full transition-all duration-200 active:scale-90"
            >
              <span className="material-symbols-outlined">language</span>
              <span className="font-caption text-caption font-bold uppercase">{lang}</span>
            </button>

            <div className="h-8 w-px bg-outline-variant/30"></div>

            {/* Profile Dropdown Indicator */}
            <div 
              onClick={() => navigate('/teacher/profile')}
              className="flex items-center gap-sm cursor-pointer hover:bg-surface-container-low px-md py-xs r-chip transition-all group"
            >
              <div className="text-right">
                <p className="font-body-sm text-sm font-bold text-white group-hover:text-primary-fixed transition-colors">
                  {session?.name || 'Lead Instructor'}
                </p>
                <p className="font-caption text-caption text-text-muted">
                  {session?.schoolCode || 'Ezhil Teacher'}
                </p>
              </div>
              <div className="w-10 h-10 rounded-full border border-secondary/20 bg-secondary/10 flex items-center justify-center font-bold text-secondary text-sm group-hover:scale-105 group-hover:border-secondary transition-all">
                {teacherInitials}
              </div>
            </div>
          </div>
        </header>

        {/* Content canvas - Scrollable with offset */}
        <main className="flex-1 w-full md:pl-0 md:pt-[64px] pb-24 md:pb-8 p-4 md:p-8 relative overflow-y-auto min-h-dvh">
          <div className="max-w-[1300px] mx-auto">
            <RouteTransition>
              <Outlet />
            </RouteTransition>
          </div>

          {/* Decorative Atmos Glow Background Circle */}
          <div className="fixed bottom-0 right-0 -z-10 w-96 h-96 opacity-[0.03] bg-primary-fixed rounded-full blur-[100px] pointer-events-none" />
        </main>

        {/* Bottom Nav Bar - Mobile Only */}
        <TeacherBottomNavBar />
      </div>
    </div>
  );
};
