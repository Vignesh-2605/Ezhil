import React from 'react';

const RISK_CONFIG: Record<string, { label: string; bg: string; text: string; dot: string }> = {
  high:       { label: 'High Risk',   bg: 'rgba(229,57,53,0.15)',   text: '#E53935', dot: '#E53935' },
  medium:     { label: 'Medium Risk', bg: 'rgba(251,140,0,0.15)',   text: '#FB8C00', dot: '#FB8C00' },
  low:        { label: 'Low Risk',    bg: 'rgba(67,160,71,0.15)',   text: '#43A047', dot: '#43A047' },
  unscreened: { label: 'Unscreened',  bg: 'rgba(255,255,255,0.08)', text: '#9A9A9A', dot: '#9A9A9A' },
};

export const RiskBadge: React.FC<{ level: string; compact?: boolean }> = ({ level, compact }) => {
  const cfg = RISK_CONFIG[level] ?? RISK_CONFIG.unscreened;
  return (
    <span
      style={{ background: cfg.bg, color: cfg.text }}
      className={`inline-flex items-center gap-1.5 rounded-full font-semibold ${compact ? 'text-xs px-2 py-0.5' : 'text-sm px-3 py-1'}`}
    >
      <span style={{ background: cfg.dot }} className="w-1.5 h-1.5 rounded-full flex-shrink-0" />
      {cfg.label}
    </span>
  );
};
