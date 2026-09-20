(() => {
  const GOOGLEVIDEO_FILTER = {
    urls: [
      "*://*.googlevideo.com/videoplayback*",
      "*://googlevideo.com/videoplayback*"
    ]
  };
  const portsByTab = new Map();

  function addPort(port) {
    if (!port || port.name !== "ilyro-media-network") return;
    const tabId = port.sender && port.sender.tab ? port.sender.tab.id : null;
    if (!Number.isInteger(tabId) || tabId < 0) return;

    const frameId = Number.isInteger(port.sender.frameId) ? port.sender.frameId : 0;
    let frames = portsByTab.get(tabId);
    if (!frames) {
      frames = new Map();
      portsByTab.set(tabId, frames);
    }
    frames.set(frameId, port);

    port.onDisconnect.addListener(() => {
      const current = portsByTab.get(tabId);
      if (!current) return;
      if (current.get(frameId) === port) current.delete(frameId);
      if (current.size === 0) portsByTab.delete(tabId);
    });
  }

  browser.runtime.onConnect.addListener(addPort);

  function forward(details) {
    if (!details || !Number.isInteger(details.tabId) || details.tabId < 0 || !details.url) return;
    const frames = portsByTab.get(details.tabId);
    if (!frames || frames.size === 0) return;

    const preferredFrame = Number.isInteger(details.frameId) ? details.frameId : 0;
    const port =
      frames.get(preferredFrame) ||
      frames.get(0) ||
      frames.values().next().value;
    if (!port) return;

    try {
      port.postMessage({
        type: "ilyro-network-media",
        url: details.url
      });
    } catch (_) {
    }
  }

  browser.webRequest.onBeforeRequest.addListener(
    forward,
    GOOGLEVIDEO_FILTER
  );
})();
