/**
 * The route inventory both web suites drive from.
 *
 * Mirrors ezhil-web/src/App.tsx. Kept as data so a suite is a loop over real
 * routes rather than a hand-written list that drifts — and so a route added to
 * the app without being added here shows up as a coverage gap, which
 * lib/coverage.js asserts on.
 */

/** Public routes — no session required. */
const PUBLIC = [
  { path: '/', name: 'Splash', transient: true },
  { path: '/onboarding/1', name: 'Onboarding 1' },
  { path: '/onboarding/2', name: 'Onboarding 2' },
  { path: '/onboarding/3', name: 'Onboarding 3' },
  { path: '/role-selection', name: 'Role selection' },
  { path: '/login', name: 'Login' },
];

/** Behind RequireAuth role="student". */
const STUDENT = [
  { path: '/student/profile-select', name: 'Profile select' },
  { path: '/student/home', name: 'Student home' },
  { path: '/student/lessons', name: 'Student lessons' },
  { path: '/student/quiz', name: 'Lesson quiz' },
  { path: '/student/assessment/history', name: 'Assessment history' },
  { path: '/student/games', name: 'Phonics games hub' },
  { path: '/student/journey', name: 'Journey map' },
  { path: '/student/lesson/reader', name: 'Lesson reader' },
  { path: '/student/lesson/complete', name: 'Lesson complete' },
  { path: '/student/assessment/start', name: 'Assessment start' },
  { path: '/student/assessment/recording', name: 'Assessment recording' },
  { path: '/student/assessment/processing', name: 'Assessment processing', transient: true },
  { path: '/student/assessment/complete', name: 'Assessment complete' },
  { path: '/student/assessment/timeout', name: 'Assessment timeout' },
  { path: '/student/quiz/feedback/correct', name: 'Quiz correct feedback' },
  { path: '/student/quiz/feedback/wrong', name: 'Quiz wrong feedback' },
  { path: '/student/achievement', name: 'Achievement' },
  { path: '/student/milestone', name: 'Streak milestone' },
  { path: '/student/games/match-sound/ready', name: 'Match sound ready' },
  { path: '/student/games/match-sound/playing', name: 'Match sound playing' },
  { path: '/student/games/spot-letter/playing', name: 'Spot letter playing' },
  { path: '/student/games/spot-letter/round-complete', name: 'Spot letter round complete' },
  { path: '/student/games/build-word/playing', name: 'Build word playing' },
  { path: '/student/games/build-word/success', name: 'Build word success' },
  { path: '/student/games/summary', name: 'Game summary' },
];

/** Behind RequireAuth role="teacher". */
const TEACHER = [
  { path: '/teacher/dashboard', name: 'Teacher dashboard' },
  { path: '/teacher/roster', name: 'Student roster' },
  { path: '/teacher/student-profile', name: 'Student profile (teacher)' },
  { path: '/teacher/add-student', name: 'Add student' },
  { path: '/teacher/lessons', name: 'Lesson library' },
  { path: '/teacher/lesson-studio', name: 'Lesson studio' },
  { path: '/teacher/reports', name: 'Reports' },
  { path: '/teacher/risk-flags', name: 'Risk flags' },
  { path: '/teacher/profile', name: 'Teacher profile' },
];

const ALL = [
  ...PUBLIC.map(r => ({ ...r, role: 'public' })),
  ...STUDENT.map(r => ({ ...r, role: 'student' })),
  ...TEACHER.map(r => ({ ...r, role: 'teacher' })),
];

/**
 * Index routes redirect rather than render, so a "renders" assertion against
 * them is really an assertion about the redirect. Kept separate.
 */
const REDIRECTS = [
  { path: '/student', to: '/student/home', role: 'student', name: 'Student index redirect' },
  { path: '/teacher', to: '/teacher/dashboard', role: 'teacher', name: 'Teacher index redirect' },
  { path: '/no-such-page', to: '/', role: 'public', name: 'Unknown route redirect' },
];

module.exports = { PUBLIC, STUDENT, TEACHER, ALL, REDIRECTS };
