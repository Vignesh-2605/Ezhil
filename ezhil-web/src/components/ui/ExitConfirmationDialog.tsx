import React from 'react';

interface ExitConfirmationDialogProps {
  isOpen: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export const ExitConfirmationDialog: React.FC<ExitConfirmationDialogProps> = ({
  isOpen, onConfirm, onCancel,
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/75 backdrop-blur-sm p-4 font-body-tamil">
      <div className="bg-[#1F2833] w-full max-w-sm responsive-modal-min r-chip shadow-[0_8px_32px_rgba(0,0,0,0.65)] border border-white/10 overflow-hidden m-4 animate-slide-in">
        <div className="p-6 flex flex-col items-center text-center space-y-4">
          <div className="w-16 h-16 rounded-full bg-secondary/20 flex items-center justify-center mb-2">
            <span className="material-symbols-outlined text-secondary text-4xl" style={{ fontVariationSettings: "'FILL' 1" }}>warning</span>
          </div>
          <div className="space-y-1">
            <h2 className="font-display-tamil text-2xl font-bold text-on-surface">Exit Lesson?</h2>
            <p className="font-body-tamil text-lg text-primary-fixed">Lesson விட்டு வெளியேறணுமா?</p>
          </div>
          <p className="text-on-surface-variant font-bilingual-sub text-sm px-4">
            Your progress is saved, but you'll lose your active streak bonus for this session.
          </p>
          <div className="w-full pt-4 flex flex-row gap-3">
            <button
              onClick={onCancel}
              className="flex-1 h-12 bg-primary-fixed text-bg-deep font-bold r-chip flex items-center justify-center gap-1.5 hover:opacity-90 active:scale-95 transition-all text-xs sm:text-sm px-2 cursor-pointer"
            >
              <span className="material-symbols-outlined text-base">auto_stories</span>
              Keep Reading
            </button>
            <button
              onClick={onConfirm}
              className="flex-1 h-12 border border-white/10 hover:bg-white/5 text-on-surface font-bold r-chip flex items-center justify-center hover:text-on-surface active:scale-95 transition-all text-xs sm:text-sm cursor-pointer"
            >
              Exit
            </button>
          </div>
        </div>
        <div className="h-1.5 w-full bg-gradient-to-r from-transparent via-primary-fixed/30 to-transparent" />
      </div>
    </div>
  );
};
