import React from 'react';

type SyncStatus = 'synced' | 'pending' | 'offline' | 'error';

interface SyncStatusBarProps {
  status: SyncStatus;
  lastSyncedTime?: string;
}

const STATUS_CONFIG: Record<SyncStatus, { icon: string; text: (t?: string) => string; textClass: string; bg: string }> = {
  synced:  { icon: 'cloud_done', text: () => 'அனைத்தும் சேமிக்கப்பட்டது | All changes synced',          textClass: 'text-success',   bg: 'bg-bg-surface'         },
  pending: { icon: 'cloud_sync', text: () => 'சேமிக்கப்படுகிறது... | Syncing...',                         textClass: 'text-secondary', bg: 'bg-bg-surface'         },
  offline: { icon: 'cloud_off',  text: (t) => `Offline — Last synced: ${t || 'Never'}`,                   textClass: 'text-text-muted',bg: 'bg-bg-surface'         },
  error:   { icon: 'error',      text: () => 'சேமிப்பதில் பிழை | Sync Error. Retrying...',               textClass: 'text-error',     bg: 'bg-error/10 border-t border-error/20' },
};

export const SyncStatusBar: React.FC<SyncStatusBarProps> = ({ status, lastSyncedTime }) => {
  const cfg = STATUS_CONFIG[status];
  return (
    <div className={`fixed bottom-20 left-0 w-full h-9 flex items-center justify-center gap-2 z-30 pointer-events-none ${cfg.bg}`}>
      <span className={`material-symbols-outlined text-base ${cfg.textClass}`} style={{ fontVariationSettings: "'FILL' 1" }}>
        {cfg.icon}
      </span>
      <span className="font-mono-metadata text-xs text-text-muted">{cfg.text(lastSyncedTime)}</span>
    </div>
  );
};
