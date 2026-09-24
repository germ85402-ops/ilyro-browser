(() => {
  const root = document.documentElement;
  const header = document.querySelector('.site-header');
  const themeToggle = document.querySelector('#theme-toggle');
  const menuToggle = document.querySelector('#menu-toggle');
  const mainNav = document.querySelector('#main-nav');
  const themeColorMeta = document.querySelector('meta[name="theme-color"]');
  const year = document.querySelector('#year');

  const safeStorage = {
    get(key) {
      try { return window.localStorage.getItem(key); } catch { return null; }
    },
    set(key, value) {
      try { window.localStorage.setItem(key, value); } catch { /* storage may be blocked */ }
    }
  };

  const russianTranslations = {
    'meta.title': 'ILYRO — браузер в твоём ритме',
    'meta.description': 'ILYRO — браузер для Android с GeckoView, встроенной защитой uBlock Origin, гибкой настройкой и синхронизацией.',
    'meta.ogTitle': 'ILYRO — браузер в твоём ритме',
    'meta.ogDescription': 'Быстрый, безопасный и настраиваемый Android-браузер.',
    'brand.home': 'ILYRO, на главную',
    'brand.top': 'ILYRO, наверх',
    'nav.label': 'Основная навигация',
    'nav.features': 'Возможности',
    'nav.screenshots': 'Скриншоты',
    'nav.download': 'Скачать',
    'language.label': 'Язык',
    'theme.system': 'Тема следует системе устройства',
    'menu.open': 'Открыть меню',
    'menu.close': 'Закрыть меню',
    'actions.downloadApk': 'Скачать APK',
    'actions.openGithub': 'Открыть GitHub',
    'hero.overline': 'БЫСТРЫЙ. БЕЗОПАСНЫЙ. ТВОЙ.',
    'hero.title': 'ILYRO — браузер<br />для Android<br /><span>в твоём ритме</span>',
    'hero.lead': 'GeckoView, встроенная защита uBlock Origin,<br />удобные вкладки, быстрые загрузки<br />и гибкая персонализация.',
    'hero.tagsLabel': 'Ключевые характеристики',
    'hero.syncTag': 'Синхронизация',
    'hero.artLabel': 'Главный экран браузера ILYRO',
    'hero.artAlt': 'Главный экран браузера ILYRO с обоями Obsidian Arch',
    'hero.note': 'БРАУЗЕР<br /><span>БЕЗ ГРАНИЦ</span>',
    'benefits.label': 'Ключевые возможности',
    'benefits.shield.title': 'Защита',
    'benefits.shield.copy': 'Встроенный uBlock Origin<br />блокирует рекламу и трекеры',
    'benefits.menu.title': 'Быстрое меню',
    'benefits.menu.copy': 'Удобный доступ ко всем<br />возможностям',
    'benefits.custom.title': 'Персонализация',
    'benefits.custom.copy': 'Темы, иконки, цвета.<br />Настраивай ILYRO под себя',
    'benefits.sync.title': 'Синхронизация',
    'benefits.sync.copy': 'Твои данные на всех<br />устройствах через Google',
    'features.kicker': 'БОЛЬШЕ ВОЗМОЖНОСТЕЙ',
    'features.title': 'Почему ILYRO',
    'features.intro': 'Современный браузер, созданный для свободы.<br />Сочетает производительность, приватность и гибкость настроек.',
    'features.shield.alt': 'Панель ILYRO Shield',
    'features.shield.title': 'Надёжная защита',
    'features.shield.copy': 'Встроенный uBlock Origin. Чистый интернет без рекламы и трекеров.',
    'features.menu.alt': 'Быстрое меню ILYRO',
    'features.menu.title': 'Удобное управление',
    'features.menu.copy': 'Быстрое меню с нужными действиями. Всё под рукой.',
    'features.custom.alt': 'Настройки внешнего вида ILYRO',
    'features.custom.title': 'Гибкая персонализация',
    'features.custom.copy': 'Темы, иконки, цвета и многое другое. Твой стиль — твои правила.',
    'features.sync.alt': 'Аккаунт и синхронизация ILYRO',
    'features.sync.title': 'Всегда с вами',
    'features.sync.copy': 'Синхронизация через Google: закладки, настройки и история на всех устройствах.',
    'screenshots.title': 'Скриншоты приложения',
    'screenshots.intro': 'Посмотрите интерфейс ILYRO — скриншоты переведены на английский язык.',
    'screenshots.link': 'Смотреть все скриншоты',
    'screenshots.newTab.alt': 'Новая вкладка ILYRO',
    'screenshots.newTab.title': 'Новая вкладка',
    'screenshots.newTab.copy': 'Стильный и функциональный старт',
    'screenshots.youtube.alt': 'YouTube в браузере ILYRO',
    'screenshots.youtube.title': 'YouTube',
    'screenshots.youtube.copy': 'Комфортный просмотр видео',
    'screenshots.tabs.alt': 'Вкладки ILYRO',
    'screenshots.tabs.title': 'Вкладки',
    'screenshots.tabs.copy': 'Удобная организация',
    'screenshots.settings.alt': 'Настройки ILYRO',
    'screenshots.settings.title': 'Настройки',
    'screenshots.settings.copy': 'Языки и поиск',
    'screenshots.appearance.alt': 'Оформление ILYRO',
    'screenshots.appearance.title': 'Оформление',
    'screenshots.appearance.copy': 'Темы и стиль',
    'screenshots.account.alt': 'Аккаунт ILYRO',
    'screenshots.account.title': 'Аккаунт',
    'screenshots.account.copy': 'Синхронизация данных',
    'download.title': 'Скачать ILYRO',
    'download.lead': 'Начни пользоваться уже сегодня.',
    'download.compat': 'ARM64 · Android 8+',
    'download.latest': 'Последняя сборка',
    'download.prerelease': 'Тестовый релиз · Release Candidate',
    'download.stable': 'Последний стабильный релиз',
    'download.releases': 'Релизы',
    'download.sha': 'SHA-256',
    'download.note': 'Твой мир.<br /><span>Больше свободы.</span>',
    'footer.tagline': 'Открывай мир по-своему.',
    'footer.privacy': 'Политика конфиденциальности',
    'footer.terms': 'Условия использования',
    'legal.project': 'Открыть проект',
    'legal.changelog': 'История изменений',
    'legal.builtFor': 'Создан для Android.',
    'legal.pages': 'Юридическая информация',
    'legal.privacyTitle': 'ILYRO — Политика конфиденциальности',
    'legal.privacyDescription': 'Политика конфиденциальности ILYRO Browser: как Android-браузер обрабатывает локальные данные, вход через Google, синхронизацию с Google Drive, разрешения и загрузки.',
    'legal.privacyHeading': 'Политика<br /><span>конфиденциальности.</span>',
    'legal.privacyLead': 'Как ILYRO хранит данные на устройстве, что может передаваться в сеть и как работают дополнительные функции аккаунта Google.',
    'legal.termsTitle': 'ILYRO — Условия использования',
    'legal.termsDescription': 'Условия использования ILYRO Browser: приложение для Android, сайт, сторонние сервисы, компоненты с открытым исходным кодом и добровольная поддержка.',
    'legal.termsHeading': 'Условия<br /><span>использования.</span>',
    'legal.termsLead': 'Правила и ответственность при использовании ILYRO Browser, сайта и общедоступных материалов проекта.',
    'footer.social': 'Ссылки проекта',
    'footer.rights': 'Все права защищены.'
  };

  const localizedPage = Boolean(document.querySelector('[data-language]'));
  const languageButtons = [...document.querySelectorAll('[data-language]')];
  const localizedElements = [...document.querySelectorAll('[data-i18n]')];
  const localizedAttributes = {
    content: [...document.querySelectorAll('[data-i18n-content]')],
    aria: [...document.querySelectorAll('[data-i18n-aria]')],
    alt: [...document.querySelectorAll('[data-i18n-alt]')]
  };

  localizedElements.forEach((element) => {
    element.dataset.i18nDefault = element.dataset.i18nMode === 'html' ? element.innerHTML : element.textContent;
  });
  Object.entries(localizedAttributes).forEach(([attribute, elements]) => {
    elements.forEach((element) => {
      const sourceAttribute = attribute === 'content' ? 'content' : attribute === 'aria' ? 'aria-label' : 'alt';
      element.dataset[`i18nDefault${attribute[0].toUpperCase()}${attribute.slice(1)}`] = element.getAttribute(sourceAttribute) || '';
    });
  });

  const applyLanguage = (language, persist = true) => {
    const nextLanguage = language === 'ru' ? 'ru' : 'en';
    if (localizedPage) root.lang = nextLanguage;
    root.dataset.language = nextLanguage;

    localizedElements.forEach((element) => {
      const value = nextLanguage === 'ru' ? russianTranslations[element.dataset.i18n] : element.dataset.i18nDefault;
      if (value == null) return;
      if (element.dataset.i18nMode === 'html') element.innerHTML = value;
      else element.textContent = value;
    });

    Object.entries(localizedAttributes).forEach(([attribute, elements]) => {
      elements.forEach((element) => {
        const key = element.dataset[`i18n${attribute[0].toUpperCase()}${attribute.slice(1)}`];
        const defaultKey = `i18nDefault${attribute[0].toUpperCase()}${attribute.slice(1)}`;
        const value = nextLanguage === 'ru' ? russianTranslations[key] : element.dataset[defaultKey];
        if (value == null) return;
        const targetAttribute = attribute === 'content' ? 'content' : attribute === 'aria' ? 'aria-label' : 'alt';
        element.setAttribute(targetAttribute, value);
      });
    });

    languageButtons.forEach((button) => {
      const selected = button.dataset.language === nextLanguage;
      button.setAttribute('aria-pressed', String(selected));
    });
    document.querySelectorAll('[data-legal-language]').forEach((article) => {
      article.hidden = article.dataset.legalLanguage !== nextLanguage;
    });
    document.querySelectorAll('[data-latest-release-status]').forEach((status) => {
      const isPrerelease = status.dataset.isPrerelease !== 'false';
      const copy = isPrerelease
        ? (nextLanguage === 'ru' ? russianTranslations['download.prerelease'] : 'Release candidate · Pre-release')
        : (nextLanguage === 'ru' ? russianTranslations['download.stable'] : 'Latest stable release');
      status.textContent = copy;
    });
    if (persist && localizedPage) safeStorage.set('ilyro-language', nextLanguage);
    updateThemeLabel();
    updateMenuLabel();
  };

  const savedThemeMode = safeStorage.get('ilyro-theme-mode');
  let themeMode = ['system', 'light', 'dark'].includes(savedThemeMode) ? savedThemeMode : 'system';
  const systemThemeQuery = window.matchMedia('(prefers-color-scheme: light)');

  const updateThemeColor = () => {
    const isLight = themeMode === 'light' || (themeMode === 'system' && systemThemeQuery.matches);
    themeColorMeta?.setAttribute('content', isLight ? '#f5f8fd' : '#071326');
  };

  const updateThemeLabel = () => {
    const language = root.dataset.language === 'ru' ? 'ru' : 'en';
    const labels = {
      en: { system: 'Theme follows your device', light: 'Theme: light (click to change)', dark: 'Theme: dark (click to change)' },
      ru: { system: 'Тема следует системе устройства', light: 'Тема: светлая (нажмите, чтобы изменить)', dark: 'Тема: тёмная (нажмите, чтобы изменить)' }
    };
    const label = labels[language][themeMode];
    themeToggle?.setAttribute('aria-label', label);
    themeToggle?.setAttribute('title', label);
    const modeLabels = {
      en: { system: 'System', light: 'Light', dark: 'Dark' },
      ru: { system: 'Система', light: 'Светлая', dark: 'Тёмная' }
    };
    const modeLabel = document.querySelector('[data-theme-mode-label]');
    if (modeLabel) modeLabel.textContent = modeLabels[language][themeMode];
  };

  const updateMenuLabel = () => {
    const language = root.dataset.language === 'ru' ? 'ru' : 'en';
    const isOpen = menuToggle?.getAttribute('aria-expanded') === 'true';
    menuToggle?.setAttribute('aria-label', isOpen
      ? (language === 'ru' ? 'Закрыть меню' : 'Close menu')
      : (language === 'ru' ? 'Открыть меню' : 'Open menu'));
  };

  const applyTheme = () => {
    if (themeMode === 'system') delete root.dataset.theme;
    else root.dataset.theme = themeMode;
    updateThemeColor();
    updateThemeLabel();
  };

  const initialLanguage = safeStorage.get('ilyro-language') === 'ru' ? 'ru' : 'en';
  applyLanguage(initialLanguage, false);
  applyTheme();

  languageButtons.forEach((button) => {
    button.addEventListener('click', () => applyLanguage(button.dataset.language));
  });

  themeToggle?.addEventListener('click', () => {
    themeMode = themeMode === 'system' ? 'light' : themeMode === 'light' ? 'dark' : 'system';
    safeStorage.set('ilyro-theme-mode', themeMode);
    applyTheme();
  });

  systemThemeQuery.addEventListener?.('change', () => {
    if (themeMode === 'system') updateThemeColor();
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
    updateMenuLabel();
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

  const motionPreference = window.matchMedia?.('(prefers-reduced-motion: reduce)');
  const motionItems = document.querySelectorAll('[data-motion-reveal]');
  if (motionItems.length && 'IntersectionObserver' in window && !motionPreference?.matches) {
    motionItems.forEach((item) => {
      const siblings = [...item.parentElement.children].filter((sibling) => sibling.hasAttribute('data-motion-reveal'));
      const stagger = Math.min(siblings.indexOf(item), 4) * 70;
      item.style.setProperty('--motion-delay', `${stagger}ms`);
    });

    const motionObserver = new IntersectionObserver((entries, observer) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('is-motion-visible');
        observer.unobserve(entry.target);
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -36px' });
    root.classList.add('motion-ready');
    motionItems.forEach((item) => motionObserver.observe(item));

    motionPreference?.addEventListener?.('change', (event) => {
      if (!event.matches) return;
      root.classList.remove('motion-ready');
      motionObserver.disconnect();
    });
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

  const updateLatestApkLinks = async () => {
    const links = [...document.querySelectorAll('[data-latest-apk-link]')];
    const checksumLinks = [...document.querySelectorAll('[data-latest-apk-checksum-link]')];
    const versionLabels = [...document.querySelectorAll('[data-latest-release-version]')];
    const releaseStatuses = [...document.querySelectorAll('[data-latest-release-status]')];
    if (!links.length) return;

    const releasesUrl = 'https://github.com/germ85402-ops/ilyro-browser/releases';
    const apiUrl = 'https://api.github.com/repos/germ85402-ops/ilyro-browser/releases?per_page=20';

    try {
      const response = await fetch(apiUrl, {
        headers: { Accept: 'application/vnd.github+json' },
        cache: 'no-store'
      });
      if (!response.ok) throw new Error('GitHub Releases request failed');

      const releases = await response.json();
      const latest = releases.find((release) =>
        !release.draft &&
        Array.isArray(release.assets) &&
        release.assets.some((asset) => /^ILYRO-.*-arm64\.apk$/.test(asset.name))
      );
      const apk = latest?.assets?.find((asset) => /^ILYRO-.*-arm64\.apk$/.test(asset.name));
      const checksum = apk && latest.assets.find((asset) => asset.name === `${apk.name}.sha256`);

      if (!apk?.browser_download_url) throw new Error('No ARM64 APK found');
      links.forEach((link) => {
        link.href = apk.browser_download_url;
        link.dataset.releaseTag = latest.tag_name || '';
      });
      checksumLinks.forEach((link) => {
        link.href = checksum?.browser_download_url || releasesUrl;
        link.dataset.releaseTag = latest.tag_name || '';
      });
      versionLabels.forEach((label) => { label.textContent = latest.tag_name || apk.name; });
      releaseStatuses.forEach((status) => { status.dataset.isPrerelease = String(Boolean(latest.prerelease)); });
      applyLanguage(root.dataset.language || 'en', false);
    } catch {
      // Keep a useful fallback if the API is rate-limited or temporarily unavailable.
      links.forEach((link) => { link.href = releasesUrl; });
      checksumLinks.forEach((link) => { link.href = releasesUrl; });
    }
  };

  updateLatestApkLinks();
})();
