(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.ILYROGesturePolicy = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  function isAtTop(state) {
    return !!state &&
      state.scrollTop <= 1 &&
      state.windowScrollY <= 1 &&
      !state.fullscreen &&
      state.scale <= 1.01;
  }

  function blocksRefreshTarget(state) {
    return !!state && !!(state.formControl || state.frame || state.editable);
  }

  function blocksNestedScroll(state) {
    if (!state) return false;
    const scrollRange = Number(state.scrollHeight) - Number(state.clientHeight);
    if (!Number.isFinite(scrollRange) || scrollRange <= 1) return false;

    // A nested scroller at its top edge cannot consume a downward pull. Allow the
    // browser refresh gesture there, but preserve ownership away from the edge and
    // for containers that explicitly contain vertical overscroll.
    const scrollTop = Number(state.scrollTop);
    const alreadyScrolled = Number.isFinite(scrollTop) && Math.abs(scrollTop) > 1;
    const trapsVerticalOverscroll = /(contain|none)/.test(state.overscrollBehaviorY || '');

    return alreadyScrolled || trapsVerticalOverscroll;
  }

  return Object.freeze({ isAtTop, blocksNestedScroll, blocksRefreshTarget });
});
