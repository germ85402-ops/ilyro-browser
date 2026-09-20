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

assert.equal(policy.isAtTop({
  scrollTop: 0,
  windowScrollY: 0,
  fullscreen: false,
  scale: 1.2
}), false);

const baseScroller = {
  scrollHeight: 800,
  clientHeight: 400,
  scrollTop: 0,
  overflowY: 'hidden',
  overscrollBehaviorY: 'auto',
  touchAction: 'auto'
};

assert.equal(policy.blocksNestedScroll({ ...baseScroller, overflowY: 'auto' }), true);
assert.equal(policy.blocksNestedScroll({ ...baseScroller, scrollTop: 12 }), true);
assert.equal(policy.blocksNestedScroll({ ...baseScroller, overscrollBehaviorY: 'contain' }), true);
assert.equal(policy.blocksNestedScroll({ ...baseScroller, touchAction: 'pan-y' }), true);
assert.equal(policy.blocksNestedScroll(baseScroller), false);
assert.equal(policy.blocksNestedScroll({ ...baseScroller, scrollHeight: 400 }), false);

console.log('gesture-policy tests passed');
