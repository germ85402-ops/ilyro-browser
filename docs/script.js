(() => {
  const root = document.documentElement;
  const header = document.querySelector('.site-header');
  const themeToggle = document.querySelector('#theme-toggle');
  const menuToggle = document.querySelector('#menu-toggle');
  const mainNav = document.querySelector('#main-nav');
  const year = document.querySelector('#year');

  const safeStorage = {
    get(key) {
      try { return window.localStorage.getItem(key); } catch { return null; }
    },
    set(key, value) {
      try { window.localStorage.setItem(key, value); } catch { /* storage may be blocked */ }
    }
  };

  const savedTheme = safeStorage.get('ilyro-theme');
  if (savedTheme === 'light') root.dataset.theme = 'light';

  const updateThemeLabel = () => {
    const isLight = root.dataset.theme === 'light';
    themeToggle?.setAttribute('aria-label', isLight ? 'Включить тёмную тему' : 'Включить светлую тему');
    themeToggle?.setAttribute('title', isLight ? 'Тёмная тема' : 'Светлая тема');
  };

  updateThemeLabel();

  themeToggle?.addEventListener('click', () => {
    const isLight = root.dataset.theme === 'light';
    if (isLight) {
      delete root.dataset.theme;
      safeStorage.set('ilyro-theme', 'dark');
    } else {
      root.dataset.theme = 'light';
      safeStorage.set('ilyro-theme', 'light');
    }
    updateThemeLabel();
  });

  const updateHeader = () => {
    header?.classList.toggle('is-scrolled', window.scrollY > 10);
  };

  updateHeader();
  window.addEventListener('scroll', updateHeader, { passive: true });

  menuToggle?.addEventListener('click', () => {
    const isOpen = menuToggle.getAttribute('aria-expanded') === 'true';
    menuToggle.setAttribute('aria-expanded', String(!isOpen));
    mainNav?.classList.toggle('is-open', !isOpen);
  });

  mainNav?.querySelectorAll('a').forEach((link) => {
    link.addEventListener('click', () => {
      menuToggle?.setAttribute('aria-expanded', 'false');
      mainNav.classList.remove('is-open');
    });
  });

  const revealItems = document.querySelectorAll('.reveal');
  if ('IntersectionObserver' in window) {
    const revealObserver = new IntersectionObserver((entries, observer) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('is-visible');
        observer.unobserve(entry.target);
      });
    }, { threshold: 0.08, rootMargin: '0px 0px -30px' });
    revealItems.forEach((item) => revealObserver.observe(item));
  } else {
    revealItems.forEach((item) => item.classList.add('is-visible'));
  }

  const mockupStates = {
    start: {
      kicker: 'A CALMER WAY TO BROWSE',
      title: 'Всё нужное.<br /><em>Ничего лишнего.</em>',
      copy: 'Твои вкладки, загрузки и настройки — собраны вокруг реального использования.',
      address: 'ilyro.dev'
    },
    video: {
      kicker: 'MEDIA / READY WHEN YOU ARE',
      title: 'Смотри<br /><em>без пауз.</em>',
      copy: 'Полный экран, Picture-in-Picture и удобное возвращение к странице.',
      address: 'watch.example/video'
    }
  };

  const switchMockup = (tabName) => {
    const state = mockupStates[tabName] || mockupStates.start;
    const kicker = document.querySelector('#mockup-kicker');
    const title = document.querySelector('#mockup-title');
    const copy = document.querySelector('#mockup-copy');
    const address = document.querySelector('#address-text');
    if (kicker) kicker.textContent = state.kicker;
    if (title) title.innerHTML = state.title;
    if (copy) copy.textContent = state.copy;
    if (address) address.textContent = state.address;
  };

  document.querySelectorAll('.browser-tab').forEach((tab) => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.browser-tab').forEach((item) => {
        const isActive = item === tab;
        item.classList.toggle('is-active', isActive);
        item.setAttribute('aria-selected', String(isActive));
      });
      switchMockup(tab.dataset.tab);
    });
  });

  document.querySelector('.browser-tab-add')?.addEventListener('click', () => {
    const firstTab = document.querySelector('.browser-tab');
    firstTab?.click();
  });

  if (year) year.textContent = String(new Date().getFullYear());
})();
