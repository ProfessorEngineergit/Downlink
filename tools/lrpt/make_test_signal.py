"""Synthetic Meteor-M LRPT baseband for testing Downlink's decoder.

Builds a test picture, compresses it the way MSU-MR does (8x8 DCT, standard JPEG luminance tables,
14 MCUs per packet), wraps it in CCSDS packets, M_PDUs, VCDUs and CADUs (randomised, sync word),
encodes it with the k=7 r=1/2 convolutional code, modulates OQPSK at 72 ksym/s with RRC pulses and
writes signed 8-bit I/Q at 192 kS/s with noise, carrier offset and phase rotation.

    python3 make_test_signal.py out.s8 [snr_db] [lines]
"""
import sys
import numpy as np

ASM = bytes.fromhex("1ACFFC1D")
ZIGZAG = [0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5, 12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28,
          35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51, 58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63]
STDQ = [16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55, 14, 13, 16, 24, 40, 57, 69, 56, 14, 17, 22, 29, 51, 87, 80, 62,
        18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92, 49, 64, 78, 87, 103, 121, 120, 101, 72, 92, 95, 98, 112, 100, 103, 99]
DC_BITS = [0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0]
DC_VALS = list(range(12))
AC_BITS = [0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d]
AC_VALS = [int(x, 16) for x in (
    "01 02 03 00 04 11 05 12 21 31 41 06 13 51 61 07 22 71 14 32 81 91 a1 08 23 42 b1 c1 15 52 d1 f0 24 33 62 72 82 09 0a 16 17 18 19 1a 25 26 27 28 "
    "29 2a 34 35 36 37 38 39 3a 43 44 45 46 47 48 49 4a 53 54 55 56 57 58 59 5a 63 64 65 66 67 68 69 6a 73 74 75 76 77 78 79 7a 83 84 85 86 87 88 89 "
    "8a 92 93 94 95 96 97 98 99 9a a2 a3 a4 a5 a6 a7 a8 a9 aa b2 b3 b4 b5 b6 b7 b8 b9 ba c2 c3 c4 c5 c6 c7 c8 c9 ca d2 d3 d4 d5 d6 d7 d8 d9 da e1 e2 "
    "e3 e4 e5 e6 e7 e8 e9 ea f1 f2 f3 f4 f5 f6 f7 f8 f9 fa").split()]


def huff_codes(bits, vals):
    codes, code, k = {}, 0, 0
    for l in range(1, 17):
        for _ in range(bits[l - 1]):
            codes[vals[k]] = (code, l); code += 1; k += 1
        code <<= 1
    return codes


DC = huff_codes(DC_BITS, DC_VALS)
AC = huff_codes(AC_BITS, AC_VALS)
C = np.array([[(np.sqrt(0.5) if u == 0 else 1) * np.cos((2 * x + 1) * u * np.pi / 16) / 2 for u in range(8)] for x in range(8)])


def qtable(q):
    f = 5000 / q if q < 50 else 200 - 2 * q
    return np.maximum(1, np.floor((np.array(STDQ) * f + 50) / 100))


class Bits:
    def __init__(self): self.b = []
    def put(self, v, n): self.b += [(v >> (n - 1 - i)) & 1 for i in range(n)]
    def bytes(self):
        b = self.b + [1] * (-len(self.b) % 8)
        return bytes(int("".join(map(str, b[i:i + 8])), 2) for i in range(0, len(b), 8))


def mag(v):
    s = int(abs(v)).bit_length()
    return s, (v if v >= 0 else v + (1 << s) - 1)


def encode_packet_mcus(img, row, col0, q):
    qt = qtable(q)
    w = Bits(); prev = 0
    for m in range(14):
        blk = img[row:row + 8, (col0 + m) * 8:(col0 + m) * 8 + 8].astype(float) - 128
        F = C.T @ blk @ C  # forward DCT: inverse of f = C F C^T
        coef = np.round(F.flatten() / qt).astype(int)
        zz = [coef[ZIGZAG[k]] for k in range(64)]
        d = zz[0] - prev; prev = zz[0]
        s, v = mag(d); c, l = DC[s]; w.put(c, l); w.put(v, s)
        run = 0
        last = max([k for k in range(1, 64) if zz[k] != 0], default=0)
        for k in range(1, last + 1):
            if zz[k] == 0:
                run += 1
                continue
            while run > 15:
                c, l = AC[0xf0]; w.put(c, l); run -= 16
            s, v = mag(zz[k]); c, l = AC[(run << 4) | s]; w.put(c, l); w.put(v, s); run = 0
        if last < 63:
            c, l = AC[0x00]; w.put(c, l)
    return w.bytes()


def test_image(lines):
    h, wd = lines * 8, 1568
    y, x = np.mgrid[0:h, 0:wd]
    base = 40 + 150 * (x / wd)
    base += 60 * np.sin(y / 9.0) * (x > wd * 0.6)
    for cx, cy, r in [(400, h * 0.5, h * 0.35), (1000, h * 0.4, h * 0.25)]:
        base[(x - cx) ** 2 + (y - cy) ** 2 < r ** 2] = 230
    base[(y // 16 + x // 64) % 2 == 0] *= 0.85
    return np.clip(base, 0, 255).astype(np.uint8)


def pn_table():
    s = [1] * 8; out = []
    for _ in range(1020 * 8):
        out.append(s[0]); nb = s[0] ^ s[3] ^ s[5] ^ s[7]; s = s[1:] + [nb]
    return np.packbits(np.array(out, dtype=np.uint8))


def main():
    np.random.seed(int(sys.argv[5]) if len(sys.argv) > 5 else 1)
    out = sys.argv[1]
    snr = float(sys.argv[2]) if len(sys.argv) > 2 else 12.0
    lines = int(sys.argv[3]) if len(sys.argv) > 3 else 24
    img = test_image(lines)
    chans = {64: img, 65: (255 - img), 66: np.roll(img, 300, axis=1)}
    np.save(out + ".truth.npy", np.stack([chans[64], chans[65], chans[66]]))
    q = 80
    # packets
    stream = bytearray(); seq = 0
    for line in range(lines):
        for apid in (64, 65, 66):
            for chunk in range(14):
                mcu = chunk * 14
                body = bytes(8) + bytes([mcu, 0, 0, 0, 0, q]) + encode_packet_mcus(chans[apid], line * 8, mcu, q)
                hdr = bytes([0x08 | (apid >> 8), apid & 0xff, 0xc0 | (seq >> 8 & 0x3f), seq & 0xff]) + (len(body) - 1).to_bytes(2, "big")
                stream += hdr + body; seq = (seq + 1) & 0x3fff
        body = bytes(100)  # telemetry packet, APID 70
        stream += bytes([0x08, 70, 0xc0 | (seq >> 8 & 0x3f), seq & 0xff]) + (len(body) - 1).to_bytes(2, "big") + body
        seq = (seq + 1) & 0x3fff
    # packet start offsets, for the first header pointers
    starts = []; i = 0
    while i < len(stream):
        starts.append(i); i += 6 + int.from_bytes(stream[i + 4:i + 6], "big") + 1
    pn = pn_table()
    cadus = []
    zone = 882; cnt = 0
    for z0 in range(0, len(stream), zone):
        chunk = stream[z0:z0 + zone]; chunk += bytes(zone - len(chunk))
        fhp = next((s - z0 for s in starts if z0 <= s < z0 + zone), 0x7ff)
        v = bytes([0x40, 5]) + cnt.to_bytes(3, "big") + bytes([0]) + bytes(2) + bytes([fhp >> 8, fhp & 0xff]) + chunk
        v = v + bytes(128)  # Reed-Solomon parity (not used by the decoder)
        v = (np.frombuffer(v, dtype=np.uint8) ^ pn).tobytes()
        cadus.append(ASM + v); cnt += 1
    data = b"".join(cadus * 1)
    bits = np.unpackbits(np.frombuffer(data, dtype=np.uint8))
    # convolutional code
    reg = 0; sym = np.empty(len(bits) * 2, dtype=np.int8)
    p4f = [bin(r & 0x4f).count("1") & 1 for r in range(128)]
    p6d = [bin(r & 0x6d).count("1") & 1 for r in range(128)]
    for k, b in enumerate(bits):
        reg = ((reg << 1) | int(b)) & 0x7f
        sym[2 * k] = p4f[reg]; sym[2 * k + 1] = p6d[reg]
    I = sym[0::2] * 2.0 - 1; Q = sym[1::2] * 2.0 - 1
    # OQPSK at 8 samples per symbol (576 kS/s), Q delayed by half a symbol
    sps = 8; n = len(I)
    a = 0.6; taps = np.arange(-4 * sps, 4 * sps + 1) / sps
    def rrc(t):
        out = np.zeros_like(t)
        for i, tt in enumerate(t):
            if abs(tt) < 1e-9: out[i] = 1 - a + 4 * a / np.pi
            elif abs(abs(4 * a * tt) - 1) < 1e-9: out[i] = a / np.sqrt(2) * ((1 + 2 / np.pi) * np.sin(np.pi / (4 * a)) + (1 - 2 / np.pi) * np.cos(np.pi / (4 * a)))
            else: out[i] = (np.sin(np.pi * tt * (1 - a)) + 4 * a * tt * np.cos(np.pi * tt * (1 + a))) / (np.pi * tt * (1 - 16 * a * a * tt * tt))
        return out
    h = rrc(taps)
    ui = np.zeros(n * sps); uq = np.zeros(n * sps)
    ui[::sps] = I; uq[sps // 2::sps] = Q
    si = np.convolve(ui, h, "same"); sq = np.convolve(uq, h, "same")
    s = si + 1j * sq
    # to 192 kS/s: low-pass and take every 3rd sample
    lp = np.sinc(np.arange(-48, 49) / 3.0) * np.hamming(97); lp /= lp.sum()
    s = np.convolve(s, lp, "same")[::3]
    fs = 192000.0
    pad = np.zeros(int(fs * 0.3), dtype=complex)
    s = np.concatenate([pad, s, pad])
    t = np.arange(len(s)) / fs
    off = float(sys.argv[4]) if len(sys.argv) > 4 else 420.0
    s *= np.exp(1j * (2 * np.pi * off * t + 1.1))          # carrier offset and phase
    p = np.mean(np.abs(s[len(pad):-len(pad)]) ** 2)
    noise = np.sqrt(p / 10 ** (snr / 10) / 2) * (np.random.randn(len(s)) + 1j * np.random.randn(len(s)))
    s = s + noise
    s *= 30 / np.sqrt(np.mean(np.abs(s) ** 2))
    iq = np.empty(len(s) * 2, dtype=np.int8)
    iq[0::2] = np.clip(np.round(s.real), -127, 127); iq[1::2] = np.clip(np.round(s.imag), -127, 127)
    iq.tofile(out)
    print(f"{len(cadus)} CADUs, {seq} packets, {len(s) / fs:.1f} s, SNR {snr} dB -> {out}")


if __name__ == "__main__":
    main()
