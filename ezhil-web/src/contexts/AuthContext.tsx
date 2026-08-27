import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';

export type UserRole = 'teacher' | 'student';

export interface Session {
  accessToken: string;
  role: UserRole;
  name: string;
  userId: string;
  studentId?: string;
  schoolCode?: string;
  schoolName?: string;
  teacherId?: string;
  teacherName?: string;
}

interface AuthContextType {
  session: Session | null;
  loading: boolean;
  loginTeacher: (
    accessToken: string,
    name: string,
    userId: string,
    schoolCode: string,
    schoolName: string,
    teacherId: string,
    teacherName: string
  ) => void;
  loginStudent: (
    accessToken: string,
    name: string,
    userId: string,
    studentId: string,
    schoolCode: string,
    schoolName: string,
    teacherId: string,
    teacherName: string
  ) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

const SESSION_KEY = 'ezhil_session';

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [session, setSession] = useState<Session | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const stored = localStorage.getItem(SESSION_KEY);
    if (stored) {
      try { setSession(JSON.parse(stored)); } catch { localStorage.removeItem(SESSION_KEY); }
    }
    setLoading(false);
  }, []);

  const persist = (s: Session) => {
    setSession(s);
    localStorage.setItem(SESSION_KEY, JSON.stringify(s));
  };

  const loginTeacher = (
    accessToken: string,
    name: string,
    userId: string,
    schoolCode: string,
    schoolName: string,
    teacherId: string,
    teacherName: string
  ) =>
    persist({
      accessToken,
      role: 'teacher',
      name,
      userId,
      schoolCode,
      schoolName,
      teacherId,
      teacherName
    });

  const loginStudent = (
    accessToken: string,
    name: string,
    userId: string,
    studentId: string,
    schoolCode: string,
    schoolName: string,
    teacherId: string,
    teacherName: string
  ) =>
    persist({
      accessToken,
      role: 'student',
      name,
      userId,
      studentId,
      schoolCode,
      schoolName,
      teacherId,
      teacherName
    });

  const logout = () => {
    setSession(null);
    localStorage.removeItem(SESSION_KEY);
  };

  return (
    <AuthContext.Provider value={{ session, loading, loginTeacher, loginStudent, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be inside AuthProvider');
  return ctx;
};
