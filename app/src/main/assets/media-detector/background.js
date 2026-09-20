(() => {
  const GOOGLEVIDEO_FILTER = {
    urls: [
      "*://*.googlevideo.com/videoplayback*",
      "*://googlevideo.com/videoplayback*"
    ]
  };
  const portsByTab = new Map();
  const allPorts = new Set();
  const portMeta = new Map();
  const recentRequests = [];
  const MAX_RECENT_REQUESTS = 24;
  const MAX_RECENT_AGE_MS = 10000;

  function pageHost(value) {
    if (typeof value !== "string" || !value) return "";
    try {
      return new URL(value).hostname.toLowerCase().replace(/\.$/, "");
    } catch (_) {
      return "";
    }
  }

  function sameSite(first, second) {
    const firstHost = pageHost(first);
    const secondHost = pageHost(second);
    if (!firstHost || !secondHost) return false;
    if (firstHost === secondHost) return true;
    const youtubeHosts = new Set([
      "youtube.com",
      "www.youtube.com",
      "m.youtube.com",
      "music.youtube.com",
      "youtu.be"
    ]);
    return youtubeHosts.has(firstHost) && youtubeHosts.has(secondHost);
  }

  function rememberRequest(details) {
    recentRequests.push({
      tabId: Number.isInteger(details.tabId) ? details.tabId : -1,
      frameId: Number.isInteger(details.frameId) ? details.frameId : 0,
      url: details.url,
      documentUrl: details.documentUrl || details.originUrl || details.initiator || "",
      at: Date.now()
    });
    while (recentRequests.length > MAX_RECENT_REQUESTS) recentRequests.shift();
  }

  function send(port, url) {
    try {
      port.postMessage({
        type: "ilyro-network-media",
        url
      });
      return true;
    } catch (_) {
      return false;
    }
  }

  function matchingPorts(details) {
    const tabId = Number.isInteger(details.tabId) ? details.tabId : -1;
    const byTab = tabId >= 0
      ? [...allPorts].filter(port => portMeta.get(port)?.tabId === tabId)
      : [];
    if (byTab.length > 0) return byTab;

    const requestPage = details.documentUrl || details.originUrl || details.initiator || "";
    if (requestPage) {
      const byPage = [...allPorts].filter(port =>
        sameSite(portMeta.get(port)?.pageUrl || "", requestPage)
      );
      if (byPage.length > 0) return byPage;
    }

    // GeckoView may report -1 for a media subrequest and omit the document URL. In that case
    // there is no tab key to join on; forwarding to the live content ports keeps detection alive.
    // The content script reports its own page URL, so this remains scoped to the actual page
    // that is hosting the port rather than inventing a URL in the native layer.
    return [...allPorts];
  }

  function replayRecentRequests(port) {
    const meta = portMeta.get(port) || { tabId: -1, pageUrl: "" };
    recentRequests.slice(-8).forEach(request => {
      if (Date.now() - request.at > MAX_RECENT_AGE_MS) return;
      if (meta.tabId >= 0 && request.tabId >= 0 && meta.tabId !== request.tabId) {
        return;
      }
      if (request.documentUrl && meta.pageUrl && !sameSite(meta.pageUrl, request.documentUrl)) {
        return;
      }
      send(port, request.url);
    });
  }

  function addPort(port) {
    if (!port || port.name !== "ilyro-media-network") return;
    const tabId = port.sender && port.sender.tab ? port.sender.tab.id : null;
    const frameId = port.sender && Number.isInteger(port.sender.frameId)
      ? port.sender.frameId
      : 0;
    const normalizedTabId = Number.isInteger(tabId) && tabId >= 0 ? tabId : -1;
    const pageUrl = port.sender && typeof port.sender.url === "string"
      ? port.sender.url
      : "";

    portMeta.set(port, {
      tabId: normalizedTabId,
      frameId,
      pageUrl
    });
    allPorts.add(port);

    if (normalizedTabId >= 0) {
      let frames = portsByTab.get(normalizedTabId);
      if (!frames) {
        frames = new Map();
        portsByTab.set(normalizedTabId, frames);
      }
      frames.set(frameId, port);
    }
    replayRecentRequests(port);

    port.onDisconnect.addListener(() => {
      allPorts.delete(port);
      portMeta.delete(port);
      if (normalizedTabId < 0) return;
      const current = portsByTab.get(normalizedTabId);
      if (!current) return;
      if (current.get(frameId) === port) current.delete(frameId);
      if (current.size === 0) portsByTab.delete(normalizedTabId);
    });
  }

  browser.runtime.onConnect.addListener(addPort);

  function forward(details) {
    if (!details || !details.url) return;
    rememberRequest(details);

    const ports = matchingPorts(details);
    const preferredFrame = Number.isInteger(details.frameId) ? details.frameId : 0;
    const orderedPorts = ports
      .slice()
      .sort((first, second) => {
        const firstFrame = portMeta.get(first)?.frameId === preferredFrame ? 0 : 1;
        const secondFrame = portMeta.get(second)?.frameId === preferredFrame ? 0 : 1;
        return firstFrame - secondFrame;
      })
      .filter((port, index, all) => port && all.indexOf(port) === index);

    // A YouTube player can move between the main document and an embed frame during SPA
    // navigation. Broadcast to every matching live content-script port; the native bridge
    // deduplicates the same signed URL.
    orderedPorts.forEach(port => send(port, details.url));
  }

  browser.webRequest.onBeforeRequest.addListener(
    forward,
    GOOGLEVIDEO_FILTER
  );
})();
