(() => {
  const host = location.hostname.toLowerCase();
  const isYouTube = host === 'youtube.com' || host.endsWith('.youtube.com') || host === 'youtu.be';

  // Keep YouTube's fullscreen media box large enough to use the available surface.
  // object-fit: contain preserves the original aspect ratio without cropping or stretching.
  const style = document.createElement('style');
  style.textContent = `
    :fullscreen {
      background-color: #000 !important;
    }

    video:fullscreen,
    :fullscreen video {
      object-fit: contain !important;
      object-position: center center !important;
      background: #000 !important;
    }

    /*
     * YouTube Ambient Mode is painted behind the media layer. Keep the fullscreen
     * surface transparent so that glow remains visible around the centered video.
     */
    html.ilyro-youtube-fullscreen :fullscreen {
      background-color: transparent !important;
      position: fixed !important;
      inset: 0 !important;
      width: 100% !important;
      height: 100% !important;
      width: 100dvw !important;
      height: 100dvh !important;
      min-width: 100% !important;
      min-height: 100% !important;
      max-width: none !important;
      max-height: none !important;
      margin: 0 !important;
    }

    html.ilyro-youtube-fullscreen :fullscreen .html5-video-container {
      position: absolute !important;
      inset: 0 !important;
      width: 100% !important;
      height: 100% !important;
      min-width: 100% !important;
      min-height: 100% !important;
      max-width: none !important;
      max-height: none !important;
      margin: 0 !important;
      overflow: hidden !important;
      display: flex !important;
      align-items: center !important;
      justify-content: center !important;
      background-color: transparent !important;
    }

    html.ilyro-youtube-fullscreen :fullscreen .html5-video-container video.html5-main-video,
    html.ilyro-youtube-fullscreen :fullscreen .html5-video-container video.video-stream {
      box-sizing: border-box !important;
      position: relative !important;
      inset: auto !important;
      top: auto !important;
      left: auto !important;
      right: auto !important;
      bottom: auto !important;
      display: block !important;
      width: 100% !important;
      height: 100% !important;
      min-width: 0 !important;
      min-height: 0 !important;
      max-width: 100% !important;
      max-height: 100% !important;
      margin: 0 !important;
      flex: 0 0 auto !important;
      transform: none !important;
      object-fit: contain !important;
      object-position: center center !important;
      background: transparent !important;
    }
  `;

  function install() {
    (document.head || document.documentElement)?.appendChild(style);
  }

  if (document.documentElement) install();
  else document.addEventListener('DOMContentLoaded', install, { once: true });

  if (!isYouTube) return;

  function syncFullscreenState() {
    document.documentElement?.classList.toggle(
      'ilyro-youtube-fullscreen',
      Boolean(document.fullscreenElement)
    );
  }

  document.addEventListener('fullscreenchange', syncFullscreenState, true);
  syncFullscreenState();
})();
