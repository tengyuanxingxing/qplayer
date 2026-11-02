const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

// Exercise the production handler against its transport contract without credentials.
function handler(name, dependencies) {
  const source = fs.readFileSync(path.join(__dirname, name, 'src/main.js'), 'utf8');
  const start = source.indexOf('function comments(args)');
  const end = source.indexOf('module.exports', start);
  const context = vm.createContext({...dependencies});
  vm.runInContext(source.slice(start, end), context);
  return context.comments;
}
test('NetEase comments preserve identity, text, pagination and hot-list boundaries', async () => {
  const comments = handler('netease', {weapi: async (url, args) => {
    assert.equal(url, 'v1/resource/comments/R_SO_4_123');
    assert.equal(args.offset, 20);
    return {code: 200, more: true, comments: [{commentId: 42, user: {nickname: '用户'}, content: '评论', time: 1000, likedCount: 3}], hotComments: []};
  }});
  const page = await comments({id: '123', cursor: '20', sort: 'new'});
  assert.equal(page.items[0].id, '42'); assert.equal(page.items[0].author, '用户');
  assert.equal(page.items[0].content, '评论'); assert.equal(page.nextCursor, '21');
  const hot = await comments({id: '123', cursor: '20', sort: 'hot'});
  assert.equal(hot.nextCursor, '');
});
test('QQ comments resolve a song mid before fetching the numeric comment thread', async () => {
  const comments = handler('qqmusic', {
    songNumericId: async mid => {assert.equal(mid, 'mid123'); return 456;},
    httpGetText: async url => {
      assert.match(url, /topid=456/); assert.match(url, /pagenum=1/);
      return JSON.stringify({code: 0, comment: {commenttotal: 60, commentlist: [{commentid: 'c1', nick: '用户', rootcommentcontent: '文字', time: 1000, praisenum: 2}]}});
    }, REF_BASE: 'https://y.qq.com/'
  });
  const page = await comments({id: 'mid123', cursor: '1', sort: 'new'});
  assert.equal(page.items[0].id, 'c1'); assert.equal(page.items[0].content, '文字');
  assert.equal(page.items[0].likeCount, 2); assert.equal(page.nextCursor, '2');
});
test('provider failures are errors rather than empty successful comment pages', async () => {
  const netease = handler('netease', {weapi: async () => ({code: 401})});
  await assert.rejects(netease({id: '1'}));
  const qq = handler('qqmusic', {songNumericId: async () => 1, httpGetText: async () => '{"code":1}', REF_BASE: ''});
  await assert.rejects(qq({id: '1'}));
});
