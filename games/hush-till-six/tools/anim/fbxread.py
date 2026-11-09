"""Минимальный разбор двоичного FBX (7.x): дерево узлов и свойства — для переноса мокапа (tools/anim/mocap.py). Только стандартная библиотека и numpy."""
import struct, zlib, sys
import numpy as np

class Node:
    __slots__ = ('name', 'props', 'kids')
    def __init__(s, name, props, kids): s.name, s.props, s.kids = name, props, kids
    def find(s, name): return next((k for k in s.kids if k.name == name), None)
    def all(s, name): return [k for k in s.kids if k.name == name]
    def __repr__(s): return f'Node({s.name}, {s.props[:3]}, {len(s.kids)} kids)'

def _arr(buf, o, code):
    n, enc, clen = struct.unpack_from('<III', buf, o); o += 12
    raw = buf[o:o + clen]; o += clen
    if enc == 1: raw = zlib.decompress(raw)
    dt = {'f': '<f4', 'd': '<f8', 'l': '<i8', 'i': '<i4', 'b': '<u1'}[code]
    return np.frombuffer(raw, dtype=dt, count=n), o

def _prop(buf, o):
    c = chr(buf[o]); o += 1
    if c == 'Y': return struct.unpack_from('<h', buf, o)[0], o + 2
    if c == 'C': return bool(buf[o]), o + 1
    if c == 'I': return struct.unpack_from('<i', buf, o)[0], o + 4
    if c == 'F': return struct.unpack_from('<f', buf, o)[0], o + 4
    if c == 'D': return struct.unpack_from('<d', buf, o)[0], o + 8
    if c == 'L': return struct.unpack_from('<q', buf, o)[0], o + 8
    if c in 'fdlib': return _arr(buf, o, c)
    if c in 'SR':
        n = struct.unpack_from('<I', buf, o)[0]; o += 4; v = buf[o:o + n]; o += n
        return (v.decode('utf-8', 'replace') if c == 'S' else v), o
    raise ValueError('prop type ' + c)

def _node(buf, o, v64):
    if v64: end, nprop, plen = struct.unpack_from('<QQQ', buf, o); o += 24
    else: end, nprop, plen = struct.unpack_from('<III', buf, o); o += 12
    nl = buf[o]; o += 1
    if end == 0: return None, o
    name = buf[o:o + nl].decode('ascii', 'replace'); o += nl
    props = []
    for _ in range(nprop): v, o = _prop(buf, o); props.append(v)
    kids = []
    null = 25 if v64 else 13
    while o < end - null or (o < end and buf[o:end] != b'\x00' * (end - o)):
        if end - o == null and buf[o:end] == b'\x00' * null: o = end; break
        k, o = _node(buf, o, v64)
        if k is None: break
        kids.append(k)
    return Node(name, props, kids), end

def read(path):
    buf = open(path, 'rb').read()
    assert buf[:20] == b'Kaydara FBX Binary  ', 'not binary FBX'
    ver = struct.unpack_from('<I', buf, 23)[0]; v64 = ver >= 7500; o = 27
    top = []
    while o < len(buf):
        k, o2 = _node(buf, o, v64)
        if k is None: break
        top.append(k); o = o2
    return Node('root', [ver], top)

def props70(node):
    out = {}
    p = node.find('Properties70')
    if p:
        for P in p.all('P'): out[P.props[0]] = P.props[4:] if len(P.props) > 4 else []
    return out

if __name__ == '__main__':
    r = read(sys.argv[1]); print('version', r.props[0])
    for k in r.kids: print(k.name, len(k.kids))
    objs = r.find('Objects')
    from collections import Counter
    print(Counter((k.name, k.props[2] if len(k.props) > 2 else '') for k in objs.kids).most_common(20))
