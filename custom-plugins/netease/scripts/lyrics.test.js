"use strict";

var test = require("node:test");
var assert = require("node:assert/strict");
var lyrics = require("../src/lyrics");

function asset(format, text, role) {
  return {format: format, role: role || "original", text: text};
}
function payload() { return {assets: Array.prototype.slice.call(arguments)}; }
function ttml(content, head) {
  return payload(asset("ttml", '<tt xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:itunes="http://music.apple.com/lyric-ttml-internal">'
    + '<head>' + (head || '') + '</head><body><div>' + content + '</div></body></tt>'));
}
function words(text, extra, attrs) {
  return '<p begin="1.000" end="3.000" ' + (attrs || '') + '>'
    + '<span begin="1.000" end="2.000">' + text.slice(0, text.length / 2) + '</span>'
    + '<span begin="2.000" end="3.000">' + text.slice(text.length / 2) + '</span>'
    + (extra || '') + '</p>';
}
function yrc(text, extras) {
  return payload.apply(null, [asset("yrc", '[1000,2000](1000,1000,0)' + text.slice(0, text.length / 2)
    + '(2000,1000,0)' + text.slice(text.length / 2))].concat(extras || []));
}
var original = "春风又绿江南岸";
var plain = payload(asset("lrc", "[00:01.000]" + original));

test("word-timed YRC beats line-only TTML, including a single whole-line span", function () {
  var line = ttml('<p begin="1" end="3"><span begin="1" end="3">' + original + '</span></p>');
  var timed = yrc(original);
  assert.equal(lyrics.select([line, timed]), timed);
});

test("actual word timing beats line timing with translation", function () {
  var timed = ttml(words(original));
  var translated = payload(plain.assets[0], asset("lrc", "[00:01.000]Translation", "translation"));
  assert.equal(lyrics.select([translated, timed]), timed);
});

test("translation and romanization can make YRC richer than word-timed TTML", function () {
  var timed = ttml(words(original));
  var rich = yrc(original, [asset("lrc", "[00:01.000]Translation", "translation"),
    asset("lrc", "[00:01.000]Romanization", "romanization")]);
  assert.equal(lyrics.select([timed, rich]), rich);
});

test("background vocals take priority over sidecars at equal word timing", function () {
  var rich = ttml(words(original, '<span ttm:role="x-bg"><span begin="1" end="2">和声</span></span>'));
  var translated = yrc(original, [asset("lrc", "[00:01.000]Translation", "translation")]);
  assert.equal(lyrics.select([translated, rich]), rich);
});

test("two actual singing agents distinguish duet lyrics from a single voice", function () {
  var duet = ttml(words(original.slice(0, 3), '', 'ttm:agent="v1"')
    + words(original.slice(3), '', 'ttm:agent="v2"'));
  var single = ttml(words(original));
  assert.equal(lyrics.select([single, duet]), duet);
});

test("empty vocal metadata does not outrank useful translation", function () {
  var emptyBackground = ttml(words(original, '<span ttm:role="x-bg"></span>'));
  var translated = yrc(original, [asset("lrc", "[00:01.000]Translation", "translation")]);
  assert.equal(lyrics.select([emptyBackground, translated]), translated);
});

test("head-level transliteration is counted for its referenced lyric line", function () {
  var romanized = ttml(words(original, '', 'itunes:key="L1"'),
    '<transliterations><transliteration><text for="L1">romanization</text></transliteration></transliterations>');
  var basic = yrc(original);
  assert.equal(lyrics.select([basic, romanized]), romanized);
});

test("untimed markup, HTML and broken TTML cannot displace synchronized lyrics", function () {
  var broken = payload(asset("ttml", '<tt><body>' + words(original)));
  var html = payload(asset("ttml", '<html><body><p>Error</p></body></html>'));
  var untimed = ttml('<p>' + original + '</p>');
  assert.equal(lyrics.select([broken, html, untimed, plain]), plain);
});

test("zero-duration spans do not advertise word timing", function () {
  var invalidTiming = ttml('<p begin="1" end="3"><span begin="1" end="1">春风又</span>'
    + '<span begin="2" end="2">绿江南岸</span></p>');
  var timed = yrc(original);
  assert.equal(lyrics.select([invalidTiming, timed]), timed);
});

test("a short rich excerpt cannot displace substantially more complete lyrics", function () {
  var excerpt = ttml(words("春风又绿"));
  assert.equal(lyrics.select([excerpt, plain]), plain);
});

test("small editorial differences do not force plain lyrics over rich timing", function () {
  var full = original + "明月何时照我还";
  var rich = ttml(words(full));
  var withCredit = payload(asset("lrc", '[00:00.000]作词\n[00:01.000]' + full));
  assert.equal(lyrics.select([withCredit, rich]), rich);
});

test("word-timed coverage matters when only part of TTML has timed spans", function () {
  var partial = ttml(words("春风") + '<p begin="3" end="5">又绿江南岸</p>');
  var complete = yrc(original);
  assert.equal(lyrics.select([partial, complete]), complete);
});

test("equivalent quality preserves source order", function () {
  var first = ttml(words(original));
  var second = yrc(original);
  assert.equal(lyrics.select([first, second]), first);
});

test("NetEase keeps valid LRC when its YRC is unusable", function () {
  var selected = lyrics.netease({yrc: {lyric: '[1000,1000]invalid'}, lrc: {lyric: plain.assets[0].text}});
  assert.equal(selected.assets[0].format, 'lrc');
  assert.equal(selected.assets[0].text, plain.assets[0].text);
});

test("YRC-specific sidecars preserve the selected source's timing", function () {
  var timed = yrc(original);
  var selected = lyrics.netease({yrc: {lyric: timed.assets[0].text},
    tlyric: {lyric: '[00:02.000]Line version'}, ytlrc: {lyric: '[00:01.000]Word version'}});
  assert.equal(selected.assets[1].text, '[00:01.000]Word version');
});

test("YRC background vocals are compared as content, not as a TTML-only feature", function () {
  var basic = ttml(words(original));
  var background = yrc(original);
  background.assets[0].text += '(2500,500,0)（和声）';
  assert.equal(lyrics.select([basic, background]), background);
});

test("a rejected source does not hide the other source's usable lyrics", async function () {
  assert.equal(await lyrics.resolve(Promise.reject(new Error('AMLL unavailable')), Promise.resolve(plain)), plain);
});

test("both rejected sources propagate a real fetch error", async function () {
  var failure = new Error('NetEase unavailable');
  await assert.rejects(lyrics.resolve(Promise.reject(new Error('AMLL unavailable')), Promise.reject(failure)),
    function (error) { return error === failure; });
});

test("a slower high-quality source is considered before publishing the result", async function () {
  var finish;
  var provider = new Promise(function (resolve) { finish = resolve; });
  var selected = lyrics.resolve(Promise.resolve(plain), provider);
  await Promise.resolve();
  var rich = ttml(words(original));
  finish(rich);
  assert.equal(await selected, rich);
});

test("no usable synchronized lyrics returns the ABI's empty asset collection", function () {
  assert.deepEqual(lyrics.select([payload(asset('lrc', '[ar:artist]')), {assets: []}]), {assets: []});
});
