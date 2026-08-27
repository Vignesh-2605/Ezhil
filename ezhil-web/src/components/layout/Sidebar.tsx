import React, { useState, useEffect } from 'react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { db } from '../../db/db';
import { useLiveQuery } from 'dexie-react-hooks';
import { SyncManager } from '../../services/syncManager';
import { EzhilMark } from '../brand/EzhilLogo';

interface NavItem {
  icon: string;
  label: string;
  subLabel: string;
  to: string;
}

const TEACHER_ITEMS: NavItem[] = [
  { icon: 'dashboard',  label: 'Dashboard', subLabel: 'கட்டுப்பாட்டகம்', to: '/teacher/dashboard'  },
  { icon: 'group',      label: 'Roster',    subLabel: 'மாணவர் பட்டியல்',  to: '/teacher/roster'     },
  { icon: 'auto_stories',  label: 'Lesson Studio', subLabel: 'பாட Studio', to: '/teacher/lesson-studio' },
  { icon: 'assessment', label: 'Reports',   subLabel: 'அறிக்கைகள்',       to: '/teacher/reports'    },
  { icon: 'settings',   label: 'Settings',  subLabel: 'அமைப்புகள்',        to: '/teacher/profile'    },
];

const STUDENT_ITEMS: NavItem[] = [
  { icon: 'home',           label: 'Home',    subLabel: 'முகப்பு',         to: '/student/home'    },
  { icon: 'menu_book',      label: 'Lessons', subLabel: 'பாடங்கள்',        to: '/student/lessons' },
  { icon: 'sports_esports', label: 'Games',   subLabel: 'விளையாட்டுகள்',  to: '/student/games'   },
  { icon: 'map',            label: 'Journey', subLabel: 'என் பயணம்',      to: '/student/journey' },
];

export const Sidebar: React.FC<{ role: 'teacher' | 'student' }> = ({ role }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const { session, logout } = useAuth();
  const teacherId = session?.userId || '';

  const items = role === 'teacher' ? TEACHER_ITEMS : STUDENT_ITEMS;

  // Sync state tracking
  const [syncing, setSyncing] = useState(false);
  const [syncProgress, setSyncProgress] = useState(100);

  // Monitor pending sync records
  const pendingSyncCount = useLiveQuery(async () => {
    if (role !== 'teacher') return 0;
    const st = await db.students.where('syncStatus').equals('pending').count();
    const as = await db.assessments.where('syncStatus').equals('pending').count();
    const gs = await db.game_sessions.where('syncStatus').equals('pending').count();
    const lp = await db.lesson_progress.where('syncStatus').equals('pending').count();
    return st + as + gs + lp;
  }, [role]) || 0;

  // Trigger auto sync when pending records count changes
  useEffect(() => {
    if (pendingSyncCount > 0 && !syncing) {
      handleAutoSync();
    } else if (pendingSyncCount === 0) {
      setSyncProgress(100);
    }
  }, [pendingSyncCount]);

  const handleAutoSync = async () => {
    setSyncing(true);
    setSyncProgress(30);
    try {
      setSyncProgress(60);
      const res = await SyncManager.sync();
      if (res.success) {
        setSyncProgress(100);
      }
    } catch (err) {
      console.error('Auto sync failed:', err);
    } finally {
      setSyncing(false);
    }
  };

  return (
    <aside className="hidden md:flex flex-col w-[240px] h-dvh bg-bg-sidebar border-r border-outline-variant/20 shadow-2xl z-50 sticky top-0 flex-shrink-0 py-lg select-none">
      {/* Brand Identity */}
      <div className="px-lg mb-xl flex items-center gap-3">
        <EzhilMark size={44} className="flex-shrink-0 drop-shadow-[0_0_14px_rgba(98,249,238,0.2)]" />
        <div className="min-w-0">
          <h1 className="font-display-tamil heading-display text-[22px] leading-none">எழில்</h1>
          <p className="font-caption text-caption text-on-surface-variant tracking-[0.18em] uppercase mt-0.5">
            {role === 'teacher' ? 'Teacher Portal' : 'Student Portal'}
          </p>
        </div>
      </div>

      {/* Main Navigation */}
      <nav className="flex-1 space-y-sm px-sm overflow-y-auto hide-scrollbar">
        {items.map((item) => {
          const isActive = location.pathname === item.to || location.pathname.startsWith(item.to + '/');
          return (
            <NavLink
              key={item.to}
              to={item.to}
              className={`relative flex items-center gap-md py-sm px-md r-chip transition-all duration-200 group active:scale-[0.98] ${
                isActive
                  ? 'text-secondary font-bold pl-3 bg-gradient-to-r from-secondary/15 to-transparent border border-secondary/20 shadow-[0_0_16px_rgba(255,185,85,0.12)]'
                  : 'text-on-surface-variant font-medium pl-4 border border-transparent hover:bg-surface-variant hover:text-secondary hover:translate-x-0.5'
              }`}
            >
              {isActive && (
                <span className="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-6 rounded-r-full bg-secondary shadow-[0_0_10px_rgba(255,185,85,0.7)]" />
              )}
              <span
                className="material-symbols-outlined text-2xl transition-transform group-hover:scale-110"
                style={{ fontVariationSettings: isActive ? "'FILL' 1" : "'FILL' 0" }}
              >
                {item.icon}
              </span>
              <div className="flex flex-col">
                <span className="font-body-sm text-sm leading-tight">{item.label}</span>
                <span className={`font-tamil-body text-xs leading-tight ${isActive ? 'text-secondary/80' : 'text-text-muted'}`}>
                  {item.subLabel}
                </span>
              </div>
            </NavLink>
          );
        })}
      </nav>

      {/* Footer Profile & Sync indicator */}
      <div className="mt-auto border-t border-outline-variant/20 pt-lg space-y-md">
        {/* Profile Button */}
        <NavLink
          to={role === 'teacher' ? '/teacher/profile' : '/student/profile-select'}
          className={({ isActive }) =>
            `flex items-center gap-md py-sm pl-4 pr-md r-chip transition-colors text-on-surface-variant font-medium hover:bg-surface-variant hover:text-secondary ${
              isActive ? 'text-secondary bg-surface-container-low font-bold border-l-4 border-secondary pl-3' : ''
            }`
          }
        >
          <span className="material-symbols-outlined">account_circle</span>
          <div className="flex flex-col">
            <span className="font-body-sm text-sm leading-tight">Profile</span>
            <span className="font-tamil-body text-xs text-text-muted">சுயவிவரம்</span>
          </div>
        </NavLink>

        {/* Sync Card widget */}
        {role === 'teacher' && (
          <div className="mx-md p-md r-chip bg-secondary-container/10 border border-secondary/20 transition-all duration-300">
            <div className="flex items-center justify-between mb-xs">
              <span className={`text-caption font-caption flex items-center gap-xs font-bold ${pendingSyncCount > 0 ? 'text-secondary' : 'text-success'}`}>
                <span className={`material-symbols-outlined text-sm ${syncing ? 'animate-spin' : ''}`}>
                  {pendingSyncCount > 0 ? 'sync' : 'check_circle'}
                </span>
                {syncing ? 'Syncing...' : pendingSyncCount > 0 ? `${pendingSyncCount} Pending` : 'System Synced'}
              </span>
              <span className={`text-caption font-caption font-mono ${pendingSyncCount > 0 ? 'text-secondary' : 'text-success'}`}>
                {syncProgress}%
              </span>
            </div>
            <div className="w-full bg-surface-container-highest h-1.5 rounded-full overflow-hidden">
              <div
                className={`h-full transition-all duration-500 ease-out ${pendingSyncCount > 0 ? 'bg-secondary' : 'bg-success'}`}
                style={{ width: `${syncProgress}%` }}
              />
            </div>
          </div>
        )}

        {/* Logout action */}
        <div className="px-md">
          <button
            onClick={() => {
              logout();
              navigate('/login');
            }}
            className="w-full flex items-center justify-center gap-sm py-sm border border-outline-variant/30 r-chip text-body-sm text-text-muted font-bold hover:bg-error/10 hover:text-error hover:border-error/30 transition-all duration-200 active:scale-95"
          >
            <span className="material-symbols-outlined text-lg">logout</span>
            <span>Logout</span>
          </button>
        </div>
      </div>
    </aside>
  );
};
