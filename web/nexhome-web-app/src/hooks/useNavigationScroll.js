import { useEffect } from 'react';
import { useLocation, useNavigationType } from 'react-router-dom';

export function scrollToTop() {
  window.scrollTo(0, 0);
  document.documentElement.scrollTop = 0;
  document.body.scrollTop = 0;
}

/**
 * Scrolling on navigation: every in-app navigation starts at the top of the page; opening a URL (or going back or
 * forward) with a #section scrolls to that section.
 */
export default function useNavigationScroll() {
  const { key, hash } = useLocation();
  const navigationType = useNavigationType();

  useEffect(() => {
    if (navigationType !== 'POP') {
      scrollToTop();
      return undefined;
    }
    if (!hash || hash === '#main-content' || hash.toLowerCase().includes('launch')) {
      return undefined;
    }
    const timer = setTimeout(() => {
      try {
        const el = document.querySelector(hash);
        if (el) el.scrollIntoView({ behavior: 'smooth' });
      } catch {
        // ignore invalid selector
      }
    }, 100);
    return () => clearTimeout(timer);
  }, [key, hash, navigationType]);
}
