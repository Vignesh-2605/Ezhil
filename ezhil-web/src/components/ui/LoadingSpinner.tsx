import React from 'react';

export const LoadingSpinner: React.FC<{ size?: number }> = ({ size = 32 }) => (
  <div
    style={{ width: size, height: size }}
    className="border-4 border-white/10 border-t-primary-fixed rounded-full animate-spin"
  />
);

export const PageLoading: React.FC = () => (
  <div className="flex-1 flex items-center justify-center min-h-[40vh]">
    <LoadingSpinner size={48} />
  </div>
);
