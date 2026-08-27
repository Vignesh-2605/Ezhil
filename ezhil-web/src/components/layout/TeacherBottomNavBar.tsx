import React from 'react';
import { NavLink } from 'react-router-dom';
import clsx from 'clsx';

const NAV = [
  { to: '/teacher/dashboard', icon: 'dashboard', label: 'முகப்பு' },
  { to: '/teacher/lessons',   icon: 'menu_book', label: 'பாடங்கள்' },
  { to: '/teacher/reports',   icon: 'leaderboard', label: 'அறிக்கை' },
  { to: '/teacher/profile',   icon: 'person', label: 'சுயவிவரம்' },
];

export const TeacherBottomNavBar: React.FC = () => (
  <nav className="fixed bottom-0 left-0 w-full z-50 flex justify-around items-center px-2 pb-2 pt-1 h-20 bg-bg-deep/85 backdrop-blur-xl border-t border-white/5 shadow-[0_-8px_24px_rgba(0,0,0,0.55)] rounded-t-2xl md:hidden">
    <div className="absolute inset-x-8 top-0 h-px bg-gradient-to-r from-transparent via-primary-fixed/40 to-transparent" />
    {NAV.map(({ to, icon, label }) => (
      <NavLink
        key={to}
        to={to}
        className={({ isActive }) => clsx(
          'relative flex flex-col items-center justify-center flex-1 min-w-0 px-1 py-1.5 r-card transition-all duration-300 active:scale-90 group',
          isActive ? 'text-primary-fixed' : 'text-border-muted hover:text-primary-fixed/80',
        )}
      >
        {({ isActive }) => (
          <>
            <span className={clsx(
              'absolute inset-0 r-card transition-all duration-300',
              isActive ? 'bg-primary-fixed/10 border border-primary-fixed/25 shadow-[0_0_18px_rgba(98,249,238,0.3)]' : 'border border-transparent',
            )} />
            <span
              className={clsx('relative material-symbols-outlined text-2xl transition-transform duration-300', isActive ? '-translate-y-0.5' : 'group-hover:-translate-y-0.5')}
              style={{ fontVariationSettings: isActive ? "'FILL' 1" : "'FILL' 0" }}
            >
              {icon}
            </span>
            {/* Tamil labels are long. Without a width share and wrapping, the row
                grew past a 375px phone and the whole page scrolled sideways. */}
            <span className="relative font-dashboard-title text-xs font-semibold mt-0.5 w-full text-center leading-tight break-words">{label}</span>
            <span className={clsx(
              'absolute -bottom-0.5 w-1 h-1 rounded-full bg-primary-fixed transition-all duration-300',
              isActive ? 'opacity-100 shadow-[0_0_6px_rgba(98,249,238,0.9)]' : 'opacity-0',
            )} />
          </>
        )}
      </NavLink>
    ))}
  </nav>
);
