import React from 'react';
import { useNavigate } from 'react-router-dom';
import { SceneRead } from '../../components/illustrations/OnboardingScenes';

export const OnboardingSlide1: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div className="bg-bg-deep text-on-surface min-h-dvh font-body-tamil overflow-hidden relative select-none grid grid-cols-1 lg:grid-cols-12">
      
      {/* LEFT COLUMN: Immersive Visual Showcase (Visible on Desktop) */}
      <section className="hidden lg:flex lg:col-span-7 relative flex-col items-center justify-center border-r border-white/5 bg-[radial-gradient(circle_at_30%_30%,_rgba(98,249,238,0.08)_0%,_transparent_60%)]">
        {/* Background ambient glows */}
        <div className="absolute top-1/4 left-1/4 w-[400px] h-[400px] rounded-full bg-primary-fixed/5 blur-[120px] animate-pulse" />
        <div className="absolute bottom-1/4 right-1/4 w-[350px] h-[350px] bg-studio-purple/3 rounded-full blur-[100px]" />
        
        {/* Decorative watermark */}
        <div className="absolute top-12 left-12 opacity-15">
          <span className="font-mono-metadata text-xs uppercase tracking-[0.3em] text-text-muted">
            Ezhil Literacy Project
          </span>
        </div>

        {/* Large floating graphic container */}
        <div className="relative z-10 scale-110">
          {/* Orbiting rings */}
          <div className="absolute -inset-10 rounded-full border border-primary-fixed/10 animate-spin [animation-duration:20s]" />
          <div className="absolute -inset-6 rounded-full border border-dashed border-accent-cyan/15 animate-spin [animation-duration:15s] [animation-direction:reverse]" />
          
          <div className="relative animate-float">
            <div className="absolute inset-0 bg-primary-fixed/20 opacity-30 blur-3xl rounded-full" />
            <div className="relative z-10 drop-shadow-[0_24px_50px_rgba(0,0,0,0.55)]">
              <SceneRead size={280} />
            </div>
            {/* Sparkle particles */}
            <div className="absolute -top-3 right-6 w-4 h-4 rounded-full bg-primary-fixed animate-ping opacity-60" />
            <div className="absolute -bottom-4 left-6 w-3 h-3 rounded-full bg-accent-cyan animate-pulse" />
            <div className="absolute top-1/2 -right-8 w-2 h-2 rounded-full bg-white opacity-40 animate-ping" />
          </div>
        </div>

        {/* Quotes banner */}
        <div className="absolute bottom-16 text-center space-y-2 px-8">
          <p className="font-display-tamil text-2xl font-bold text-on-surface-variant opacity-80">
            "ஒரு நேரத்தில் ஒரு கதை..."
          </p>
          <p className="font-dashboard-title text-sm text-text-muted uppercase tracking-[0.2em] opacity-65">
            One story at a time
          </p>
        </div>
      </section>

      {/* RIGHT COLUMN: Interactive Onboarding Panel */}
      <section className="col-span-1 lg:col-span-5 flex flex-col justify-between p-6 md:p-10 relative z-10 bg-bg-sidebar/20 backdrop-blur-md">
        {/* Mobile background glows */}
        <div className="absolute inset-0 pointer-events-none lg:hidden -z-10 overflow-hidden">
          <div className="absolute top-1/4 left-1/2 -translate-x-1/2 w-[300px] h-[300px] rounded-full bg-primary-fixed/5 blur-[100px]" />
          <div className="absolute bottom-10 right-10 w-64 h-64 bg-studio-purple/5 rounded-full blur-[80px]" />
        </div>

        {/* Skip button header */}
        <header className="w-full flex justify-between items-center h-12">
          {/* Logo signature visible on mobile */}
          <div className="lg:hidden flex items-center gap-2">
            <span className="font-display-tamil heading-display-accent text-xl font-black">எழில்</span>
            <span className="text-xs font-mono-metadata text-text-muted opacity-60 uppercase tracking-widest mt-1">Ezhil</span>
          </div>
          <div className="ml-auto">
            <button 
              onClick={() => navigate('/role-selection')}
              className="bg-white/5 hover:bg-white/10 text-on-surface-variant hover:text-on-surface py-1.5 px-4 rounded-full border border-white/5 hover:border-white/10 transition-all duration-300 text-xs font-semibold uppercase backdrop-blur-md cursor-pointer"
            >
              Skip / தவிர்
            </button>
          </div>
        </header>

        {/* Main Content Card Container */}
        <main className="my-auto py-8 flex flex-col items-center w-full">
          {/* Mobile visible graphic placeholder */}
          <div className="lg:hidden relative mb-6 animate-float">
            <div className="absolute inset-0 bg-primary-fixed/15 opacity-30 blur-2xl rounded-full" />
            <div className="relative z-10 drop-shadow-[0_14px_30px_rgba(0,0,0,0.5)]">
              <SceneRead size={150} />
            </div>
            <div className="absolute -top-1 -right-1 w-2.5 h-2.5 rounded-full bg-primary-fixed animate-ping opacity-60" />
          </div>

          {/* Unified layout card: flex-col, min-h-[500px] */}
          <div className="min-w-[300px] w-[320px] max-w-[360px] min-h-[500px] shrink-0 glass-card r-hero surface-lit grain p-8 flex flex-col items-center text-center border-white/5 animate-slide-in relative overflow-hidden">
            {/* Accent light stripe */}
            <div className="absolute top-0 inset-x-0 h-[2px] bg-gradient-to-r from-transparent via-primary-fixed/30 to-transparent" />

            {/* Dynamic Content Section (flex-grow) */}
            <div className="flex-1 flex flex-col justify-center space-y-4 mb-6 w-full whitespace-normal break-normal break-words">
              <h1 className="font-display-tamil heading-display-accent text-3xl">
                படிக்கலாம்!
              </h1>
              <h2 className="font-dashboard-title text-xl font-bold text-on-surface">
                Let's Read!
              </h2>
              <div className="w-12 h-[2px] bg-primary-fixed/20 mx-auto my-3" />
              <p className="font-body-tamil text-sm text-on-surface-variant leading-relaxed px-2">
                Ezhil helps you read Tamil better, one story at a time.
              </p>
            </div>

            {/* Actions Section (mt-auto) */}
            <div className="mt-auto w-full flex flex-col items-center gap-6">
              {/* Pagination dots */}
              <div className="flex items-center justify-center gap-2.5">
                <div className="h-1.5 w-6 bg-primary-fixed rounded-full shadow-[0_0_8px_rgba(98,249,238,0.5)] transition-all duration-300" />
                <div 
                  className="h-1.5 w-1.5 bg-border-muted hover:bg-white/20 cursor-pointer rounded-full transition-all duration-300" 
                  onClick={() => navigate('/onboarding/2')} 
                />
                <div 
                  className="h-1.5 w-1.5 bg-border-muted hover:bg-white/20 cursor-pointer rounded-full transition-all duration-300" 
                  onClick={() => navigate('/onboarding/3')} 
                />
              </div>

              {/* Navigation button */}
              <div className="flex items-center justify-center gap-3 w-full h-12">
                <button 
                  onClick={() => navigate('/onboarding/2')}
                  className="w-[160px] h-12 bg-primary-fixed hover:bg-primary-fixed-dim text-bg-deep font-dashboard-title font-bold r-chip flex items-center justify-center gap-2 active:scale-95 hover:scale-[1.01] hover:shadow-[0_0_24px_rgba(98,249,238,0.35)] transition-all duration-200 cursor-pointer"
                >
                  <span>அடுத்து / Next</span>
                  <span className="material-symbols-outlined text-lg font-bold">
                    arrow_forward
                  </span>
                </button>
              </div>
            </div>
          </div>
        </main>

        {/* Empty footer layout placeholder to maintain grid split height */}
        <div className="h-4" />
      </section>

    </div>
  );
};
