import re, sys, os
src = sys.argv[1]; out = sys.argv[2]
icons = sys.argv[3].split(',')
def camel(n): return ''.join(p.capitalize() for p in n.split('-'))
def el_to_path(tag, attrs):
    a = dict(re.findall(r'([\w-]+)="([^"]*)"', attrs))
    f = lambda k, d=0: float(a.get(k, d))
    if tag == 'path': return a['d']
    if tag == 'circle':
        cx, cy, r = f('cx'), f('cy'), f('r')
        return f"M{cx-r} {cy}a{r} {r} 0 1 0 {2*r} 0a{r} {r} 0 1 0 {-2*r} 0"
    if tag == 'ellipse':
        cx, cy, rx, ry = f('cx'), f('cy'), f('rx'), f('ry')
        return f"M{cx-rx} {cy}a{rx} {ry} 0 1 0 {2*rx} 0a{rx} {ry} 0 1 0 {-2*rx} 0"
    if tag == 'line':
        return f"M{f('x1')} {f('y1')}L{f('x2')} {f('y2')}"
    if tag in ('polyline', 'polygon'):
        pts = a['points'].replace(',', ' ').split()
        pairs = [f"{pts[i]} {pts[i+1]}" for i in range(0, len(pts), 2)]
        return 'M' + 'L'.join(pairs) + ('Z' if tag == 'polygon' else '')
    if tag == 'rect':
        x, y, w, h = f('x'), f('y'), f('width'), f('height')
        rx = float(a.get('rx', a.get('ry', 0)))
        if rx == 0: return f"M{x} {y}h{w}v{h}h{-w}Z"
        r = min(rx, w/2, h/2)
        return (f"M{x+r} {y}h{w-2*r}a{r} {r} 0 0 1 {r} {r}v{h-2*r}a{r} {r} 0 0 1 {-r} {r}"
                f"h{-(w-2*r)}a{r} {r} 0 0 1 {-r} {-r}v{-(h-2*r)}a{r} {r} 0 0 1 {r} {-r}Z")
    raise Exception(tag)
lines = []
for name in icons:
    svg = open(os.path.join(src, name + '.svg')).read()
    paths = [el_to_path(t, at) for t, at in re.findall(r'<(path|circle|ellipse|line|polyline|polygon|rect)\s([^>]*)/?>', svg)]
    joined = ', '.join('"%s"' % p for p in paths)
    lines.append(f'    /** Lucide `{name}`. */\n    public val {camel(name)}: LucideGlyph = LucideGlyph("{name}", listOf({joined}))')
hdr = '''// GENERATED from lucide-static (ISC license, https://lucide.dev) by docs/tools/gen_icons.py — do not edit by hand.
package rocks.lumina.app.core.designsystem.icon

/** Raw Lucide glyph data (24x24 viewBox path strings). Render via [LuminaIcons]. */
public object LucideGlyphs {
'''
open(out, 'w').write(hdr + '\n\n'.join(lines) + '\n}\n')
