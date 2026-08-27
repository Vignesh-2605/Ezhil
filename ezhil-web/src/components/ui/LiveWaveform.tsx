import React, { useEffect, useRef } from 'react';

/**
 * Waveform bars driven by the REAL microphone signal via an AnalyserNode —
 * a child sees the bars jump with their own voice. Falls back to a gentle
 * idle pulse when no stream is available yet.
 *
 * Bar heights are written directly to DOM styles inside requestAnimationFrame
 * (no React re-renders at 60fps).
 */
export const LiveWaveform: React.FC<{
  stream: MediaStream | null;
  bars?: number;
  className?: string;
}> = ({ stream, bars = 16, className }) => {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!stream) return;

    const ctx = new AudioContext();
    const source = ctx.createMediaStreamSource(stream);
    const analyser = ctx.createAnalyser();
    analyser.fftSize = 256;
    analyser.smoothingTimeConstant = 0.75;
    source.connect(analyser);

    const data = new Uint8Array(analyser.frequencyBinCount);
    let raf = 0;

    const draw = () => {
      analyser.getByteFrequencyData(data);
      const els = containerRef.current?.children;
      if (els) {
        // Voice energy lives in the lower bins — sample the first ~60%.
        const usable = Math.floor(data.length * 0.6);
        const step = usable / els.length;
        for (let i = 0; i < els.length; i++) {
          const v = data[Math.floor(i * step)] / 255; // 0..1
          const h = 6 + v * 42; // 6..48 px
          (els[i] as HTMLElement).style.height = `${h}px`;
          (els[i] as HTMLElement).style.opacity = `${0.45 + v * 0.55}`;
        }
      }
      raf = requestAnimationFrame(draw);
    };
    raf = requestAnimationFrame(draw);

    return () => {
      cancelAnimationFrame(raf);
      source.disconnect();
      ctx.close().catch(() => {});
    };
  }, [stream]);

  return (
    <div ref={containerRef} className={`flex items-center justify-center gap-1.5 h-12 ${className ?? ''}`}>
      {[...Array(bars)].map((_, i) => (
        <div key={i}
          className={`w-1 bg-secondary rounded-full transition-none ${stream ? '' : 'animate-wave-bar'}`}
          style={{ height: 8, ...(stream ? {} : { animationDelay: `${i * 0.05}s` }) }}
        />
      ))}
    </div>
  );
};
