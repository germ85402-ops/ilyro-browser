(() => {
  let port = null;
  let networkPort = null;
  let networkWatcherStarted = false;
  let performanceObserver = null;
  const seen = new Map();
  const MAX_SEEN = 256;
  const MEDIA_URL_RE = /\.(m3u8|mpd|mp4|webm|m4v|mov|ogv|mp3|m4a|aac|flac|wav|oga|opus)(?:$|[?#])/i;
  const STREAM_HINT_RE = /(?:[?&](?:format|type|mime)=(?:m3u8|application%2F(?:vnd\.apple\.mpegurl|dash\+xml))|\/(?:hls|dash)\/[^?#]*(?:master|manifest|playlist|index))/i;
  const YOUTUBE_PROGRESSIVE_ITAGS = new Set([17, 18, 22, 36, 43, 44, 45, 46]);
  const YOUTUBE_AUDIO_ITAGS = new Set([
    139, 140, 141, 171, 172, 249, 250, 251, 599, 600
  ]);
  const YOUTUBE_VIDEO_ITAGS = new Set([
    17, 18, 22, 36, 43, 44, 45, 46,
    160, 133, 134, 135, 136, 137, 264, 266, 278, 242, 243, 244, 247, 248,
    271, 272, 298, 299, 302, 303, 308, 313, 315, 330, 331, 332, 333, 334,
    335, 336, 337, 394, 395, 396, 397, 398, 399, 400, 401
  ]);
  const YOUTUBE_HEIGHT_BY_ITAG = new Map(Object.entries({
    17: 144, 36: 240, 18: 360, 43: 360, 44: 480, 22: 720, 45: 720, 46: 1080,
    160: 144, 133: 240, 134: 360, 135: 480, 136: 720, 137: 1080, 264: 1440,
    266: 2160, 278: 144, 242: 240, 243: 360, 244: 480, 247: 720, 248: 1080,
    271: 1440, 272: 2160, 298: 720, 299: 1080, 302: 720, 303: 1080, 308: 1440,
    313: 2160, 315: 2160, 330: 144, 331: 240, 332: 360, 333: 480, 334: 720,
    335: 1080, 336: 1440, 337: 2160, 394: 144, 395: 240, 396: 360, 397: 480,
    398: 720, 399: 1080, 400: 1440, 401: 2160
  }).map(([key, value]) => [Number(key), value]));

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

  function youtubeStreamInfo(value) {
    try {
      const url = new URL(value, location.href);
      const mediaHost = (url.hostname || '').toLowerCase();
      if (!(mediaHost === 'googlevideo.com' || mediaHost.endsWith('.googlevideo.com'))) return null;
      if (!url.pathname.includes('/videoplayback')) return null;

      const queryMime = (url.searchParams.get('mime') || '').toLowerCase();
      const itag = Number(url.searchParams.get('itag') || 0);
      const kind = queryMime.startsWith('video/') ? 'video' :
        queryMime.startsWith('audio/') ? 'audio' :
        YOUTUBE_AUDIO_ITAGS.has(itag) ? 'audio' :
        YOUTUBE_VIDEO_ITAGS.has(itag) ? 'video' : '';
      if (!kind) return null;

      ['range', 'rn', 'rbuf'].forEach(name => url.searchParams.delete(name));

      const progressive = kind === 'video' && YOUTUBE_PROGRESSIVE_ITAGS.has(itag);
      const height = YOUTUBE_HEIGHT_BY_ITAG.get(itag) ||
        Math.max(0, Number(url.searchParams.get('height') || 0));
      const width = Math.max(0, Number(url.searchParams.get('width') || 0));
      const bitrate = Math.max(0, Number(url.searchParams.get('bitrate') || 0));
      const frameRate = Math.max(0, Number(url.searchParams.get('fps') || 0));
      const codecMatch = queryMime.match(/codecs?=["']?([^;"']+)/i);

      return {
        url: url.href,
        kind,
        mime: queryMime,
        source: kind === 'audio'
          ? 'youtube-adaptive-audio'
          : (progressive ? 'youtube-progressive' : 'youtube-adaptive-video'),
        width,
        height,
        bitrate,
        frameRate,
        codecs: codecMatch ? codecMatch[1].trim() : '',
        requiresSeparateAudio: kind === 'video' && !progressive
      };
    } catch (_) {
      return null;
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
    const signature = (mime || '') + '|' + (source || '') + '|' + (width || 0) + '|' + (height || 0);
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
    return value
      .replace(/\s+-\s+YouTube\s*$/i, '')
      .replace(/\s+/g, ' ')
      .trim()
      .slice(0, 180);
  }

  function report(rawUrl, mime, source, width, height, titleOverride) {
    const youtube = youtubeStreamInfo(rawUrl);
    const url = normalizedUrl(youtube ? youtube.url : rawUrl);
    if (!url) return;

    const effectiveMime = youtube ? youtube.mime : (mime || '');
    const effectiveSource = youtube ? youtube.source : (source || 'page');
    const kind = youtube ? youtube.kind : classify(url, effectiveMime, effectiveSource);
    if (!kind) return;

    const inferred = inferQualityFromUrl(url);
    const mediaWidth = youtube && youtube.width > 0
      ? youtube.width
      : (Number.isFinite(width) && width > 0 ? Math.round(width) : inferred.width);
    const mediaHeight = youtube && youtube.height > 0
      ? youtube.height
      : (Number.isFinite(height) && height > 0 ? Math.round(height) : inferred.height);

    const key = kind + '|' + url;
    if (!remember(key, effectiveMime, effectiveSource, mediaWidth, mediaHeight)) return;

    const targetPort = connect();
    if (!targetPort) return;

    try {
      targetPort.postMessage({
        type: 'media',
        url,
        kind,
        mime: effectiveMime,
        source: effectiveSource,
        pageUrl: location.href,
        title: cleanMediaTitle(titleOverride) || cleanMediaTitle(document.title),
        width: Math.max(0, mediaWidth),
        height: Math.max(0, mediaHeight),
        bitrate: youtube ? youtube.bitrate : 0,
        frameRate: youtube ? youtube.frameRate : 0,
        codecs: youtube ? youtube.codecs : '',
        separateAudio: youtube ? youtube.requiresSeparateAudio === true : false
      });
    } catch (_) {
      port = null;
    }
  }

  function connectNetworkPort() {
    if (networkPort) return;
    try {
      const connected = browser.runtime.connect({ name: 'ilyro-media-network' });
      networkPort = connected;
      connected.onMessage.addListener(message => {
        if (!message ||
            message.type !== 'ilyro-network-media' ||
            typeof message.url !== 'string') {
          return;
        }
        // The background page sees the actual googlevideo network request even when Gecko does
        // not expose it through PerformanceObserver.
        report(message.url, '', 'youtube-webrequest', 0, 0);
      });
      connected.onDisconnect.addListener(() => {
        if (networkPort === connected) networkPort = null;
        setTimeout(connectNetworkPort, 250);
      });
    } catch (_) {
      networkPort = null;
      setTimeout(connectNetworkPort, 500);
    }
  }

  function inspectPerformanceEntry(entry) {
    const name = typeof entry?.name === 'string' ? entry.name : '';
    if (!name) return;

    const initiator = (entry?.initiatorType || '').toLowerCase();
    const isMediaInitiator = initiator === 'video' || initiator === 'audio';
    const youtube = youtubeStreamInfo(name);

    if (!youtube && !isMediaInitiator && !MEDIA_URL_RE.test(name) && !STREAM_HINT_RE.test(name)) return;

    let hint = 'network';
    if (initiator === 'video') hint = 'resource-video';
    if (initiator === 'audio') hint = 'resource-audio';
    report(name, youtube ? youtube.mime : '', hint, 0, 0);
  }

  function startNetworkWatcher() {
    if (networkWatcherStarted) return;
    networkWatcherStarted = true;

    try {
      const existing = performance.getEntriesByType('resource');
      existing.slice(Math.max(0, existing.length - 300)).forEach(inspectPerformanceEntry);

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

    element.querySelectorAll('source[src]').forEach(sourceElement => {
      report(
        sourceElement.src,
        sourceElement.type || '',
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

  connectNetworkPort();
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
    if (!document.hidden) {
      startNetworkWatcher();
      scanMediaElements();
    }
  }, true);
})();
