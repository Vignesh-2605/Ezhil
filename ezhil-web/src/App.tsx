import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext';
import { LanguageProvider } from './contexts/LanguageContext';

import { TeacherLayout } from './components/layout/TeacherLayout';
import { StudentLayout } from './components/layout/StudentLayout';
import { RequireAuth } from './components/RequireAuth';

/* ── Auth ── */
import { SplashScreen }     from './pages/auth/SplashScreen';
import { OnboardingSlide1 } from './pages/auth/OnboardingSlide1';
import { OnboardingSlide2 } from './pages/auth/OnboardingSlide2';
import { OnboardingSlide3 } from './pages/auth/OnboardingSlide3';
import { RoleSelection }    from './pages/auth/RoleSelection';
import { Login }            from './pages/auth/Login';

/* ── Student ── */
import { StudentProfileSelect }       from './pages/student/StudentProfileSelect';
import { StudentHome }                from './pages/student/StudentHome';
import { StudentLessonsList }         from './pages/student/StudentLessonsList';
import { LessonReader }               from './pages/student/LessonReader';
import { AssessmentStart }            from './pages/student/AssessmentStart';
import { AssessmentRecording }        from './pages/student/AssessmentRecording';
import { AssessmentProcessing }       from './pages/student/AssessmentProcessing';
import { AssessmentComplete }         from './pages/student/AssessmentComplete';
import { AssessmentTimeout }          from './pages/student/AssessmentTimeout';
import { AssessmentHistory }          from './pages/student/AssessmentHistory';
import { LessonQuiz }                 from './pages/student/LessonQuiz';
import { QuizCorrectFeedback }        from './pages/student/QuizCorrectFeedback';
import { QuizWrongFeedback }          from './pages/student/QuizWrongFeedback';
import { LessonComplete }             from './pages/student/LessonComplete';
import { VocabularyDetail }           from './pages/student/VocabularyDetail';
import { PhonicsGamesHub }            from './pages/student/PhonicsGamesHub';
import { MatchSoundReady }            from './pages/student/MatchSoundReady';
import { MatchSoundPlaying }          from './pages/student/MatchSoundPlaying';
import { SpotLetterPlaying }          from './pages/student/SpotLetterPlaying';
import { SpotLetterRoundComplete }    from './pages/student/SpotLetterRoundComplete';
import { BuildWordPlaying }           from './pages/student/BuildWordPlaying';
import { BuildWordSuccess }           from './pages/student/BuildWordSuccess';
import { GameSummary }                from './pages/student/GameSummary';
import { JourneyMap }                 from './pages/student/JourneyMap';
import { StreakMilestoneCelebration } from './pages/student/StreakMilestoneCelebration';
import { AchievementScreen }          from './pages/student/AchievementScreen';

/* ── Teacher ── */
import { TeacherDashboard }          from './pages/teacher/TeacherDashboard';
import { AddStudentForm }            from './pages/teacher/AddStudentForm';
import { StudentRoster }             from './pages/teacher/StudentRoster';
import { StudentProfileTeacherView } from './pages/teacher/StudentProfileTeacherView';
import { LessonLibrary }             from './pages/teacher/LessonLibrary';
import { LessonStudio }              from './pages/teacher/LessonStudio';
import { ReportsPage }               from './pages/teacher/ReportsPage';
import { RiskFlagsPage }             from './pages/teacher/RiskFlagsPage';
import { TeacherProfile }            from './pages/teacher/TeacherProfile';

function App() {
  return (
    <AuthProvider>
      <LanguageProvider>
        <BrowserRouter>
          <Routes>
            {/* Auth / Onboarding */}
            <Route path="/"               element={<SplashScreen />} />
            <Route path="/onboarding/1"   element={<OnboardingSlide1 />} />
            <Route path="/onboarding/2"   element={<OnboardingSlide2 />} />
            <Route path="/onboarding/3"   element={<OnboardingSlide3 />} />
            <Route path="/role-selection" element={<RoleSelection />} />
            <Route path="/login"          element={<Login />} />

            {/* Student routes — all require a student session */}
            <Route element={<RequireAuth role="student" />}>
              {/* Student profile picker (no shell) */}
              <Route path="/student/profile-select" element={<StudentProfileSelect />} />

              {/* Full-screen student flows (no shell, immersive) */}
              <Route path="/student/lesson/reader"          element={<LessonReader />} />
              <Route path="/student/assessment/start"       element={<AssessmentStart />} />
              <Route path="/student/assessment/recording"   element={<AssessmentRecording />} />
              <Route path="/student/assessment/processing"  element={<AssessmentProcessing />} />
              <Route path="/student/assessment/complete"    element={<AssessmentComplete />} />
              <Route path="/student/assessment/timeout"     element={<AssessmentTimeout />} />
              <Route path="/student/quiz/feedback/correct"  element={<QuizCorrectFeedback />} />
              <Route path="/student/quiz/feedback/wrong"    element={<QuizWrongFeedback />} />
              <Route path="/student/lesson/complete"        element={<LessonComplete />} />
              <Route path="/student/achievement"            element={<AchievementScreen />} />
              <Route path="/student/milestone"              element={<StreakMilestoneCelebration />} />
              <Route path="/student/games/match-sound/ready"          element={<MatchSoundReady />} />
              <Route path="/student/games/match-sound/playing"        element={<MatchSoundPlaying />} />
              <Route path="/student/games/spot-letter/playing"        element={<SpotLetterPlaying />} />
              <Route path="/student/games/spot-letter/round-complete" element={<SpotLetterRoundComplete />} />
              <Route path="/student/games/build-word/playing"         element={<BuildWordPlaying />} />
              <Route path="/student/games/build-word/success"         element={<BuildWordSuccess />} />
              <Route path="/student/games/summary"                    element={<GameSummary />} />

              {/* Student shell — sidebar + bottom nav */}
              <Route path="/student" element={<StudentLayout />}>
                <Route index element={<Navigate to="home" replace />} />
                <Route path="home"                element={<StudentHome />} />
                <Route path="lessons"             element={<StudentLessonsList />} />
                <Route path="quiz"                element={<LessonQuiz />} />
                <Route path="vocabulary/:id"      element={<VocabularyDetail />} />
                <Route path="assessment/history"  element={<AssessmentHistory />} />
                <Route path="games"               element={<PhonicsGamesHub />} />
                <Route path="journey"             element={<JourneyMap />} />
              </Route>
            </Route>

            {/* Teacher routes — all require a teacher session */}
            <Route element={<RequireAuth role="teacher" />}>
              <Route path="/teacher" element={<TeacherLayout />}>
                <Route index element={<Navigate to="dashboard" replace />} />
                <Route path="dashboard"      element={<TeacherDashboard />} />
                <Route path="roster"         element={<StudentRoster />} />
                <Route path="student-profile" element={<StudentProfileTeacherView />} />
                <Route path="add-student"    element={<AddStudentForm />} />
                <Route path="lessons"        element={<LessonLibrary />} />
                <Route path="lesson-studio"  element={<LessonStudio />} />
                <Route path="reports"        element={<ReportsPage />} />
                <Route path="risk-flags"     element={<RiskFlagsPage />} />
                <Route path="profile"        element={<TeacherProfile />} />
              </Route>
            </Route>

            {/* Catch-all */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </LanguageProvider>
    </AuthProvider>
  );
}

export default App;
