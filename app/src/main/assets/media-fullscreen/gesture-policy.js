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

  function blocksNestedScroll(state) {
    if (!state) return false;
    const scrollRange = state.scrollHeight - state.clientHeight;
    if (scrollRange <= 1) return false;

    const overflowScrollable = /(auto|scroll|overlay)/.test(state.overflowY || '');
    const alreadyScrolled = state.scrollTop > 1;
    const trapsVerticalOverscroll = /(contain|none)/.test(state.overscrollBehaviorY || '');
    const ownsVerticalPan = /(^|\s)pan-y(\s|$)/.test(state.touchAction || '');

    return overflowScrollable || alreadyScrolled || trapsVerticalOverscroll || ownsVerticalPan;
  }

  return Object.freeze({ isAtTop, blocksNestedScroll });
});
