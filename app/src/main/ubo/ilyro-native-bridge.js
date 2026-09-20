import µb from './background.js';
import { onBroadcast } from './broadcast.js';

const NATIVE_APP = 'ilyro_shield';
let nativePort = null;
let reconnectTimer = null;

function validPageUrl(value) {
    if (typeof value !== 'string') return '';
    const url = value.trim();
    return /^(https?|file):\/\//i.test(url) ? url : '';
}

function post(message) {
    try {
        nativePort?.postMessage(message);
    } catch (_) {
    }
}

function sameSite(left, right) {
    try {
        return new URL(left).hostname === new URL(right).hostname;
    } catch (_) {
        return false;
    }
}

function fallbackPageStore(url) {
    let siteMatch = null;
    for (const pageStore of µb.pageStores.values()) {
        if (pageStore.rawURL === url) return pageStore;
        if (siteMatch === null && sameSite(pageStore.rawURL, url)) {
            siteMatch = pageStore;
        }
    }
    return siteMatch;
}

async function pageStoreForUrl(url) {
    try {
        const tabs = await browser.tabs.query({ active: true });
        const tab = tabs.find(candidate => candidate.url === url) ||
            tabs.find(candidate => sameSite(candidate.url, url));
        if (Number.isInteger(tab?.id)) {
            const pageStore = µb.pageStoreFromTabId(tab.id);
            if (pageStore !== null) return pageStore;
        }
    } catch (_) {
    }
    return fallbackPageStore(url);
}

async function postSiteState(url) {
    if (!url) return;
    const pageStore = await pageStoreForUrl(url);
    const blocked = Math.max(0, Number(pageStore?.counts?.blocked?.any) || 0);
    const allowed = Math.max(0, Number(pageStore?.counts?.allowed?.any) || 0);
    post({
        type: 'siteState',
        url,
        enabled: µb.getNetFilteringSwitch(url) === true,
        statsAvailable: pageStore !== null,
        blocked,
        allowed,
    });
}

function postGlobalStats() {
    const stats = µb.requestStats || {};
    post({
        type: 'globalStats',
        blocked: Math.max(0, Number(stats.blockedCount) || 0),
        allowed: Math.max(0, Number(stats.allowedCount) || 0),
    });
}

onBroadcast(message => {
    if (message?.what !== 'assetsUpdated') return;
    post({ type: 'filterUpdateFinished', updatedAt: Date.now() });
    postGlobalStats();
});

function handleNativeMessage(message) {
    if (!message || typeof message !== 'object') return;
    const url = validPageUrl(message.url);

    if (message.type === 'getSiteState') {
        void postSiteState(url);
        return;
    }

    if (message.type === 'setSiteState') {
        if (!url || typeof message.enabled !== 'boolean') return;
        µb.toggleNetFilteringSwitch(url, '', message.enabled);
        void postSiteState(url);
    }

    if (message.type === 'getGlobalStats') {
        postGlobalStats();
        return;
    }

    if (message.type === 'updateFilters') {
        try {
            post({ type: 'filterUpdateStarted' });
            µb.scheduleAssetUpdater({ now: true, fetchDelay: 100, auto: true });
        } catch (_) {
            post({ type: 'filterUpdateFailed' });
        }
    }
}

function scheduleReconnect() {
    if (reconnectTimer !== null) return;
    reconnectTimer = setTimeout(() => {
        reconnectTimer = null;
        connectNative();
    }, 1000);
}

function connectNative() {
    try {
        const port = browser.runtime.connectNative(NATIVE_APP);
        nativePort = port;
        port.onMessage.addListener(handleNativeMessage);
        port.onDisconnect.addListener(() => {
            if (nativePort === port) nativePort = null;
            scheduleReconnect();
        });
        post({ type: 'bridgeReady' });
        postGlobalStats();
    } catch (_) {
        nativePort = null;
        scheduleReconnect();
    }
}

Promise.resolve(µb.isReadyPromise)
    .then(connectNative)
    .catch(scheduleReconnect);
