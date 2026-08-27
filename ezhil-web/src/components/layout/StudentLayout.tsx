import React from 'react';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { StudentBottomNavBar } from './StudentBottomNavBar';
import { RouteTransition } from '../motion/RouteTransition';

export const StudentLayout: React.FC = () => (
  <div className="flex min-h-dvh bg-transparent w-full">
    <Sidebar role="student" />
    <div className="flex-1 flex flex-col w-full min-w-0 relative">
      <main className="flex-1 w-full max-w-7xl mx-auto pb-24 md:pb-8 p-4 md:p-8">
        <RouteTransition>
          <Outlet />
        </RouteTransition>
      </main>
      <StudentBottomNavBar />
    </div>
  </div>
);
