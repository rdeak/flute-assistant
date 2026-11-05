#!/usr/bin/env python3
"""One HTML page for a recorded "Your turn" session: the run on a timeline.

    python3 tools/session-report.py sessions/2026-11-05_18-32-10

reads the folder's session.log and frames.csv (a debug build wrote them; the design is in
docs/superpowers/specs/2026-11-05-flute-listen-session-recorder-design.md) and writes
report.html next to them: the facts, the tune with the marks it earned, every rule in force,
and three lanes over one time axis, loudness (level and minLevel, the frames a note was
sounding shaded, onsets and verdicts), pitch (what was detected against the note waited on,
the sounding note as a step line) and the decision strip with what the child saw under it;
then the log's events and the whole frames table. Hover the picture for a frame's row.
Standard library only. The audio is not needed.
"""
import csv
import html
import json
import math
import os
import sys

HOP = 2048
NOTE_NAMES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]
DECISIONS = ["SILENT", "LOW_PROBABILITY", "UNSTEADY", "SHOWN", "IGNORED"]
MARK_GLYPH = {"right": "★", "retry": "☆", "wrong": "✕", "ignored": "·"}
MARK_WORD = {"right": "right, a star", "retry": "right after a stumble, a faint star", "wrong": "wrong", "ignored": "ignored"}


def note_name(midi):
    return "%s%d" % (NOTE_NAMES[midi % 12], midi // 12 - 1)


def parse_line(line):
    """An event name and its key=value fields, quotes removed (core/log/LogLine)."""
    tokens, token, quoted = [], [], False
    for c in line:
        if c == '"':
            quoted = not quoted
        elif c == " " and not quoted:
            if token:
                tokens.append("".join(token))
                token = []
        else:
            token.append(c)
    if token:
        tokens.append("".join(token))
    fields = {}
    for t in tokens[1:]:
        if "=" in t:
            k, v = t.split("=", 1)
            fields[k] = v
    return (tokens[0] if tokens else ""), fields


def load(folder):
    session = {"facts": {}, "rules": {}, "pulse": {}, "tune": {}, "audio": {}, "end": {}, "pin": None, "events": []}
    with open(os.path.join(folder, "session.log"), encoding="utf-8") as f:
        for raw in f:
            line = raw.rstrip("\n")
            if not line.strip():
                continue
            event, fields = parse_line(line)
            if event == "session.start":
                session["facts"] = fields
            elif event == "session.rules":
                session["rules"] = fields
            elif event == "session.pulse":
                session["pulse"] = fields
            elif event == "session.tune":
                session["tune"][int(fields["index"])] = fields
            elif event == "session.audio":
                session["audio"] = fields
            elif event == "session.end":
                session["end"] = fields
            elif event == "pin":
                session["pin"] = fields.get("marks", "")
            else:
                session["events"].append((event, fields, line))
    rows = []
    with open(os.path.join(folder, "frames.csv"), encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f):
            rows.append(r)
    session["rows"] = rows
    session["rate"] = int(session["audio"].get("rate", 48000))
    return session


def marks_of(text):
    marks = {}
    for entry in (text or "").split(","):
        if ":" in entry:
            i, m = entry.split(":", 1)
            marks[int(i)] = m
    return marks


def tune_items(session):
    items = []
    for i in sorted(session["tune"]):
        f = session["tune"][i]
        if "note" in f:
            items.append((i, "note", int(f["note"]), f.get("length", "")))
        elif "rest" in f:
            items.append((i, "rest", None, f.get("rest", "")))
        else:
            items.append((i, "breath", None, ""))
    return items


def fnum(text, default=None):
    try:
        return float(text)
    except (TypeError, ValueError):
        return default


CSS = """
:root {
  color-scheme: light;
  --surface: #fcfcfb; --page: #f9f9f7; --ink: #0b0b0b; --ink-2: #52514e; --muted: #898781;
  --grid: #e1e0d9; --axis: #c3c2b7; --border: rgba(11,11,11,0.10);
  --s1: #2a78d6; --s2: #eb6834; --s3: #1baf7a; --s4: #eda100; --s7: #6250d6;
  --good: #0ca30c; --warn: #fab219; --critical: #d03b3b; --band: #f0efec;
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    color-scheme: dark;
    --surface: #1a1a19; --page: #0d0d0d; --ink: #f0efec; --ink-2: #c3c2b7; --muted: #898781;
    --grid: #2c2c2a; --axis: #383835; --border: rgba(255,255,255,0.10);
    --s1: #3987e5; --s2: #d95926; --s3: #199e70; --s4: #c98500; --s7: #9085e9; --band: #383835;
  }
}
:root[data-theme="dark"] {
  color-scheme: dark;
  --surface: #1a1a19; --page: #0d0d0d; --ink: #f0efec; --ink-2: #c3c2b7; --muted: #898781;
  --grid: #2c2c2a; --axis: #383835; --border: rgba(255,255,255,0.10);
  --s1: #3987e5; --s2: #d95926; --s3: #199e70; --s4: #c98500; --s7: #9085e9; --band: #383835;
}
* { box-sizing: border-box; }
body { margin: 0; padding: 16px; background: var(--page); color: var(--ink); font: 14px/1.45 system-ui, -apple-system, "Segoe UI", sans-serif; }
h1 { font-size: 20px; margin: 0 0 4px; }
h2 { font-size: 15px; margin: 20px 0 6px; }
.sub { color: var(--ink-2); margin: 0 0 12px; }
.card { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; padding: 12px; margin-bottom: 12px; }
.facts { display: flex; flex-wrap: wrap; gap: 6px 18px; color: var(--ink-2); }
.facts b { color: var(--ink); font-weight: 600; }
.tune { display: flex; flex-wrap: wrap; gap: 8px; }
.step { border: 1px solid var(--border); border-radius: 6px; padding: 4px 8px; min-width: 56px; text-align: center; }
.step .m { display: block; font-size: 16px; }
.step .n { display: block; font-weight: 600; }
.step .l { display: block; color: var(--muted); font-size: 11px; }
.rules { display: flex; flex-wrap: wrap; gap: 4px 14px; color: var(--ink-2); font-size: 12px; }
.rules code { font: 12px ui-monospace, SFMono-Regular, Menlo, monospace; color: var(--ink); }
.scroll { overflow-x: auto; position: relative; }
svg { display: block; }
.legend { display: flex; flex-wrap: wrap; gap: 4px 16px; color: var(--ink-2); font-size: 12px; margin: 6px 0 0; }
.legend i { display: inline-block; width: 14px; height: 3px; vertical-align: middle; margin-right: 6px; border-radius: 2px; }
.legend i.dot { width: 9px; height: 9px; border-radius: 50%; }
.legend i.wash { height: 10px; opacity: .35; }
.legend i.sw { height: 10px; }
.tip { position: absolute; pointer-events: none; display: none; background: var(--surface); border: 1px solid var(--border); border-radius: 6px; padding: 6px 8px; font: 12px/1.4 ui-monospace, SFMono-Regular, Menlo, monospace; color: var(--ink); box-shadow: 0 2px 8px rgba(0,0,0,.15); max-width: 360px; white-space: pre; z-index: 2; }
.events { font: 12px/1.5 ui-monospace, SFMono-Regular, Menlo, monospace; color: var(--ink-2); margin: 0; padding: 0; list-style: none; }
.events li b { color: var(--ink); font-weight: 600; }
.events li .t { color: var(--muted); display: inline-block; width: 64px; }
.events li .lt { color: var(--muted); display: inline-block; width: 92px; }
details summary { cursor: pointer; color: var(--ink-2); }
table { border-collapse: collapse; font: 12px ui-monospace, SFMono-Regular, Menlo, monospace; font-variant-numeric: tabular-nums; margin-top: 8px; }
th, td { padding: 2px 8px; text-align: right; border-bottom: 1px solid var(--grid); white-space: nowrap; }
th { color: var(--muted); font-weight: 500; position: sticky; top: 0; background: var(--surface); }
td.l, th.l { text-align: left; }
.note { color: var(--ink-2); font-size: 12px; }
"""

JS = """
(function () {
  var data = __DATA__, rows = data.rows, px = data.px, left = data.left, rate = data.rate, hop = data.hop;
  var svg = document.getElementById('timeline'), tip = document.getElementById('tip');
  var cursor = document.getElementById('cursor');
  var box = svg.parentNode;
  function at(x) {
    var sec = (x - left) / px, frame = Math.round(sec * rate / hop);
    if (frame < 1) frame = 1; if (frame > rows.length) frame = rows.length;
    return frame;
  }
  svg.addEventListener('mousemove', function (e) {
    var r = svg.getBoundingClientRect(), x = e.clientX - r.left;
    if (x < left || !rows.length) { tip.style.display = 'none'; cursor.setAttribute('opacity', 0); return; }
    var frame = at(x), row = rows[frame - 1], fx = left + frame * hop / rate * px;
    cursor.setAttribute('x1', fx); cursor.setAttribute('x2', fx); cursor.setAttribute('opacity', 1);
    var lines = [];
    for (var k in row) if (row[k] !== '') lines.push(k + ' = ' + row[k]);
    tip.textContent = lines.join('\\n');
    tip.style.display = 'block';
    var bx = e.clientX - box.getBoundingClientRect().left + box.scrollLeft + 14, by = e.clientY - box.getBoundingClientRect().top + 14;
    if (bx + 380 > box.scrollLeft + box.clientWidth) bx -= 400;
    tip.style.left = bx + 'px'; tip.style.top = by + 'px';
  });
  svg.addEventListener('mouseleave', function () { tip.style.display = 'none'; cursor.setAttribute('opacity', 0); });
})();
"""


def build(session, folder):
    rows = session["rows"]
    rate = session["rate"]
    n = len(rows)
    duration = max(n * HOP / rate, 1.0)
    px = max(60.0, min(400.0, 1100.0 / duration))
    left, right = 64, 24
    width = int(left + duration * px + right)
    gate = fnum(session["rules"].get("gate"), 0.85)
    tune = tune_items(session)
    marks = marks_of(session["end"].get("marks"))

    def x_of(frame):
        return left + frame * HOP / rate * px

    def x_sec(sec):
        return left + sec * px

    out = []
    w = out.append

    # ---- lane A: loudness -------------------------------------------------------------------
    a_top, a_h = 24, 170
    def ya(level):
        return a_top + a_h - level * a_h
    # ---- lane B: pitch ----------------------------------------------------------------------
    b_top = a_top + a_h + 56
    b_h = 190
    midis = [int(r["sounding"]) for r in rows if r["sounding"]]
    midis += [m for (_, kind, m, _) in tune if m is not None]
    freqs = [fnum(r["f"]) for r in rows if r["f"]]
    for f in freqs:
        if f and f > 0:
            midis.append(int(round(69 + 12 * math.log2(f / 440.0))))
    lo = (min(midis) - 3) if midis else 57
    hi = (max(midis) + 3) if midis else 84
    if hi - lo < 10:
        hi = lo + 10
    def yb(midi):
        return b_top + b_h - (midi - lo) / (hi - lo) * b_h
    # ---- lane C: decisions and the screen ----------------------------------------------------
    c_top = b_top + b_h + 52
    strip_h = 14
    row_h = 18
    c_h = strip_h + 4 + 3 * row_h
    height = c_top + c_h + 40

    w('<svg id="timeline" width="%d" height="%d" viewBox="0 0 %d %d" role="img" aria-label="The run on a timeline">' % (width, height, width, height))
    # time axis ticks (every second, labelled every 1, 2 or 5 s)
    step = 1 if px >= 50 else (2 if px >= 25 else 5)
    for sec in range(0, int(math.ceil(duration)) + 1):
        x = x_sec(sec)
        w('<line x1="%.1f" y1="%d" x2="%.1f" y2="%d" stroke="var(--grid)" stroke-width="1"/>' % (x, a_top, x, c_top + c_h))
        if sec % step == 0:
            w('<text x="%.1f" y="%d" fill="var(--muted)" font-size="11" text-anchor="middle">%ds</text>' % (x, c_top + c_h + 16, sec))

    # lane A
    w('<text x="%d" y="%d" fill="var(--ink-2)" font-size="12" font-weight="600">Loudness</text>' % (left, a_top - 8))
    for v in (0.0, 0.25, 0.5, 0.75, 1.0):
        w('<line x1="%d" y1="%.1f" x2="%d" y2="%.1f" stroke="var(--grid)" stroke-width="1"/>' % (left, ya(v), width - right, ya(v)))
        w('<text x="%d" y="%.1f" fill="var(--muted)" font-size="11" text-anchor="end">%.2f</text>' % (left - 6, ya(v) + 4, v))
    # sounding shade
    start = None
    for i, r in enumerate(rows):
        s = r["sounding"] != ""
        if s and start is None:
            start = i
        if (not s or i == n - 1) and start is not None:
            end = i if not s else i + 1
            w('<rect x="%.1f" y="%d" width="%.1f" height="%d" fill="var(--s1)" opacity="0.10"/>' % (x_of(start), a_top, x_of(end) - x_of(start), a_h))
            start = None
    # floor, minLevel, level
    def path(key, color, dash=""):
        pts = []
        for i, r in enumerate(rows):
            v = fnum(r.get(key))
            if v is None:
                continue
            pts.append("%.1f,%.1f" % (x_of(i + 1), ya(max(0.0, min(1.0, v)))))
        if pts:
            w('<polyline points="%s" fill="none" stroke="%s" stroke-width="%s" stroke-linejoin="round" stroke-linecap="round"%s/>' % (" ".join(pts), color, "1" if dash else "2", dash))
    path("floor", "var(--muted)", ' stroke-dasharray="3 3" opacity="0.8"')
    path("minLevel", "var(--s2)")
    path("level", "var(--s1)")
    # onsets and verdicts
    for i, r in enumerate(rows):
        if r["onset"]:
            x = x_of(i + 1)
            y = ya(max(0.0, min(1.0, fnum(r["level"], 0.0))))
            fill = "var(--s1)" if r["onset"] == "fresh" else "var(--surface)"
            w('<circle cx="%.1f" cy="%.1f" r="6" fill="var(--surface)"/><circle cx="%.1f" cy="%.1f" r="4.5" fill="%s" stroke="var(--s1)" stroke-width="2"/>' % (x, y, x, y, fill))
            v = r["verdict"]
            if v:
                color = {"right": "var(--good)", "retry": "var(--warn)", "wrong": "var(--critical)"}.get(v, "var(--muted)")
                w('<text x="%.1f" y="%.1f" fill="%s" font-size="15" text-anchor="middle"><title>%s</title>%s</text>' % (x, y - 12, color, html.escape(MARK_WORD.get(v, v)), MARK_GLYPH.get(v, v)))

    # lane B
    w('<text x="%d" y="%d" fill="var(--ink-2)" font-size="12" font-weight="600">Pitch</text>' % (left, b_top - 8))
    tune_midis = {t[2] for t in tune if t[2] is not None}
    for m in range(lo, hi + 1):
        if hi - lo <= 14 or m % 12 == 0 or m in tune_midis:
            w('<line x1="%d" y1="%.1f" x2="%d" y2="%.1f" stroke="var(--grid)" stroke-width="1"/>' % (left, yb(m), width - right, yb(m)))
            w('<text x="%d" y="%.1f" fill="var(--muted)" font-size="11" text-anchor="end">%s</text>' % (left - 6, yb(m) + 4, note_name(m)))
    # expected band per stretch of one waited index
    start, cur = None, None
    for i, r in enumerate(rows):
        key = (r["waited"], r["expected"]) if r["expected"] else None
        if key != cur:
            if cur is not None and cur[1]:
                m = int(cur[1])
                w('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" fill="var(--band)" opacity="0.9"><title>waiting on step %s, %s</title></rect>' % (x_of(start), yb(m + 0.5), x_of(i) - x_of(start), yb(m - 0.5) - yb(m + 0.5), cur[0], note_name(m)))
            start, cur = i, key
    if cur is not None and cur[1]:
        m = int(cur[1])
        w('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" fill="var(--band)" opacity="0.9"><title>waiting on step %s, %s</title></rect>' % (x_of(start), yb(m + 0.5), x_of(n) - x_of(start), yb(m - 0.5) - yb(m + 0.5), cur[0], note_name(m)))
    # detected pitch dots
    for i, r in enumerate(rows):
        f = fnum(r["f"])
        if not f or f <= 0:
            continue
        midi = 69 + 12 * math.log2(f / 440.0)
        if midi < lo - 1 or midi > hi + 1:
            continue
        p = fnum(r["p"], 0.0)
        w('<circle cx="%.1f" cy="%.1f" r="2.2" fill="var(--s1)" opacity="%s"/>' % (x_of(i + 1), yb(midi), "1" if p >= gate else "0.3"))
    # sounding note step line
    pts = []
    for i, r in enumerate(rows):
        if r["sounding"]:
            m = int(r["sounding"])
            pts.append("%.1f,%.1f" % (x_of(i), yb(m)))
            pts.append("%.1f,%.1f" % (x_of(i + 1), yb(m)))
        elif pts:
            w('<polyline points="%s" fill="none" stroke="var(--s3)" stroke-width="2"/>' % " ".join(pts))
            pts = []
    if pts:
        w('<polyline points="%s" fill="none" stroke="var(--s3)" stroke-width="2"/>' % " ".join(pts))

    # lane C
    w('<text x="%d" y="%d" fill="var(--ink-2)" font-size="12" font-weight="600">Decision and the screen</text>' % (left, c_top - 8))
    colors = {"SILENT": "var(--grid)", "LOW_PROBABILITY": "var(--s2)", "UNSTEADY": "var(--s4)", "SHOWN": "var(--s1)", "IGNORED": "var(--s7)"}
    start, cur = None, None
    def strip(i0, i1, d):
        w('<rect x="%.1f" y="%d" width="%.1f" height="%d" fill="%s"><title>%s</title></rect>' % (x_of(i0), c_top, max(0.5, x_of(i1) - x_of(i0) - 0.5), strip_h, colors.get(d, "var(--muted)"), d))
    for i, r in enumerate(rows):
        d = r["decision"]
        if d != cur:
            if cur is not None:
                strip(start, i, cur)
            start, cur = i, d
    if cur is not None:
        strip(start, n, cur)
    labels = [("heardNow", "heard now", lambda r: note_name(int(r["heardNow"])) if r["heardNow"] else ""),
              ("advice", "advice", lambda r: r["advice"].replace("_", " ").lower() if r["advice"] else ""),
              ("finishing", "finishing", lambda r: "finishing" if r["finishing"] == "true" else "")]
    for k, (key, title, text_of) in enumerate(labels):
        y = c_top + strip_h + 4 + k * row_h
        w('<text x="%d" y="%.1f" fill="var(--muted)" font-size="11" text-anchor="end">%s</text>' % (left - 6, y + 13, title))
        start, cur = None, None
        def seg(i0, i1, label):
            if not label:
                return
            x0, x1 = x_of(i0), x_of(i1)
            w('<rect x="%.1f" y="%.1f" width="%.1f" height="%d" fill="var(--band)" rx="3"/>' % (x0, y + 2, max(1.0, x1 - x0 - 1), row_h - 4))
            if x1 - x0 > 7 * len(label) + 6:
                w('<text x="%.1f" y="%.1f" fill="var(--ink-2)" font-size="11">%s</text>' % (x0 + 4, y + 13, html.escape(label)))
        for i, r in enumerate(rows):
            t = text_of(r)
            if t != cur:
                if cur is not None:
                    seg(start, i, cur)
                start, cur = i, t
        if cur is not None:
            seg(start, n, cur)

    # the cursor the hover moves
    w('<line id="cursor" x1="0" y1="%d" x2="0" y2="%d" stroke="var(--ink-2)" stroke-width="1" opacity="0"/>' % (a_top, c_top + c_h))
    w("</svg>")
    svg = "\n".join(out)

    # ---- the page ---------------------------------------------------------------------------
    facts = session["facts"]
    audio = session["audio"]
    page = []
    p = page.append
    p("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
    p("<title>Session %s</title><style>%s</style></head><body>" % (html.escape(os.path.basename(folder)), CSS))
    p("<h1>Your turn session %s</h1>" % html.escape(os.path.basename(folder)))
    done = "the tune was finished" if "flute.listen_done" in [e[0] for e in session["events"]] else "the run was stopped"
    p("<p class=\"sub\">%d frames, %.1f s at %s Hz from the %s microphone; %s.</p>" % (n, n * HOP / rate, audio.get("rate", "?"), html.escape(audio.get("source", "?")), done))
    p("<div class=\"card\"><div class=\"facts\">")
    for k in ("at", "app", "code", "device", "sdk", "floor"):
        if k in facts:
            p("<span>%s <b>%s</b></span>" % (k, html.escape(facts[k])))
    if session["pulse"]:
        p("<span>pulse <b>%s at %s</b></span>" % (html.escape(session["pulse"].get("time", "")), html.escape(session["pulse"].get("bpm", ""))))
    if session["pin"] is not None:
        p("<span>pin <b>%s</b></span>" % html.escape(session["pin"]))
    p("</div></div>")
    p("<h2>The tune and what it earned</h2><div class=\"card\"><div class=\"tune\">")
    for (i, kind, midi, length) in tune:
        m = marks.get(i)
        glyph = {"FIRST_TRY": "★", "RETRY": "☆"}.get(m, "·" if kind == "note" else "")
        color = {"FIRST_TRY": "var(--good)", "RETRY": "var(--warn)"}.get(m, "var(--muted)")
        label = note_name(midi) if kind == "note" else ("rest" if kind == "rest" else "breath")
        p("<div class=\"step\"><span class=\"m\" style=\"color:%s\" title=\"%s\">%s</span><span class=\"n\">%s</span><span class=\"l\">%d %s</span></div>" % (color, html.escape(m or "no mark"), glyph, html.escape(label), i, html.escape(length.lower())))
    p("</div><p class=\"note\">★ a star, first try; ☆ a faint star, after a stumble (a forgiven repeat too); · no mark.</p></div>")
    p("<h2>Timeline</h2><div class=\"card\"><div class=\"scroll\">%s<div id=\"tip\" class=\"tip\"></div></div>" % svg)
    p("<div class=\"legend\"><span><i style=\"background:var(--s1)\"></i>level</span><span><i style=\"background:var(--s2)\"></i>minLevel, the quietest stretch of the hop</span><span><i style=\"background:var(--muted)\"></i>floor the tracker holds</span><span><i class=\"wash\" style=\"background:var(--s1)\"></i>a note sounding</span><span><i class=\"dot\" style=\"background:var(--s1)\"></i>fresh onset</span><span><i class=\"dot\" style=\"border:2px solid var(--s1)\"></i>onset by a step</span><span>★ right &nbsp;☆ retry &nbsp;✕ wrong &nbsp;· ignored</span></div>")
    p("<div class=\"legend\"><span><i class=\"dot\" style=\"background:var(--s1)\"></i>detected pitch (faint under the gate %.2f)</span><span><i style=\"background:var(--s3)\"></i>sounding note</span><span><i class=\"sw\" style=\"background:var(--band)\"></i>the note waited on</span></div>" % gate)
    p("<div class=\"legend\">" + "".join("<span><i class=\"sw\" style=\"background:%s\"></i>%s</span>" % (colors[d], d.replace("_", " ").lower()) for d in DECISIONS) + "</div>")
    p("</div>")
    p("<h2>Rules in force</h2><div class=\"card\"><div class=\"rules\">" + "".join("<span>%s <code>%s</code></span>" % (html.escape(k), html.escape(v)) for k, v in session["rules"].items()) + "</div></div>")
    p("<h2>Events</h2><div class=\"card\"><ul class=\"events\">")
    for (event, fields, line) in session["events"]:
        frame = fnum(fields.get("frame"), None)
        t = ("%.2fs" % (frame * HOP / rate)) if frame is not None else ""
        where = "%s/%s" % (fields.get("level", ""), fields.get("tag", "")) if "tag" in fields else ""
        rest = " ".join("%s=%s" % (k, v) for k, v in fields.items() if k not in ("frame", "tag", "level"))
        p("<li><span class=\"t\">%s</span><span class=\"lt\">%s</span><b>%s</b> %s</li>" % (t, html.escape(where), html.escape(event), html.escape(rest)))
    p("</ul></div>")
    if rows:
        p("<h2>Frames</h2><div class=\"card\"><details><summary>The whole table, %d rows</summary><div style=\"overflow:auto;max-height:480px\"><table><thead><tr>" % n)
        keys = list(rows[0].keys())
        p("".join("<th class=\"%s\">%s</th>" % ("l" if k in ("decision", "onset", "verdict", "advice", "hearing") else "", html.escape(k)) for k in keys))
        p("</tr></thead><tbody>")
        for r in rows:
            p("<tr>" + "".join("<td class=\"%s\">%s</td>" % ("l" if k in ("decision", "onset", "verdict", "advice", "hearing") else "", html.escape(r[k])) for k in keys) + "</tr>")
        p("</tbody></table></div></details></div>")
    # One object, substituted once, so no cell can ever be mistaken for a placeholder; "</" is
    # escaped so no cell can close the script either.
    data = json.dumps({"rows": rows, "px": round(px, 3), "left": left, "rate": rate, "hop": HOP}).replace("</", "<\\/")
    p("<script>%s</script></body></html>" % JS.replace("__DATA__", data, 1))
    return "\n".join(page)


def main(argv):
    if len(argv) != 2 or not os.path.isdir(argv[1]):
        print(__doc__.strip().splitlines()[0])
        print("usage: python3 tools/session-report.py <session folder>")
        return 2
    folder = os.path.normpath(argv[1])
    session = load(folder)
    out = os.path.join(folder, "report.html")
    with open(out, "w", encoding="utf-8") as f:
        f.write(build(session, folder))
    print(out)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
