import React, { useState } from 'react';

interface IdDisplayProps {
  id: string;
  maxLength?: number;
  className?: string;
}

export const IdDisplay: React.FC<IdDisplayProps> = ({ id, maxLength = 8, className = '' }) => {
  const [copied, setCopied] = useState(false);

  if (!id) return null;

  const displayId = id.length > maxLength * 2 + 3 
    ? `${id.slice(0, maxLength)}...${id.slice(-maxLength)}`
    : id;

  const handleCopy = async (e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await navigator.clipboard.writeText(id);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy ID: ', err);
    }
  };

  return (
    <div className={`inline-flex items-center gap-1.5 font-mono text-xs text-text-muted bg-bg-hover px-1.5 py-0.5 rounded border border-border/50 group relative max-w-full min-w-0 ${className}`}>
      <span 
        className="cursor-help overflow-hidden text-ellipsis whitespace-nowrap min-w-0" 
        title={id}
      >
        {displayId}
      </span>
      <button
        onClick={handleCopy}
        className="p-0.5 rounded hover:bg-border/50 text-text-muted hover:text-text active:scale-95 transition-all cursor-pointer flex items-center justify-center"
        title={copied ? "Copied!" : "Copy Full ID"}
      >
        <span className="material-symbols-outlined text-sm">
          {copied ? 'check' : 'content_copy'}
        </span>
      </button>
    </div>
  );
};
