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
  const STREAM_HINT_RE = /(?:[?&](?:format|type|mime)=(?:m3u8|application%2F(?:vnd\.apple\.mpegurl|dash\+xml))|\/(?:hls|dash)\/[^?#]*(?:master|manifest|playlist|index))/i;

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
    const lowerUrl = (url || '').toLowerCase();
    const type = (mime || '').toLowerCase();
    const source = (hint || '').toLowerCase();

    if (
      type.includes('mpegurl') ||
      path.endsWith('.m3u8') ||
      /[?&](?:format|type)=m3u8(?:&|$)/i.test(lowerUrl) ||
      (/\/hls\//i.test(lowerUrl) && /(?:master|playlist|index)/i.test(lowerUrl))
    ) return 'hls';
    if (
      type.includes('dash+xml') ||
      path.endsWith('.mpd') ||
      /[?&](?:format|type)=mpd(?:&|$)/i.test(lowerUrl) ||
      (/\/dash\//i.test(lowerUrl) && /(?:manifest|index)/i.test(lowerUrl))
    ) return 'dash';

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

  function inferQualityFromUrl(url) {
    const decoded = (() => {
      try { return decodeURIComponent(url); } catch (_) { return url; }
    })();
    const dimensions = decoded.match(/(?:^|[^0-9])(\d{3,4})[x×](\d{3,4})(?:[^0-9]|$)/i);
    if (dimensions) {
      return {
        width: Math.max(0, Number(dimensions[1]) || 0),
        height: Math.max(0, Number(dimensions[2]) || 0)
      };
    }
    const quality = decoded.match(/(?:^|[/_.-])(4320|2160|1440|1080|900|720|576|540|480|360|240|144)p(?:[/_.?#&-]|$)/i) ||
      decoded.match(/(?:^|[/_.-])(4320|2160|1440|1080|900|720|576|540|480|360|240|144)(?:[/_.-]|$)/i);
    return { width: 0, height: quality ? Math.max(0, Number(quality[1]) || 0) : 0 };
  }

  function cleanMediaTitle(value) {
    if (typeof value !== 'string') return '';
    return value.replace(/\s+/g, ' ').trim().slice(0, 180);
  }

  function report(rawUrl, mime, source, width, height, titleOverride) {
    const url = normalizedUrl(rawUrl);
    if (!url) return;
    const kind = classify(url, mime, source);
    if (!kind) return;

    const inferred = inferQualityFromUrl(url);
    const mediaWidth = Number.isFinite(width) && width > 0 ? Math.round(width) : inferred.width;
    const mediaHeight = Number.isFinite(height) && height > 0 ? Math.round(height) : inferred.height;

    const key = `${kind}|${url}`;
    if (!remember(key, mime, source, mediaWidth, mediaHeight)) return;

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
        title: cleanMediaTitle(titleOverride) || cleanMediaTitle(document.title),
        width: Math.max(0, mediaWidth),
        height: Math.max(0, mediaHeight)
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

    if (!isMediaInitiator && !MEDIA_URL_RE.test(name) && !STREAM_HINT_RE.test(name)) return;

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

    // Only intrinsic media dimensions describe stream quality. CSS/client dimensions describe
    // the player box and previously produced fake qualities such as 538p or 150p.
    const width = isVideo ? (element.videoWidth || 0) : 0;
    const height = isVideo ? (element.videoHeight || 0) : 0;
    const hint = isVideo ? 'video' : 'audio';
    const mediaTitle =
      element.getAttribute('aria-label') ||
      element.getAttribute('title') ||
      element.getAttribute('data-title') ||
      element.closest?.('[data-title]')?.getAttribute?.('data-title') ||
      '';

    report(element.currentSrc, element.getAttribute('type') || '', hint, width, height, mediaTitle);
    report(element.src, element.getAttribute('type') || '', hint, width, height, mediaTitle);

    element.querySelectorAll('source[src]').forEach(source => {
      report(
        source.src,
        source.type || '',
        isVideo ? 'source-video' : 'source-audio',
        width,
        height,
        mediaTitle
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

  // Players are often inserted or swapped after user interaction. Observe the document instead
  // of relying only on fixed timers, while batching scans to keep the page cheap.
  let mutationScanTimer = null;
  try {
    const observer = new MutationObserver(mutations => {
      if (!mutations.some(mutation =>
        mutation.type === 'childList' ||
        (mutation.type === 'attributes' && ['src', 'type'].includes(mutation.attributeName))
      )) return;
      if (mutationScanTimer !== null) return;
      mutationScanTimer = setTimeout(() => {
        mutationScanTimer = null;
        scanMediaElements();
      }, 120);
    });
    observer.observe(document.documentElement || document, {
      subtree: true,
      childList: true,
      attributes: true,
      attributeFilter: ['src', 'type']
    });
  } catch (_) {
  }

  window.addEventListener('pageshow', scanMediaElements, true);
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) scanMediaElements();
  }, true);
})();
