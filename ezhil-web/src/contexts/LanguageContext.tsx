import React, { createContext, useContext, useState, ReactNode } from 'react';
import { LOCALES, LocaleKey } from '../services/locales';

type Language = 'ta' | 'en';

interface LanguageContextType {
  lang: Language;
  toggle: () => void;
  t: (ta: string, en: string) => string;
  tKey: (key: LocaleKey) => string;
}

const LanguageContext = createContext<LanguageContextType | null>(null);

export const LanguageProvider = ({ children }: { children: ReactNode }) => {
  const [lang, setLang] = useState<Language>(
    () => (localStorage.getItem('ezhil_lang') as Language) || 'ta'
  );

  const toggle = () =>
    setLang(l => {
      const next = l === 'ta' ? 'en' : 'ta';
      localStorage.setItem('ezhil_lang', next);
      return next;
    });

  const t = (ta: string, en: string) => (lang === 'ta' ? ta : en);
  const tKey = (key: LocaleKey) => LOCALES[lang][key] || key;

  return (
    <LanguageContext.Provider value={{ lang, toggle, t, tKey }}>
      {children}
    </LanguageContext.Provider>
  );
};

export const useLanguage = () => {
  const ctx = useContext(LanguageContext);
  if (!ctx) throw new Error('useLanguage must be inside LanguageProvider');
  return ctx;
};
