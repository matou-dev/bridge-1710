#!/usr/bin/env python3
"""B3 probe: list block IDs at height y in chunk (cx,cz) of a 1.7.10 region file."""
import struct, sys, zlib


def read_nbt(buf, pos):
    t = buf[pos]
    pos += 1
    if t == 0:
        return None, pos
    nlen = struct.unpack(">H", buf[pos:pos + 2])[0]
    pos += 2
    name = buf[pos:pos + nlen].decode("utf-8", "replace")
    pos += nlen
    val, pos = read_payload(buf, pos, t)
    return (name, val), pos


def read_payload(buf, pos, t):
    if t == 1:
        return buf[pos], pos + 1
    if t == 2:
        return struct.unpack(">h", buf[pos:pos + 2])[0], pos + 2
    if t == 3:
        return struct.unpack(">i", buf[pos:pos + 4])[0], pos + 4
    if t == 4:
        return struct.unpack(">q", buf[pos:pos + 8])[0], pos + 8
    if t == 5:
        return struct.unpack(">f", buf[pos:pos + 4])[0], pos + 4
    if t == 6:
        return struct.unpack(">d", buf[pos:pos + 8])[0], pos + 8
    if t == 7:
        n = struct.unpack(">i", buf[pos:pos + 4])[0]
        return bytes(buf[pos + 4:pos + 4 + n]), pos + 4 + n
    if t == 8:
        n = struct.unpack(">H", buf[pos:pos + 2])[0]
        return buf[pos + 2:pos + 2 + n].decode("utf-8", "replace"), pos + 2 + n
    if t == 9:
        et = buf[pos]
        n = struct.unpack(">i", buf[pos + 1:pos + 5])[0]
        pos += 5
        out = []
        for _ in range(n):
            if et in (1, 2, 3, 4, 5, 6, 7, 8):
                v, pos = read_payload(buf, pos, et)
                out.append(v)
            elif et == 10:
                d = {}
                while True:
                    item, pos = read_nbt(buf, pos)
                    if item is None:
                        break
                    d[item[0]] = item[1]
                out.append(d)
            else:
                raise ValueError("list of %d" % et)
        return out, pos
    if t == 10:
        d = {}
        while True:
            item, pos = read_nbt(buf, pos)
            if item is None:
                break
            d[item[0]] = item[1]
        return d, pos
    if t == 11:
        n = struct.unpack(">i", buf[pos:pos + 4])[0]
        vals = struct.unpack(">%di" % n, buf[pos + 4:pos + 4 + 4 * n])
        return list(vals), pos + 4 + 4 * n
    raise ValueError("tag %d" % t)


def chunk_at(path, cx, cz):
    raw = open(path, "rb").read()
    lx, lz = cx & 31, cz & 31
    off = struct.unpack(">I", raw[(lx + lz * 32) * 4:(lx + lz * 32) * 4 + 4])[0]
    if off == 0:
        raise SystemExit("chunk absent")
    pos = (off >> 8) * 4096
    ln = struct.unpack(">I", raw[pos:pos + 4])[0]
    comp = raw[pos + 4]
    data = raw[pos + 5:pos + 4 + ln]
    if comp == 2:
        data = zlib.decompress(data)
    elif comp != 1:
        raise SystemExit("compression %d" % comp)
    (name, val), _ = read_nbt(data, 0)
    return val


def main():
    path, cx, cz, y = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
    root = chunk_at(path, cx, cz)
    level = root["Level"]
    found = {}
    for sec in level["Sections"]:
        if sec["Y"] == (y >> 4):
            blocks = sec["Blocks"]
            ly = y & 15
            for x in range(16):
                for z in range(16):
                    bid = blocks[ly * 256 + z * 16 + x]
                    if bid != 0:
                        found["%d,%d" % (x, z)] = bid
    for cell in sorted(found):
        print(cell, found[cell])


if __name__ == "__main__":
    main()
