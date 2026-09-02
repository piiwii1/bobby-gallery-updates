#!/usr/bin/env python3
import argparse
import datetime as dt
import math
import os
import re
import sqlite3
import sys

import osmium

ALLOWED_HIGHWAYS = {
    'motorway','motorway_link','trunk','trunk_link','primary','primary_link',
    'secondary','secondary_link','tertiary','tertiary_link','unclassified',
    'residential','living_street','service','road'
}

SYMBOLIC_SPEEDS = {
    'fr:urban': 50,
    'fr:rural': 80,
    'fr:motorway': 130,
    'fr:trunk': 110,
    'fr:zone30': 30,
    'fr:zone20': 20,
    'fr:living_street': 20,
    'fr:zone50': 50,
    'fr:zone40': 40,
}

EARTH_R = 6371000.0


def haversine(a, b):
    lat1, lon1 = a
    lat2, lon2 = b
    p1 = math.radians(lat1)
    p2 = math.radians(lat2)
    dp = math.radians(lat2-lat1)
    dl = math.radians(lon2-lon1)
    x = math.sin(dp/2)**2 + math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2
    return 2*EARTH_R*math.asin(min(1.0, math.sqrt(x)))


def projected(points):
    lat0 = sum(p[0] for p in points)/len(points)
    c = math.cos(math.radians(lat0))
    return [(p[1]*111320.0*c, p[0]*110574.0) for p in points]


def point_segment_distance(p, a, b):
    px, py = p; ax, ay = a; bx, by = b
    dx = bx-ax; dy = by-ay
    if dx == 0 and dy == 0:
        return math.hypot(px-ax, py-ay)
    t = ((px-ax)*dx + (py-ay)*dy)/(dx*dx+dy*dy)
    if t < 0: t = 0
    elif t > 1: t = 1
    qx = ax+t*dx; qy = ay+t*dy
    return math.hypot(px-qx, py-qy)


def simplify_dp(points, tolerance_m=3.0):
    if len(points) <= 2:
        return points
    xy = projected(points)
    keep = [False]*len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points)-1)]
    while stack:
        i, j = stack.pop()
        a, b = xy[i], xy[j]
        best_d = -1.0; best_k = None
        for k in range(i+1, j):
            d = point_segment_distance(xy[k], a, b)
            if d > best_d:
                best_d = d; best_k = k
        if best_k is not None and best_d > tolerance_m:
            keep[best_k] = True
            stack.append((i, best_k)); stack.append((best_k, j))
    return [p for p,k in zip(points,keep) if k]


def densify(points, max_segment_m=150.0):
    out = []
    for a,b in zip(points, points[1:]):
        d = haversine(a,b)
        if d < 0.02:
            continue
        n = max(1, int(math.ceil(d/max_segment_m)))
        prev = a
        for i in range(1,n+1):
            t = i/n
            cur = (a[0]+(b[0]-a[0])*t, a[1]+(b[1]-a[1])*t)
            out.append((prev,cur))
            prev = cur
    return out


def parse_speed(raw):
    if raw is None:
        return None
    s = str(raw).strip()
    if not s:
        return None
    lo = s.lower()
    if lo in SYMBOLIC_SPEEDS:
        return SYMBOLIC_SPEEDS[lo]
    # A few OSM values use lowercase/uppercase variants.
    lo2 = lo.replace(' ', '')
    if lo2 in SYMBOLIC_SPEEDS:
        return SYMBOLIC_SPEEDS[lo2]
    m = re.search(r'(?<![\d.])(\d+(?:\.\d+)?)', lo)
    if not m:
        return None
    value = float(m.group(1))
    if 'mph' in lo:
        value *= 1.609344
    v = int(round(value))
    if v < 0 or v > 250:
        return None
    return v


def default_speed(highway):
    if highway in ('motorway','motorway_link'):
        return 130
    if highway == 'living_street':
        return 20
    return 0


def derive_base(tags, highway):
    for k in ('maxspeed','source:maxspeed','maxspeed:type','zone:maxspeed'):
        v = parse_speed(tags.get(k))
        if v is not None:
            return v
    return default_speed(highway)


def tile_for(a,b):
    lat = (a[0]+b[0])/2.0
    lon = (a[1]+b[1])/2.0
    return math.floor((lat+90.0)*100.0)*40000 + math.floor((lon+180.0)*100.0)


class RegionHandler(osmium.SimpleHandler):
    def __init__(self, db, region):
        super().__init__()
        self.db = db
        self.region = region
        self.roads = 0
        self.segments = 0
        self.with_speed = 0
        self.without_speed = 0
        self.min_lat = 90.0; self.max_lat = -90.0
        self.min_lon = 180.0; self.max_lon = -180.0
        self._pending = 0

    def way(self, w):
        highway = w.tags.get('highway')
        if highway not in ALLOWED_HIGHWAYS:
            return
        pts = []
        try:
            for n in w.nodes:
                if n.location.valid():
                    pts.append((n.location.lat, n.location.lon))
        except Exception:
            return
        if len(pts) < 2:
            return
        pts = simplify_dp(pts, 3.0)
        segs = densify(pts, 150.0)
        if not segs:
            return

        tags = {k: w.tags.get(k) for k in (
            'name','ref','oneway','maxspeed','maxspeed:forward','maxspeed:backward',
            'source:maxspeed','maxspeed:type','zone:maxspeed'
        )}
        base = derive_base(tags, highway)
        fwd = parse_speed(tags.get('maxspeed:forward'))
        back = parse_speed(tags.get('maxspeed:backward'))
        if fwd is None: fwd = base
        if back is None: back = base

        self.db.execute(
            'INSERT INTO roads(id,name,ref,highway,oneway,raw_maxspeed,raw_forward,raw_backward,source_maxspeed,maxspeed_type,zone_maxspeed,maxspeed,maxspeed_forward,maxspeed_backward) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
            (int(w.id), tags.get('name'), tags.get('ref'), highway, tags.get('oneway') or '',
             tags.get('maxspeed'), tags.get('maxspeed:forward'), tags.get('maxspeed:backward'),
             tags.get('source:maxspeed'), tags.get('maxspeed:type'), tags.get('zone:maxspeed'),
             int(base), int(fwd), int(back))
        )
        rows = []
        for a,b in segs:
            self.min_lat = min(self.min_lat,a[0],b[0]); self.max_lat = max(self.max_lat,a[0],b[0])
            self.min_lon = min(self.min_lon,a[1],b[1]); self.max_lon = max(self.max_lon,a[1],b[1])
            rows.append((int(w.id), round(a[0]*1e7), round(a[1]*1e7), round(b[0]*1e7), round(b[1]*1e7), tile_for(a,b)))
        self.db.executemany('INSERT INTO segments(road_id,lat1,lon1,lat2,lon2,tile) VALUES(?,?,?,?,?,?)', rows)
        self.roads += 1
        self.segments += len(rows)
        if base > 0: self.with_speed += 1
        else: self.without_speed += 1
        self._pending += 1
        if self._pending >= 3000:
            self.db.commit(); self._pending = 0
            print(f'{self.region}: roads={self.roads:,} segments={self.segments:,}', flush=True)


def build(pbf, output, region, data_version):
    tmp = output + '.tmp'
    if os.path.exists(tmp): os.remove(tmp)
    if os.path.exists(output): os.remove(output)
    db = sqlite3.connect(tmp)
    db.execute('PRAGMA journal_mode=OFF')
    db.execute('PRAGMA synchronous=OFF')
    db.execute('PRAGMA temp_store=MEMORY')
    db.executescript('''
CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE roads(
 id INTEGER PRIMARY KEY,
 name TEXT, ref TEXT, highway TEXT NOT NULL, oneway TEXT,
 raw_maxspeed TEXT, raw_forward TEXT, raw_backward TEXT,
 source_maxspeed TEXT, maxspeed_type TEXT, zone_maxspeed TEXT,
 maxspeed INTEGER NOT NULL DEFAULT 0,
 maxspeed_forward INTEGER NOT NULL DEFAULT 0,
 maxspeed_backward INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE segments(
 road_id INTEGER NOT NULL,
 lat1 INTEGER NOT NULL, lon1 INTEGER NOT NULL,
 lat2 INTEGER NOT NULL, lon2 INTEGER NOT NULL,
 tile INTEGER NOT NULL
);
''')
    h = RegionHandler(db, region)
    h.apply_file(pbf, locations=True, idx='flex_mem')
    db.commit()
    db.execute('CREATE INDEX idx_segments_tile ON segments(tile)')
    now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace('+00:00','Z')
    meta = {
        'country':'FR','data_version':data_version,'generated_utc':now,
        'max_lat':str(h.max_lat),'max_lon':str(h.max_lon),'max_segment_m':'150.0',
        'min_lat':str(h.min_lat),'min_lon':str(h.min_lon),'region':region,
        'schema':'compact-1','segments':str(h.segments),'simplification_m':'3.0',
        'source':'OpenStreetMap / Geofabrik','ways':str(h.roads),
        'ways_with_speed':str(h.with_speed),'ways_without_speed':str(h.without_speed)
    }
    db.executemany('INSERT INTO metadata(key,value) VALUES(?,?)', sorted(meta.items()))
    db.commit()
    db.execute('VACUUM')
    db.close()
    os.replace(tmp, output)
    print(f'DONE {region}: {output} roads={h.roads} segments={h.segments} speed={h.with_speed} no_speed={h.without_speed}', flush=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('pbf'); ap.add_argument('output'); ap.add_argument('region')
    ap.add_argument('--data-version', default='02.09.2026')
    args = ap.parse_args()
    build(args.pbf,args.output,args.region,args.data_version)

if __name__ == '__main__':
    main()
