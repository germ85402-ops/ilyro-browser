(() => {
  const host = location.hostname.toLowerCase();
  const isYouTube = host === 'youtube.com' || host.endsWith('.youtube.com') || host === 'youtu.be';

  // Lightweight fullscreen handling: keep YouTube in control of the player itself.
  // ILYRO only makes the existing video container fill the fullscreen element and
  // scales the video inside it with object-fit: contain.
  const style = document.createElement('style');
  style.textContent = `
    video:fullscreen,
    :fullscreen video {
      object-fit: contain !important;
      object-position: center center !important;
      background: #000 !important;
    }

    html.ilyro-youtube-fullscreen :fullscreen .html5-video-container {
      position: absolute !important;
      inset: 0 !important;
      width: 100% !important;
      height: 100% !important;
      margin: 0 !important;
      overflow: hidden !important;
      background: #000 !important;
    }

    html.ilyro-youtube-fullscreen :fullscreen .html5-video-container video.html5-main-video,
    html.ilyro-youtube-fullscreen :fullscreen .html5-video-container video.video-stream {
      position: absolute !important;
      inset: 0 !important;
      top: 0 !important;
      left: 0 !important;
      right: 0 !important;
      bottom: 0 !important;
      width: 100% !important;
      height: 100% !important;
      min-width: 0 !important;
      min-height: 0 !important;
      max-width: none !important;
      max-height: none !important;
      margin: 0 !important;
      transform: none !important;
      object-fit: contain !important;
      object-position: center center !important;
      background: #000 !important;
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
