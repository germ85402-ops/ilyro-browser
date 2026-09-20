(() => {
  const GOOGLEVIDEO_FILTER = {
    urls: [
      "*://*.googlevideo.com/videoplayback*",
      "*://googlevideo.com/videoplayback*"
    ]
  };

  function forward(details) {
    if (!details || details.tabId == null || details.tabId < 0 || !details.url) return;

    const message = {
      type: "ilyro-network-media",
      url: details.url
    };

    // Prefer the frame that actually initiated the request. If the frame no longer exists
    // (YouTube frequently replaces player frames), fall back to the top-level content script.
    Promise.resolve(
      browser.tabs.sendMessage(
        details.tabId,
        message,
        Number.isInteger(details.frameId) && details.frameId >= 0
          ? { frameId: details.frameId }
          : undefined
      )
    ).catch(() => {
      return browser.tabs.sendMessage(details.tabId, message).catch(() => {});
    });
  }

  browser.webRequest.onBeforeRequest.addListener(
    forward,
    GOOGLEVIDEO_FILTER
  );
})();
