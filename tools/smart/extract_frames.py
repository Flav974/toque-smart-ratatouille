#!/usr/bin/env python3
"""Extrait les trames d'une capture strace du Monsieur Cuisine Smart vers un fichier TSV.

Usage : python3 -I extract_frames.py capture.txt FD > sortie.tsv
Colonnes : horodatage, sens (TX = Android -> carte, RX = carte -> Android), trame en hexadécimal.
Seules les trames dont la somme de contrôle est juste sont gardées. Sert de jeu de test
pour SmartProtocolTest (core/src/test/resources/smart/).
"""
import re
import sys

LINE = re.compile(r'^(?:\d+\s+)?(\S+)\s+(read|write)\((\d+),\s*"((?:\\x[0-9a-f]{2})*)"(?:\.\.\.)?,\s*\d+\)\s*=\s*(\d+)')


def split(buf):
    out, i = [], 0
    while i < len(buf):
        if buf[i] != 0xAA:
            i += 1
            continue
        if len(buf) - i < 3:
            break
        n = (buf[i + 1] << 8) | buf[i + 2]
        if n < 8 or n > 300:
            i += 1
            continue
        if len(buf) - i < n + 1:
            break
        f = bytes(buf[i:i + n + 1])
        if (sum(f[1:n]) & 0xFF) == f[n]:
            out.append(f)
            i += n + 1
        else:
            i += 1
    return out, buf[i:]


def main():
    path, fd = sys.argv[1], sys.argv[2]
    streams = {'read': bytearray(), 'write': bytearray()}
    for line in open(path, errors='replace'):
        m = LINE.match(line)
        if not m or m.group(3) != fd:
            continue
        op = m.group(2)
        b = bytes(int(h, 16) for h in re.findall(r'\\x([0-9a-f]{2})', m.group(4)))[:int(m.group(5))]
        streams[op] += b
        frames, rest = split(streams[op])
        streams[op] = bytearray(rest)
        for f in frames:
            print('%s\t%s\t%s' % (m.group(1), 'RX' if op == 'read' else 'TX', ' '.join('%02X' % x for x in f)))


if __name__ == '__main__':
    main()
