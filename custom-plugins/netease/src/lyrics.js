"use strict";

// Allow punctuation, credits and editorial differences, but do not let a short
// rich excerpt displace a substantially more complete lyric from another source.
var MIN_TEXT_COVERAGE = 0.8;

function characters(text) {
  return String(text || "")
    .replace(/&(?:#x[0-9a-f]+|#\d+|amp|lt|gt|quot|apos);/gi, "x")
    .replace(/[\s.,!?;:"'()[\]{}…，。！？、；：“”‘’（）《》〈〉「」『』—\-·]/g, "").length;
}

function quality() {
  return {characters: 0, wordCharacters: 0, lines: 0, translation: 0,
    romanization: 0, background: false, agents: Object.create(null)};
}

function clock(value) {
  if (value === undefined || !/^(?:\d+:){0,2}\d+(?:\.\d+)?$/.test(value)) return -1;
  var parts = value.split(":"), seconds = 0;
  for (var i = 0; i < parts.length; i++) seconds = seconds * 60 + Number(parts[i]);
  return Math.round(seconds * 1000);
}

function attributes(text) {
  var result = Object.create(null), end = 0, match;
  var pattern = /([\w:.-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')/g;
  while ((match = pattern.exec(text))) {
    if (text.slice(end, match.index).trim() || result[match[1]] !== undefined) return null;
    result[match[1]] = match[2] !== undefined ? match[2] : match[3];
    end = pattern.lastIndex;
  }
  return text.slice(end).trim() ? null : result;
}

// A structural scan of the TTML subset rendered by QPlayer. Inspect text-bearing
// timing spans, not file extensions or metadata tags. No DOM/Java access is
// available in the plugin sandbox. The host remains responsible for rendering.
function ttml(text) {
  var result = quality(), stack = [], paragraphs = [], transliterations = Object.create(null);
  var tokens = /<!--[\s\S]*?-->|<\?[\s\S]*?\?>|<!\[CDATA\[[\s\S]*?\]\]>|<\/?[A-Za-z_](?:[^>"']|"[^"]*"|'[^']*')*>|[^<]+/g;
  var token, position = 0, roots = 0;
  while ((token = tokens.exec(text))) {
    if (token.index !== position) return null;
    position = tokens.lastIndex;
    var value = token[0], parent = stack.length ? stack[stack.length - 1] : null;
    if (value.indexOf("<!--") === 0 || value.indexOf("<?") === 0) continue;
    if (value.charAt(0) !== "<" || value.indexOf("<![CDATA[") === 0) {
      if (value.indexOf("<![CDATA[") === 0) value = value.slice(9, -3);
      var count = characters(value);
      if (!parent) { if (value.trim()) return null; continue; }
      if (parent.forKey && count) transliterations[parent.forKey] = true;
      var line = parent.line;
      if (!line || !count) continue;
      if (parent.role.indexOf("x-translation") === 0) line.translation = true;
      else if (parent.role.indexOf("x-roman") === 0) line.romanization = true;
      else if (parent.background) line.background = true;
      else {
        line.characters += count;
        if (parent.word) {
          line.wordCharacters += count;
          line.segments[parent.word] = true;
        }
      }
      continue;
    }
    var tag = /^<(\/?)([\w:.-]+)([\s\S]*?)(\/?)>$/.exec(value);
    if (!tag) return null;
    if (tag[1]) {
      if (!parent || parent.name !== tag[2] || tag[3].trim() || tag[4]) return null;
      stack.pop();
      continue;
    }
    var attrs = attributes(tag[3]);
    if (!attrs) return null;
    if (!parent && (++roots !== 1 || tag[2] !== "tt")) return null;
    var role = String(attrs["ttm:role"] || attrs.role || (parent && parent.role) || "").toLowerCase();
    var begin = clock(attrs.begin), end = clock(attrs.end);
    var frame = {name: tag[2], role: role, body: tag[2] === "body" || !!(parent && parent.body),
      background: role === "x-bg" || !!(parent && parent.background),
      word: parent && parent.word, line: parent && parent.line,
      forKey: attrs["for"] || (parent && parent.forKey)};
    if (tag[2] === "p" && frame.body) {
      frame.line = {characters: 0, wordCharacters: 0, segments: Object.create(null),
        timed: begin >= 0 && end > begin, translation: false, romanization: false,
        agent: attrs["ttm:agent"] || attrs.agent, key: attrs["itunes:key"] || attrs.key};
      paragraphs.push(frame.line);
    }
    if (tag[2] === "span" && begin >= 0 && end > begin) frame.word = begin + ":" + end;
    if (!tag[4]) stack.push(frame);
  }
  if (position !== text.length || stack.length || roots !== 1) return null;
  for (var i = 0; i < paragraphs.length; i++) {
    var paragraph = paragraphs[i], segments = Object.keys(paragraph.segments).length;
    if (!paragraph.characters || (!paragraph.timed && !segments)) continue;
    result.lines++;
    result.characters += paragraph.characters;
    // One timed span covering an entire line is line-level, not word-level.
    if (segments > 1) result.wordCharacters += paragraph.wordCharacters;
    if (paragraph.translation) result.translation++;
    if (paragraph.romanization || transliterations[paragraph.key]) result.romanization++;
    if (paragraph.agent) result.agents[paragraph.agent] = true;
    if (paragraph.background) result.background = true;
  }
  return result;
}

function lrc(text) {
  var result = quality();
  String(text || "").split(/\r?\n/).forEach(function (line) {
    var match = /^((?:\[\d+:\d{1,2}(?:[.:]\d+)?\])+)(.*)$/.exec(line.trim());
    if (!match) return;
    var count = characters(match[2]);
    if (!count) return;
    result.lines++;
    result.characters += count;
  });
  return result;
}

function yrc(text) {
  var result = quality();
  String(text || "").split(/\r?\n/).forEach(function (line) {
    if (!/^\[\d+,\d+\]/.test(line.trim())) return;
    var words = /\((\d+),(\d+),\d+\)([^(]*)/g, match;
    var count = 0, timed = 0, segments = Object.create(null), background = false;
    while ((match = words.exec(line))) {
      var opens = match[3].indexOf("（") >= 0, closes = match[3].indexOf("）") >= 0;
      var size = characters(match[3]);
      if (background || opens) {
        if (size && Number(match[2]) > 0) result.background = true;
      } else {
        count += size;
        if (size && Number(match[2]) > 0) {
          timed += size;
          segments[match[1] + ":" + match[2]] = true;
        }
      }
      if (opens) background = true;
      if (closes) background = false;
    }
    if (count && timed) {
      result.characters += count;
      result.lines++;
      if (Object.keys(segments).length > 1) result.wordCharacters += timed;
    }
  });
  return result;
}

function inspect(payload) {
  if (!payload || !payload.assets) return null;
  var original, translation, romanization;
  payload.assets.forEach(function (asset) {
    if (!asset || typeof asset.text !== "string") return;
    if (asset.role === "translation") translation = asset;
    else if (asset.role === "romanization") romanization = asset;
    else if (!original) original = asset;
  });
  if (!original) return null;
  var result = original.format === "ttml" ? ttml(original.text)
    : original.format === "yrc" ? yrc(original.text)
    : original.format === "lrc" ? lrc(original.text) : null;
  if (!result || !result.characters || !result.lines) return null;
  if (translation) result.translation = Math.max(result.translation, lrc(translation.text).lines);
  if (romanization) result.romanization = Math.max(result.romanization, lrc(romanization.text).lines);
  return result;
}

function rank(value) {
  return [value.wordCharacters / value.characters,
    (value.background ? 1 : 0) + (Object.keys(value.agents).length > 1 ? 1 : 0),
    Math.min(1, value.translation / value.lines),
    Math.min(1, value.romanization / value.lines), value.characters];
}

function better(left, right) {
  var a = rank(left), b = rank(right);
  for (var i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) return a[i] > b[i];
  }
  return false;
}

function select(payloads) {
  var candidates = [], longest = 0;
  payloads.forEach(function (payload) {
    var value = inspect(payload);
    if (!value) return;
    candidates.push({payload: payload, quality: value});
    longest = Math.max(longest, value.characters);
  });
  var best = null;
  candidates.forEach(function (candidate) {
    if (candidate.quality.characters < longest * MIN_TEXT_COVERAGE) return;
    if (!best || better(candidate.quality, best.quality)) best = candidate;
  });
  return best ? best.payload : {assets: []};
}

function netease(body) {
  var candidates = [];
  ["yrc", "lrc"].forEach(function (format) {
    if (!body[format] || !body[format].lyric) return;
    var assets = [{format: format, role: "original", text: body[format].lyric}];
    var translated = format === "yrc" && body.ytlrc && body.ytlrc.lyric ? body.ytlrc : body.tlyric;
    var romanized = format === "yrc" && body.yromalrc && body.yromalrc.lyric ? body.yromalrc : body.romalrc;
    if (translated && translated.lyric) assets.push({format: "lrc", role: "translation", text: translated.lyric});
    if (romanized && romanized.lyric) assets.push({format: "lrc", role: "romanization", text: romanized.lyric});
    candidates.push({assets: assets});
  });
  return select(candidates);
}

function resolve(amll, provider) {
  function settle(promise) {
    return promise.then(function (payload) { return {ok: true, payload: payload}; },
      function (error) { return {ok: false, error: error}; });
  }
  return Promise.all([settle(amll), settle(provider)]).then(function (results) {
    if (!results[0].ok && !results[1].ok) throw results[1].error;
    return select([results[0].payload, results[1].payload]);
  });
}

module.exports = {select: select, netease: netease, resolve: resolve};
