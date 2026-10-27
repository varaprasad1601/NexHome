/** Whether the URL asks for the launch countdown: /launch, #launch or ?launch (shown over the home page). */
export function isLaunchLocation({ pathname, hash, search }) {
  return (
    pathname.toLowerCase().includes('launch') ||
    hash.toLowerCase().includes('launch') ||
    new URLSearchParams(search).has('launch')
  );
}

/** Which navigation item to highlight for a path; '' for pages without one (policies, launch). */
export function getActiveNavKey(pathname, isLaunch) {
  const path = pathname.toLowerCase();
  if (isLaunch) return '';
  if (path.includes('about')) return 'about';
  if (path.includes('services')) return 'services';
  if (path.includes('how-it-works')) return 'how';
  if (path.includes('join')) return 'join';
  if (path.includes('team')) return 'team';
  if (path.includes('faq')) return 'faqs';
  if (path.includes('contact')) return 'contact';
  if (path.includes('policies')) return '';
  // Any other path shows the home page
  return 'home';
}
