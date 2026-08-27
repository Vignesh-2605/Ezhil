import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useLanguage } from '../../contexts/LanguageContext';
import { LanguageToggle } from '../../components/ui/LanguageToggle';

export const RoleSelection: React.FC = () => {
  const navigate = useNavigate();
  const { t } = useLanguage();

  return (
    <div className="bg-bg-deep text-on-surface min-h-dvh font-body-tamil overflow-hidden relative select-none">
      {/* Background spotlights */}
      <div className="absolute inset-0 pointer-events-none -z-10 overflow-hidden">
        <div className="absolute -top-48 left-1/2 -translate-x-1/2 w-[600px] h-[600px] bg-primary-fixed/5 rounded-full blur-[120px]" />
        <div className="absolute top-[60%] -left-40 w-96 h-96 bg-primary-fixed/5 rounded-full blur-[100px]" />
        <div className="absolute top-[40%] -right-40 w-96 h-96 bg-studio-purple/5 rounded-full blur-[100px]" />
      </div>

      {/* Centered spacious wrapper */}
      <div className="w-full max-w-4xl min-h-dvh mx-auto flex flex-col justify-between py-10 px-6 md:px-10 relative z-10">
        {/* Header back button */}
        <header className="w-full flex justify-start">
          <button
            onClick={() => navigate('/onboarding/3')}
            className="flex items-center gap-1.5 text-on-surface-variant hover:text-on-surface text-sm font-semibold transition-colors duration-200 cursor-pointer"
          >
            <span className="material-symbols-outlined text-lg">arrow_back</span>
            <span>{t('பின்செல் / Back', 'Back')}</span>
          </button>
        </header>

        {/* Main Content Area */}
        <main className="flex-1 flex flex-col items-center justify-center w-full my-8">
          <div className="text-center mb-12 animate-fade-in w-full">
            <h1 className="font-display-tamil heading-display-accent text-[44px] font-black leading-tight">
              {t('யார் நீ?', 'Who are you?')}
            </h1>
            <p className="font-bilingual-sub text-base text-text-muted mt-2 font-medium">
              {t('தொடர உங்கள் பங்கைத் தேர்ந்தெடுக்கவும்', 'Select your role to continue')}
            </p>
            <div className="w-16 h-[3px] bg-gradient-to-r from-transparent via-primary-fixed/50 to-transparent mx-auto mt-4 rounded-full" />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-8 w-full">
            {/* Student Card */}
            <button
              onClick={() => navigate('/login?role=student')}
              className="group relative flex flex-col justify-between items-start text-left p-10 rounded-[32px] bg-gradient-to-b from-bg-student-card to-bg-deep border-2 border-primary-fixed/20 hover:border-primary-fixed hover:shadow-[0_24px_50px_rgba(98,249,238,0.14)] transition-all duration-300 transform hover:-translate-y-2 cursor-pointer overflow-hidden h-[260px] w-full"
            >
              {/* Glossy shine effect on hover */}
              <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/5 to-transparent -translate-x-full group-hover:translate-x-full transition-transform duration-1000" />
              <div className="absolute -top-12 -right-12 w-32 h-32 bg-primary-fixed/5 rounded-full blur-2xl group-hover:bg-primary-fixed/10 transition-colors" />

              <div className="flex items-center justify-between w-full">
                <div className="w-16 h-16 rounded-[20px] bg-primary-fixed/10 border border-primary-fixed/20 flex items-center justify-center group-hover:scale-110 group-hover:border-primary-fixed/40 transition-all duration-300">
                  <span className="material-symbols-outlined text-primary-fixed text-[36px] group-hover:animate-pulse">
                    mic
                  </span>
                </div>
                <span className="material-symbols-outlined text-primary-fixed opacity-40 group-hover:opacity-100 group-hover:translate-x-2 transition-all duration-300 text-[32px]">
                  arrow_forward
                </span>
              </div>

              <div className="space-y-2 relative z-10 mt-6 w-full">
                <h2 className="font-dashboard-title text-2xl font-black text-primary-fixed leading-none">
                  {t('மாணவர்', 'Student')}
                </h2>
                <h3 className="font-bilingual-sub text-base text-on-surface font-semibold">
                  {t('மாணவர் உள்நுழைவு', 'Student Login')}
                </h3>
                <p className="font-body-tamil text-sm text-on-surface-variant leading-relaxed line-clamp-2 mt-2">
                  {t('கதைகள் வாசித்தல், விளையாடுதல் மற்றும் தமிழ் கற்றல்', 'Read stories, play games and learn Tamil')}
                </p>
              </div>
            </button>

            {/* Teacher Card */}
            <button
              onClick={() => navigate('/login?role=teacher')}
              className="group relative flex flex-col justify-between items-start text-left p-10 rounded-[32px] bg-gradient-to-b from-bg-teacher-card to-bg-deep border-2 border-accent-teal/20 hover:border-accent-teal hover:shadow-[0_24px_50px_rgba(0,229,200,0.14)] transition-all duration-300 transform hover:-translate-y-2 cursor-pointer overflow-hidden h-[260px] w-full"
            >
              {/* Glossy shine effect on hover */}
              <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/5 to-transparent -translate-x-full group-hover:translate-x-full transition-transform duration-1000" />
              <div className="absolute -top-12 -right-12 w-32 h-32 bg-accent-teal/5 rounded-full blur-2xl group-hover:bg-accent-teal/10 transition-colors" />

              <div className="flex items-center justify-between w-full">
                <div className="w-16 h-16 rounded-[20px] bg-accent-teal/10 border border-accent-teal/20 flex items-center justify-center group-hover:scale-110 group-hover:border-accent-teal/40 transition-all duration-300">
                  <span className="material-symbols-outlined text-accent-teal text-[36px] group-hover:animate-pulse">
                    bar_chart
                  </span>
                </div>
                <span className="material-symbols-outlined text-accent-teal opacity-40 group-hover:opacity-100 group-hover:translate-x-2 transition-all duration-300 text-[32px]">
                  arrow_forward
                </span>
              </div>

              <div className="space-y-2 relative z-10 mt-6 w-full">
                <h2 className="font-dashboard-title text-2xl font-black text-accent-teal leading-none">
                  {t('ஆசிரியர்', 'Teacher')}
                </h2>
                <h3 className="font-bilingual-sub text-base text-on-surface font-semibold">
                  {t('ஆசிரியர் உள்நுழைவு', 'Teacher Login')}
                </h3>
                <p className="font-body-tamil text-sm text-on-surface-variant leading-relaxed line-clamp-2 mt-2">
                  {t('மாணவர் மதிப்பீடு, முன்னேற்றக் கண்காணிப்பு மற்றும் வகுப்பறை மேலாண்மை', 'Student assessment, progress tracking and classroom management')}
                </p>
              </div>
            </button>
          </div>
        </main>

        {/* Language Switcher Footer */}
        <footer className="w-full flex justify-center mt-auto relative z-10">
          <div className="px-6 py-3 rounded-full bg-bg-surface/35 border border-white/5 shadow-[0_8px_32px_rgba(0,0,0,0.3)] backdrop-blur-md">
            <LanguageToggle />
          </div>
        </footer>
      </div>
    </div>
  );
};
