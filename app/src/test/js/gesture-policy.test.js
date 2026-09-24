'use strict';

const assert = require('node:assert/strict');
const policy = require('../../main/assets/media-fullscreen/gesture-policy.js');

assert.equal(policy.isAtTop({
  scrollTop: 0,
  windowScrollY: 0,
  fullscreen: false,
  scale: 1
}), true);

assert.equal(policy.isAtTop({
  scrollTop: 0,
  windowScrollY: 2,
  fullscreen: false,
  scale: 1
}), false);

assert.equal(policy.isAtTop({
  scrollTop: 0,
  windowScrollY: 0,
  fullscreen: true,
  scale: 1
}), false);

for (const url of [
  'https://youtube.com/shorts/video-id',
  'https://www.youtube.com/shorts/video-id?t=4',
  'https://m.youtube.com/shorts/video-id',
  'https://youtube.com/shorts'
]) {
  assert.equal(policy.disablesPullToRefresh(url), true, url);
}

for (const url of [
  'https://youtube.com/watch?v=video-id',
  'https://youtube.com.evil.com/shorts/video-id',
  'https://notyoutube.com/shorts/video-id',
  'https://example.com/shorts/video-id',
  'not a url'
]) {
  assert.equal(policy.disablesPullToRefresh(url), false, url);
}

assert.equal(policy.isAtTop({
  scrollTop: 0,
  windowScrollY: 0,
  fullscreen: false,
  scale: 1.2
}), false);

// Keep refresh away from text editing and embedded frames, but allow video surfaces.
assert.equal(policy.blocksRefreshTarget({ formControl: true }), true);
assert.equal(policy.blocksRefreshTarget({ frame: true }), true);
assert.equal(policy.blocksRefreshTarget({ editable: true }), true);
assert.equal(policy.blocksRefreshTarget({ video: true }), false);

const baseScroller = {
  scrollHeight: 800,
  clientHeight: 400,
  scrollTop: 0,
  overflowY: 'hidden',
  overscrollBehaviorY: 'auto',
  touchAction: 'auto'
};

// A nested scroller at its top edge cannot consume a downward refresh pull.
assert.equal(policy.blocksNestedScroll({ ...baseScroller, overflowY: 'auto' }), false);
assert.equal(policy.blocksNestedScroll({ ...baseScroller, overflowY: 'auto', touchAction: 'pan-y' }), false);

// Preserve the nested scroller's gesture when it can scroll back toward its top.
assert.equal(policy.blocksNestedScroll({ ...baseScroller, overflowY: 'auto', scrollTop: 12 }), true);
assert.equal(policy.blocksNestedScroll({ ...baseScroller, scrollTop: -12 }), true);

// Explicit overscroll containment and non-top-level scroll states still own the pull.
assert.equal(policy.blocksNestedScroll({ ...baseScroller, overscrollBehaviorY: 'contain' }), true);
assert.equal(policy.blocksNestedScroll({ ...baseScroller, overscrollBehaviorY: 'none' }), true);
assert.equal(policy.blocksNestedScroll(baseScroller), false);
assert.equal(policy.blocksNestedScroll({ ...baseScroller, scrollHeight: 400 }), false);

console.log('gesture-policy tests passed');
