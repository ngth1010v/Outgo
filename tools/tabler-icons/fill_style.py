"""Redraws a Tabler outline icon in the style of Phosphor Fill (the app's UI and built-in icons).

The outline's strokes are merged and every area they enclose is filled. Then each stroke's centre
line is cut back out as a GAP-wide gap, but only where it lies more than a stroke width inside the
shape: the outline keeps its full width, and inner details (a car's windows, a clock's hands) show
as gaps, the way Phosphor Fill draws them. A small closed inner shape (a keyhole, a dice pip) is cut
out whole. The result is rasterized at SCALE px per unit and traced back to curves with potrace,
which is far more robust here than vector boolean ops.

Needs: pip install skia-python potracer numpy
"""
import re

import potrace
import skia

STROKE = 2.0      # Tabler's stroke width, in its 24-unit grid
GAP = 1.5         # Phosphor Fill's cut-out width (16 of its 256 units)
SMALL_HOLE = 7.0  # a closed inner shape up to this area is cut out whole
SCALE = 20        # raster pixels per unit

_TOKEN = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]|-?(?:\d+\.?\d*|\.\d+)(?:e-?\d+)?")


def filled_path_data(svg_path_ds, scale=1.0):
    """The `d` of one even-odd path drawing the icon whose outline paths are svg_path_ds, shrunk
    by scale around the 24-unit grid's centre."""
    return _trace(_filled_mask([s for d in svg_path_ds for s in _subpaths(d)]), scale)


def _subpaths(d):
    """One skia.Path per M of an SVG path's d."""
    toks = _TOKEN.findall(d)
    out, p, i, cmd = [], None, 0, None
    x = y = sx = sy = 0.0
    lc = None  # last control point, for S/T

    def num():
        nonlocal i
        i += 1
        return float(toks[i - 1])

    while i < len(toks):
        if toks[i].isalpha():
            cmd = toks[i]
            i += 1
        rel, c = cmd.islower(), cmd.upper()
        ox, oy = (x, y) if rel else (0.0, 0.0)
        if c == "Z":
            p.close()
            x, y, lc = sx, sy, None
        elif c == "M":
            x, y = num() + ox, num() + oy
            p = skia.Path()
            out.append(p)
            p.moveTo(x, y)
            sx, sy, lc = x, y, None
            cmd = "l" if rel else "L"  # further pairs are line-tos
        elif c == "L":
            x, y = num() + ox, num() + oy
            p.lineTo(x, y)
            lc = None
        elif c == "H":
            x = num() + ox
            p.lineTo(x, y)
            lc = None
        elif c == "V":
            y = num() + oy
            p.lineTo(x, y)
            lc = None
        elif c in "CS":
            c1 = (2 * x - lc[0], 2 * y - lc[1]) if c == "S" and lc else (x, y)
            if c == "C":
                c1 = (num() + ox, num() + oy)
            c2 = (num() + ox, num() + oy)
            x, y = num() + ox, num() + oy
            p.cubicTo(*c1, *c2, x, y)
            lc = c2
        elif c in "QT":
            q = (2 * x - lc[0], 2 * y - lc[1]) if c == "T" and lc else (x, y)
            if c == "Q":
                q = (num() + ox, num() + oy)
            x, y = num() + ox, num() + oy
            p.quadTo(*q, x, y)
            lc = q
        elif c == "A":
            rx, ry, rot, large, sweep = num(), num(), num(), num(), num()
            x, y = num() + ox, num() + oy
            p.arcTo(rx, ry, rot, skia.Path.kLarge_ArcSize if large else skia.Path.kSmall_ArcSize,
                    skia.PathDirection.kCW if sweep else skia.PathDirection.kCCW, x, y)
            lc = None
        else:
            raise ValueError(f"unsupported path command {cmd!r}")
    return out


def _filled_mask(subs):
    stroke = skia.Paint(Style=skia.Paint.kStroke_Style, StrokeWidth=STROKE,
                        StrokeCap=skia.Paint.kRound_Cap, StrokeJoin=skia.Paint.kRound_Join)
    shape = skia.Path()
    for s in subs:
        line = skia.Path()
        stroke.getFillPath(s, line, None, 16)
        shape = skia.Op(shape, line, skia.PathOp.kUnion_PathOp)
    # Fill what the strokes enclose: keep the contours no other contour encloses an odd number of times.
    solid = skia.Path()
    for c, depth in _depths(shape):
        if depth % 2 == 0:
            solid = skia.Op(solid, _winding(c), skia.PathOp.kUnion_PathOp)

    black = skia.Paint(AntiAlias=True, Color=skia.ColorBLACK)
    m_solid = _mask(lambda cv: cv.drawPath(solid, black))
    # Within one stroke width of the outside nothing is cut, so the outline keeps its width.
    band = skia.Paint(AntiAlias=True, Style=skia.Paint.kStroke_Style, StrokeWidth=2 * STROKE,
                      StrokeJoin=skia.Paint.kRound_Join, Color=skia.ColorBLACK)
    m_core = m_solid & ~_mask(lambda cv: cv.drawPath(solid, band))

    gap = skia.Paint(AntiAlias=True, Style=skia.Paint.kStroke_Style, StrokeWidth=GAP,
                     StrokeCap=skia.Paint.kRound_Cap, StrokeJoin=skia.Paint.kRound_Join, Color=skia.ColorBLACK)

    def cuts(cv):
        for s in subs:
            cv.drawPath(s, gap)
            if _closes(s) and abs(_signed_area(s)) <= SMALL_HOLE:
                cv.drawPath(_winding(s), black)
    return m_solid & ~(_mask(cuts) & m_core)


def _mask(draw):
    """Bool array of what draw(canvas) paints, at SCALE px per unit."""
    surface = skia.Surface(24 * SCALE, 24 * SCALE)
    canvas = surface.getCanvas()
    canvas.clear(skia.ColorTRANSPARENT)
    canvas.scale(SCALE, SCALE)
    draw(canvas)
    return surface.makeImageSnapshot().toarray()[:, :, 3] >= 128


def _trace(mask, scale):
    """mask traced to an SVG path d in icon units, scaled around (12, 12); specks under 0.3 square
    units are dropped."""
    curves = potrace.Bitmap(~mask).trace(  # potracer traces the False pixels
        turdsize=int(0.3 * SCALE * SCALE), alphamax=1.0, opticurve=True, opttolerance=0.2)
    at = lambda v: 12 + (v / SCALE - 12) * scale
    pt = lambda p: f"{_fmt(at(p.x))},{_fmt(at(p.y))}"
    parts = []
    for curve in curves:
        parts.append("M" + pt(curve.start_point))
        for seg in curve.segments:
            if seg.is_corner:
                parts.append("L" + pt(seg.c) + "L" + pt(seg.end_point))
            else:
                parts.append("C" + pt(seg.c1) + " " + pt(seg.c2) + " " + pt(seg.end_point))
        parts.append("Z")
    return "".join(parts)


def _fmt(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def _verbs(path):
    it = skia.Path.Iter(path, False)
    while True:
        verb, pts = it.next()
        if verb == skia.Path.kDone_Verb:
            return
        yield verb, pts, (it.conicWeight() if verb == skia.Path.kConic_Verb else 1.0)


def _contours(path):
    """Each contour of path as its own skia.Path."""
    out, cur = [], None
    for verb, pts, w in _verbs(path):
        if verb == skia.Path.kMove_Verb:
            cur = skia.Path()
            out.append(cur)
            cur.moveTo(pts[0])
        elif verb == skia.Path.kLine_Verb:
            cur.lineTo(pts[1])
        elif verb == skia.Path.kQuad_Verb:
            cur.quadTo(pts[1], pts[2])
        elif verb == skia.Path.kConic_Verb:
            cur.conicTo(pts[1], pts[2], w)
        elif verb == skia.Path.kCubic_Verb:
            cur.cubicTo(pts[1], pts[2], pts[3])
        elif verb == skia.Path.kClose_Verb:
            cur.close()
    return out


def _polyline(path):
    """The first contour of path flattened to points."""
    out = []
    for verb, pts, w in _verbs(path):
        P = [(p.x(), p.y()) for p in pts]
        if verb == skia.Path.kMove_Verb:
            if out:
                break
            out.append(P[0])
        elif verb == skia.Path.kLine_Verb:
            out.append(P[1])
        elif verb == skia.Path.kCubic_Verb:
            for k in range(1, 17):
                t = k / 16
                a, b, c, d = (1 - t) ** 3, 3 * t * (1 - t) ** 2, 3 * t * t * (1 - t), t ** 3
                out.append(tuple(a * P[0][i] + b * P[1][i] + c * P[2][i] + d * P[3][i] for i in (0, 1)))
        elif verb in (skia.Path.kQuad_Verb, skia.Path.kConic_Verb):
            for k in range(1, 17):
                t = k / 16
                a, b, c = (1 - t) ** 2, 2 * t * (1 - t) * w, t * t
                out.append(tuple((a * P[0][i] + b * P[1][i] + c * P[2][i]) / (a + b + c) for i in (0, 1)))
    return out


def _signed_area(path):
    pts = _polyline(path)
    return sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(pts, pts[1:] + pts[:1])) / 2


def _depths(path):
    """Each contour of path with how many of its other contours enclose it."""
    cs = _contours(path)
    filled = [_winding(c) for c in cs]
    out = []
    for i, c in enumerate(cs):
        probe = _inside_point(filled[i], _polyline(c))
        out.append((c, sum(1 for j, o in enumerate(filled) if j != i and o.contains(*probe))))
    return out


def _inside_point(shape, pts):
    """A point inside shape, just off its outline (contours never cross, so its depth is the outline's)."""
    for (x0, y0), (x1, y1) in zip(pts, pts[1:] + pts[:1]):
        n = ((x1 - x0) ** 2 + (y1 - y0) ** 2) ** 0.5
        if n < 1e-6:
            continue
        for sign in (1, -1):
            px, py = (x0 + x1) / 2 - sign * (y1 - y0) / n * 0.01, (y0 + y1) / 2 + sign * (x1 - x0) / n * 0.01
            if shape.contains(px, py):
                return px, py
    return pts[0]


def _closes(s):
    """Whether a one-contour path ends where it starts (Tabler often omits the z)."""
    if s.isLastContourClosed():
        return True
    pts = s.getPoints(s.countPoints())
    return len(pts) > 2 and abs(pts[0].x() - pts[-1].x()) < 1e-3 and abs(pts[0].y() - pts[-1].y()) < 1e-3


def _winding(c):
    q = skia.Path(c)
    q.setFillType(skia.PathFillType.kWinding)
    return q
