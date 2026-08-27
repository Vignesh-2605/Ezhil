import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { LiveWaveform } from '../../components/ui/LiveWaveform';
import { Ezhilan } from '../../components/mascot/Ezhilan';

/**
 * Opus in WebM where it exists, MP4 for Safari, otherwise whatever the browser
 * picks for itself. `isTypeSupported` is absent on a few older engines, which
 * is itself a signal to stop negotiating and let the browser decide.
 */
const RECORDER_MIME_TYPES = [
  'audio/webm;codecs=opus',
  'audio/webm',
  'audio/mp4',
  'audio/ogg;codecs=opus',
];

function pickRecorderOptions(): MediaRecorderOptions | undefined {
  if (typeof MediaRecorder === 'undefined' || typeof MediaRecorder.isTypeSupported !== 'function') {
    return undefined;
  }
  const mimeType = RECORDER_MIME_TYPES.find(t => MediaRecorder.isTypeSupported(t));
  return mimeType ? { mimeType } : undefined;
}

/**
 * Build and start a recorder, falling back to the browser's own default.
 *
 * isTypeSupported() is necessary but not sufficient: headless Chrome reports
 * audio/webm;codecs=opus as supported and then throws NotSupportedError from
 * start(), because the encoder is not actually there. Anything that answers a
 * capability question optimistically has to be checked by trying it, so the
 * preferred type is attempted and a failure falls through to no options at
 * all -- which is the configuration every engine can honour.
 */
function startRecorder(stream: MediaStream, onChunk: (e: BlobEvent) => void): MediaRecorder {
  const attempt = (options?: MediaRecorderOptions) => {
    const rec = new MediaRecorder(stream, options);
    rec.ondataavailable = onChunk;
    rec.start(1000);   // chunk every second
    return rec;
  };
  const preferred = pickRecorderOptions();
  if (preferred) {
    try {
      return attempt(preferred);
    } catch {
      // Fall through to the default below.
    }
  }
  return attempt();
}

export const AssessmentRecording: React.FC = () => {
  const navigate = useNavigate();
  const [seconds, setSeconds] = useState(0);
  const [paused, setPaused] = useState(false);
  const [stream, setStream] = useState<MediaStream | null>(null);
  const [frames, setFrames] = useState<string[]>([]);
  
  const videoRef = useRef<HTMLVideoElement>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const frameIntervalRef = useRef<any>(null);

  // Timer loop with a strict 3-minute (180s) limit
  useEffect(() => {
    if (paused) return;
    const interval = setInterval(() => {
      setSeconds(s => {
        if (s >= 180) {
          handleFinish();
          return s;
        }
        return s + 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [paused, navigate]);

  // Request permissions and start recording/framing on mount
  useEffect(() => {
    let activeStream: MediaStream | null = null;

    async function startMedia() {
      try {
        const userStream = await navigator.mediaDevices.getUserMedia({
          audio: true,
          video: { facingMode: 'user', width: 224, height: 224 }
        });
        
        activeStream = userStream;
        setStream(userStream);
        
        if (videoRef.current) {
          videoRef.current.srcObject = userStream;
        }

        // Initialize audio recorder.
        //
        // The type has to be negotiated, not assumed. This asked for
        // audio/webm unconditionally, which Safari does not support for
        // MediaRecorder — so start() threw NotSupportedError and screening was
        // dead on every iPhone and iPad. Falling through to the browser's own
        // default is better than failing: the backend reads the container from
        // the blob, and a recording in the wrong format still beats none.
        const recorder = startRecorder(userStream, e => {
          if (e.data.size > 0) chunksRef.current.push(e.data);
        });
        chunksRef.current = [];
        mediaRecorderRef.current = recorder;

        // Frame collector loop at 1 frame per second
        const canvas = document.createElement('canvas');
        canvas.width = 224;
        canvas.height = 224;
        const ctx = canvas.getContext('2d');

        frameIntervalRef.current = setInterval(() => {
          if (videoRef.current && ctx) {
            ctx.drawImage(videoRef.current, 0, 0, 224, 224);
            const frameData = canvas.toDataURL('image/jpeg', 0.6);
            setFrames(prev => [...prev, frameData]);
          }
        }, 1000);

      } catch (err) {
        console.error('Failed to get media devices for screening:', err);
      }
    }

    if (!paused) {
      startMedia();
    } else {
      stopMedia();
    }

    return () => {
      stopMedia();
    };

    function stopMedia() {
      if (activeStream) {
        activeStream.getTracks().forEach(track => track.stop());
      }
      if (frameIntervalRef.current) {
        clearInterval(frameIntervalRef.current);
      }
      if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
        mediaRecorderRef.current.stop();
      }
    }
  }, [paused]);

  const handleFinish = () => {
    const recorder = mediaRecorderRef.current;
    if (recorder && recorder.state !== 'inactive') {
      recorder.onstop = () => {
        // Label the blob with what was actually recorded. Hardcoding webm here
        // mislabels a Safari mp4 recording, and the backend reads this type.
        const audioBlob = new Blob(chunksRef.current, { type: recorder.mimeType || 'audio/webm' });
        navigate('/student/assessment/processing', {
          state: {
            audioBlob,
            framesCount: frames.length,
            durationMs: seconds * 1000
          }
        });
      };
      recorder.stop();
    } else {
      navigate('/student/assessment/processing', {
        state: {
          framesCount: frames.length,
          durationMs: seconds * 1000
        }
      });
    }
  };

  const fmt = (s: number) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;

  return (
    <div className="min-h-dvh bg-bg-deep flex flex-col items-center justify-center font-body-tamil px-6 gap-8">
      <div className="text-center space-y-2">
        <h1 className="font-display-tamil text-3xl font-bold text-white">படிக்கிறீர்கள்...</h1>
        <p className="text-on-surface-variant">Recording in progress</p>
      </div>

      {/* Front camera circular preview */}
      <div className="relative w-36 h-36 rounded-full overflow-hidden border-2 border-primary-fixed/40 bg-bg-surface flex items-center justify-center shadow-lg">
        <video ref={videoRef} autoPlay playsInline muted className="w-full h-full object-cover scale-x-[-1]" />
        <div className="absolute top-2 right-2 w-3 h-3 rounded-full bg-error animate-pulse" />
      </div>

      {/* Ezhilan leans in and listens while the child reads */}
      <div className="flex items-end gap-4">
        <Ezhilan mode={paused ? 'idle' : 'listening'} size={84} />
        <div className="flex flex-col items-center gap-1">
          <LiveWaveform stream={paused ? null : stream} />
          <p className="text-text-muted text-xs">உங்கள் குரல் / Your voice</p>
        </div>
      </div>

      {/* Timer */}
      <div className="text-center">
        <span className="font-mono-metadata text-4xl font-bold text-primary-fixed">{fmt(seconds)}</span>
        <p className="text-text-muted text-xs mt-1 font-mono-metadata">/ 3:00</p>
      </div>

      {/* Passage snippet */}
      <div className="glass-panel r-card surface-lit p-5 w-full max-w-sm">
        <p className="font-reader-tamil text-on-surface text-base leading-relaxed text-center">
          ஒரு காட்டில் ஒரு பெரிய யானை இருந்தது...
        </p>
      </div>

      <div className="w-full max-w-sm space-y-3">
        <button onClick={handleFinish}
          className="w-full h-14 bg-primary-fixed text-bg-deep font-bold text-lg r-chip active:scale-95 transition-all flex items-center justify-center gap-2">
          <span className="material-symbols-outlined">stop_circle</span>
          முடி / Finish
        </button>
        <div className="flex gap-3">
          <button onClick={() => setPaused(p => !p)}
            className="flex-1 h-12 border border-white/15 text-on-surface-variant r-chip font-medium hover:bg-white/5 transition-colors flex items-center justify-center gap-2">
            <span className="material-symbols-outlined">{paused ? 'play_arrow' : 'pause'}</span>
            {paused ? 'Resume' : 'Pause'}
          </button>
          <button onClick={() => navigate('/student/assessment/timeout')}
            className="flex-1 h-12 border border-error/30 text-error r-chip font-medium hover:bg-error/10 transition-colors">
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
};
