import { useEffect, useState } from 'react';
import { isTamilVoiceAvailable, onTamilVoiceChange } from '../services/speechService';

/**
 * True when the browser has a Tamil TTS voice. Pages with audio controls
 * should hide the controls (or show an explanatory note) when this is false —
 * speakTamil() refuses to speak through a non-Tamil voice.
 */
export function useTamilVoice(): boolean {
  const [available, setAvailable] = useState(isTamilVoiceAvailable());
  useEffect(() => onTamilVoiceChange(setAvailable), []);
  return available;
}
