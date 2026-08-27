/**
 * Tamil text-to-speech.
 *
 * Most browsers/devices do NOT ship a Tamil voice. Speaking Tamil text with
 * an English voice reads it with English phonetics — actively harmful for a
 * child learning to read. So `speakTamil` refuses to speak without a Tamil
 * voice and returns false; UI should check `useTamilVoice()` (hooks/) and
 * hide or annotate audio controls accordingly.
 */

let tamilVoice: SpeechSynthesisVoice | null = null;
let voicesResolved = false;
const listeners = new Set<(available: boolean) => void>();

function scanVoices(): void {
  if (!('speechSynthesis' in window)) {
    voicesResolved = true;
    return;
  }
  const voices = window.speechSynthesis.getVoices();
  if (voices.length === 0) return; // not loaded yet — wait for voiceschanged
  tamilVoice = voices.find(v => v.lang.toLowerCase().startsWith('ta')) || null;
  voicesResolved = true;
  listeners.forEach(fn => fn(tamilVoice !== null));
}

if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
  scanVoices();
  window.speechSynthesis.addEventListener?.('voiceschanged', scanVoices);
}

/** Current best knowledge; may flip to true once voices finish loading. */
export function isTamilVoiceAvailable(): boolean {
  if (!voicesResolved) scanVoices();
  return tamilVoice !== null;
}

/** Subscribe to availability changes (voices load asynchronously). */
export function onTamilVoiceChange(fn: (available: boolean) => void): () => void {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

/** Speaks with a real Tamil voice, or not at all. Returns whether it spoke. */
export const speakTamil = (text: string): boolean => {
  if (!('speechSynthesis' in window)) return false;
  if (!isTamilVoiceAvailable() || !tamilVoice) {
    console.warn('[TTS] No Tamil voice installed — skipping speech.');
    return false;
  }

  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = 'ta-IN';
  utterance.voice = tamilVoice;
  utterance.rate = 0.85; // slightly slower for pediatric accessibility
  window.speechSynthesis.speak(utterance);
  return true;
};
