(() => {
  const host = (location.hostname || '').toLowerCase();
  if (host === 'youtube.com' || host.endsWith('.youtube.com') || host === 'youtu.be' || host.endsWith('.youtu.be')) {
    return;
  }

  let port = null;
  let networkWatcherStarted = false;
  let performanceObserver = null;
  const seen = new Map();
  const MAX_SEEN = 256;
  const MEDIA_URL_RE = /\.(m3u8|mpd|mp4|webm|m4v|mov|ogv|mp3|m4a|aac|flac|wav|oga|opus)(?:$|[?#])/i;

  function connect() {
    if (port) return port;
    try {
      port = browser.runtime.connectNative('ilyro_media');
      port.onDisconnect.addListener(() => {
        port = null;
      });
    } catch (_) {
      port = null;
    }
    return port;
  }

  function normalizedUrl(value) {
    if (typeof value !== 'string' || !value) return '';
    try {
      const url = new URL(value, location.href);
      if (url.protocol !== 'http:' && url.protocol !== 'https:') return '';
      return url.href;
    } catch (_) {
      return '';
    }
  }

  function pathOf(url) {
    try {
      return new URL(url).pathname.toLowerCase();
    } catch (_) {
      return '';
    }
  }

  function classify(url, mime, hint) {
    const path = pathOf(url);
    const type = (mime || '').toLowerCase();
    const source = (hint || '').toLowerCase();

    if (type.includes('mpegurl') || path.endsWith('.m3u8')) return 'hls';
    if (type.includes('dash+xml') || path.endsWith('.mpd')) return 'dash';

    if (
      type.startsWith('video/') ||
      source === 'video' || source === 'source-video' || source === 'resource-video' ||
      /\.(mp4|webm|m4v|mov|ogv)$/i.test(path)
    ) return 'video';

    if (
      type.startsWith('audio/') ||
      source === 'audio' || source === 'source-audio' || source === 'resource-audio' ||
      /\.(mp3|m4a|aac|flac|wav|oga|opus)$/i.test(path)
    ) return 'audio';

    return '';
  }

  function remember(key, mime, source, width, height) {
    const signature = `${mime || ''}|${source || ''}|${width || 0}|${height || 0}`;
    if (seen.get(key) === signature) return false;
    seen.set(key, signature);
    if (seen.size > MAX_SEEN) {
      seen.delete(seen.keys().next().value);
    }
    return true;
  }

  function report(rawUrl, mime, source, width, height) {
    const url = normalizedUrl(rawUrl);
    if (!url) return;
    const kind = classify(url, mime, source);
    if (!kind) return;

    const key = `${kind}|${url}`;
    if (!remember(key, mime, source, width, height)) return;

    const targetPort = connect();
    if (!targetPort) return;

    try {
      targetPort.postMessage({
        type: 'media',
        url,
        kind,
        mime: typeof mime === 'string' ? mime : '',
        source: source || 'page',
        pageUrl: location.href,
        title: document.title || '',
        width: Number.isFinite(width) ? Math.max(0, Math.round(width)) : 0,
        height: Number.isFinite(height) ? Math.max(0, Math.round(height)) : 0
      });
    } catch (_) {
      port = null;
    }
  }

  function inspectPerformanceEntry(entry) {
    const name = typeof entry?.name === 'string' ? entry.name : '';
    if (!name) return;

    const initiator = (entry?.initiatorType || '').toLowerCase();
    const isMediaInitiator = initiator === 'video' || initiator === 'audio';

    if (!isMediaInitiator && !MEDIA_URL_RE.test(name)) return;

    let hint = 'network';
    if (initiator === 'video') hint = 'resource-video';
    if (initiator === 'audio') hint = 'resource-audio';
    report(name, '', hint, 0, 0);
  }

  function startNetworkWatcher() {
    if (networkWatcherStarted) return;
    networkWatcherStarted = true;

    try {
      const existing = performance.getEntriesByType('resource');
      existing.slice(Math.max(0, existing.length - 250)).forEach(inspectPerformanceEntry);

      performanceObserver = new PerformanceObserver(list => {
        list.getEntries().forEach(inspectPerformanceEntry);
      });
      performanceObserver.observe({ type: 'resource', buffered: true });
    } catch (_) {
      performanceObserver = null;
    }
  }

  function inspectMediaElement(element) {
    if (!element) return;
    const tag = (element.tagName || '').toLowerCase();
    const isVideo = tag === 'video';
    const isAudio = tag === 'audio';
    if (!isVideo && !isAudio) return;

    startNetworkWatcher();

    const width = isVideo ? (element.videoWidth || element.clientWidth || 0) : 0;
    const height = isVideo ? (element.videoHeight || element.clientHeight || 0) : 0;
    const hint = isVideo ? 'video' : 'audio';

    report(element.currentSrc, element.getAttribute('type') || '', hint, width, height);
    report(element.src, element.getAttribute('type') || '', hint, width, height);

    element.querySelectorAll('source[src]').forEach(source => {
      report(
        source.src,
        source.type || '',
        isVideo ? 'source-video' : 'source-audio',
        width,
        height
      );
    });
  }

  function scanMediaElements() {
    document.querySelectorAll('video, audio').forEach(inspectMediaElement);
  }

  startNetworkWatcher();
  scanMediaElements();

  const mediaEventHandler = event => {
    const target = event.target;
    if (target && (target.tagName === 'VIDEO' || target.tagName === 'AUDIO')) {
      inspectMediaElement(target);
    }
  };
  document.addEventListener('loadedmetadata', mediaEventHandler, true);
  document.addEventListener('loadeddata', mediaEventHandler, true);
  document.addEventListener('canplay', mediaEventHandler, true);
  document.addEventListener('play', mediaEventHandler, true);
  document.addEventListener('playing', mediaEventHandler, true);

  setTimeout(scanMediaElements, 500);
  setTimeout(scanMediaElements, 1500);
  setTimeout(scanMediaElements, 4000);
  setTimeout(scanMediaElements, 12000);

  window.addEventListener('pageshow', scanMediaElements, true);
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) scanMediaElements();
  }, true);
})();
