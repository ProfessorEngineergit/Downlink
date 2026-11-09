// Meteor-M LRPT decoder for Downlink.
//
// Chain: AGC -> RRC matched filter -> Costas loop (QPSK, decision directed) -> Gardner timing
// with the Q rail sampled half a symbol later (OQPSK) -> soft symbols -> search for the
// convolutionally encoded sync word in all 8 rotations/reflections -> Viterbi (k=7, r=1/2,
// polynomials 0x4F/0x6D) -> CADU sync (0x1ACFFC1D) -> CCSDS derandomisation -> VCDU/M_PDU ->
// CCSDS packets -> MSU-MR JPEG-like decoding into 8-row strips per channel.
// Reed-Solomon parity is not used for correction yet.

#include "lrpt.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <cstdio>
#include <cstdlib>

namespace lrpt {

static const double SYMRATE = 72000.0;
static const uint32_t ASM = 0x1ACFFC1D;

static inline int parity(uint32_t x) { return __builtin_popcount(x) & 1; }

// ---------------- JPEG tables ----------------
static const uint8_t ZIGZAG[64] = {
    0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5, 12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28,
    35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51, 58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63};
static const int STDQ[64] = {
    16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55, 14, 13, 16, 24, 40, 57, 69, 56, 14, 17, 22, 29, 51, 87, 80, 62,
    18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92, 49, 64, 78, 87, 103, 121, 120, 101, 72, 92, 95, 98, 112, 100, 103, 99};
static const uint8_t DC_BITS[16] = {0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0};
static const uint8_t DC_VALS[12] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
static const uint8_t AC_BITS[16] = {0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d};
static const uint8_t AC_VALS[162] = {
    0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07, 0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xa1, 0x08,
    0x23, 0x42, 0xb1, 0xc1, 0x15, 0x52, 0xd1, 0xf0, 0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0a, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28,
    0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59,
    0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
    0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6,
    0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xe1, 0xe2,
    0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea, 0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8, 0xf9, 0xfa};

struct Huff {
    int mincode[17], maxcode[18], valptr[17];
    std::vector<uint8_t> vals;
    void build(const uint8_t* bits, const uint8_t* v, int n) {
        vals.assign(v, v + n);
        int code = 0, k = 0;
        for (int l = 1; l <= 16; l++) {
            valptr[l] = k; mincode[l] = code;
            code += bits[l - 1]; k += bits[l - 1];
            maxcode[l] = bits[l - 1] ? code - 1 : -1;
            code <<= 1;
        }
        maxcode[17] = 0x7fffffff;
    }
};

struct BitReader {
    const uint8_t* p; size_t n; size_t pos = 0;
    BitReader(const uint8_t* p, size_t n) : p(p), n(n) {}
    int bit() { if (pos >= n * 8) return -1; int b = (p[pos >> 3] >> (7 - (pos & 7))) & 1; pos++; return b; }
    int bits(int k) { int v = 0; for (int i = 0; i < k; i++) { int b = bit(); if (b < 0) return -1; v = (v << 1) | b; } return v; }
    int decode(const Huff& h) {
        int code = 0;
        for (int l = 1; l <= 16; l++) {
            int b = bit(); if (b < 0) return -1;
            code = (code << 1) | b;
            if (h.maxcode[l] >= 0 && code <= h.maxcode[l] && code >= h.mincode[l]) return h.vals[h.valptr[l] + code - h.mincode[l]];
        }
        return -1;
    }
};

static inline int extend(int v, int s) { return s == 0 ? 0 : (v < (1 << (s - 1)) ? v - (1 << s) + 1 : v); }

// ---------------- implementation ----------------
struct Decoder::Impl {
    double rate, sps;
    // front end
    std::vector<float> rrc;
    std::vector<float> hi, hq; int hpos = 0;
    float agc = 1.0f;
    double ncoPhase = 0, freq = 0;            // freq in rad/sample
    double alpha, beta;                        // Costas gains (per symbol)
    // sample ring for interpolation
    static const int RING = 128;
    float ri[RING * 2], rq[RING * 2];
    long sampleIndex = 0;                      // absolute index of the next sample written
    double tNext;                              // absolute time of next I strobe (samples)
    double tInt = 0;                           // timing loop integrator
    float pI = 0, pQ = 0;                      // previous symbol (I at t, Q at t+sps/2)
    float ampAvg = 0.5f, ampVar = 0.1f;
    float p4r = 1, p4i = 0; double fllGain = 0.0015;
    // sync search / viterbi
    int variant = -1;
    uint64_t hard[16] = {0};
    int8_t prevB = 0;
    uint64_t asmPat = 0, asmMask = 0;
    std::vector<int8_t> history;               // last soft pairs for replay after lock
    long pairsSinceFrame = 0;
    // viterbi
    int32_t metric[64], nmetric[64];
    std::vector<uint64_t> dec;                 // decision bits per step
    int decCount = 0;
    static const int TB = 96, CHUNK = 32;
    // bit stream
    uint32_t sr = 0; bool inFrame = false; bool invert = false; int frameBits = 0; uint8_t frame[1024];
    int misses = 0; long sinceFrame = -1;
    uint8_t pn[1020];
    // packets
    std::vector<uint8_t> pkt; bool pktActive = false; long lastVcduCount = -1;
    // image
    Huff dcH, acH;
    std::map<int, Channel> ch;
    struct Track { bool init = false; long firstSeq = 0; int firstChunk = 0; long lastSeq = 0; long seqU = 0; int votes43 = 0, votes14 = 0; int line0 = 0; };
    std::map<int, Track> tracks;
    long firstGlobalSeq = -1; int firstGlobalChunk = -1;
    Stats st;
    float cosT[8][8];

    Impl(double r) : rate(r) {
        sps = rate / SYMRATE;
        // root raised cosine, alpha 0.6, 8 symbols long
        int taps = ((int)(sps * 8)) | 1;
        rrc.resize(taps);
        double a = 0.6, sum = 0;
        for (int i = 0; i < taps; i++) {
            double t = (i - taps / 2) / sps, v;
            if (std::fabs(t) < 1e-9) v = 1 - a + 4 * a / M_PI;
            else if (std::fabs(std::fabs(4 * a * t) - 1) < 1e-9) v = a / std::sqrt(2) * ((1 + 2 / M_PI) * std::sin(M_PI / (4 * a)) + (1 - 2 / M_PI) * std::cos(M_PI / (4 * a)));
            else v = (std::sin(M_PI * t * (1 - a)) + 4 * a * t * std::cos(M_PI * t * (1 + a))) / (M_PI * t * (1 - 16 * a * a * t * t));
            rrc[i] = (float)v; sum += v;
        }
        for (auto& v : rrc) v = (float)(v / sum);
        hi.assign(taps * 2, 0); hq.assign(taps * 2, 0);
        // Costas loop, second order
        double bw = 0.004, z = 0.707, th = bw / (z + 1 / (4 * z)), dd = 1 + 2 * z * th + th * th;
        alpha = 4 * z * th / dd; beta = 4 * th * th / dd;
        std::memset(ri, 0, sizeof ri); std::memset(rq, 0, sizeof rq);
        tNext = (double)(rrc.size() + 8);
        // encoded sync word: bits 6.. of the convolutionally coded ASM (the first 6 depend on the previous byte)
        uint32_t reg = 0; int n = 0;
        for (int i = 31; i >= 0; i--) {
            reg = ((reg << 1) | ((ASM >> i) & 1)) & 0x7f;
            int b0 = parity(reg & 0x4f), b1 = parity(reg & 0x6d);
            if (31 - i >= 6) { asmPat = (asmPat << 2) | (b0 << 1) | b1; n += 2; }
        }
        asmMask = (n >= 64) ? ~0ULL : ((1ULL << n) - 1);
        // CCSDS pseudo random sequence
        int s[8] = {1, 1, 1, 1, 1, 1, 1, 1};
        for (int i = 0; i < 1020; i++) {
            uint8_t b = 0;
            for (int k = 0; k < 8; k++) { b = (b << 1) | s[0]; int nb = s[0] ^ s[3] ^ s[5] ^ s[7]; for (int j = 0; j < 7; j++) s[j] = s[j + 1]; s[7] = nb; }
            pn[i] = b;
        }
        dcH.build(DC_BITS, DC_VALS, 12); acH.build(AC_BITS, AC_VALS, 162);
        for (int x = 0; x < 8; x++) for (int u = 0; u < 8; u++) cosT[x][u] = (float)((u == 0 ? std::sqrt(0.5) : 1.0) * std::cos((2 * x + 1) * u * M_PI / 16) / 2);
        resetViterbi();
    }

    void resetViterbi() { for (int i = 0; i < 64; i++) metric[i] = i == 0 ? 0 : 0; dec.clear(); decCount = 0; }

    // ---- front end: one complex sample ----
    void sample(float i, float q) {
        // AGC to unit power
        float m = std::sqrt(i * i + q * q) * agc;
        agc += 1e-4f * (1.f - m) * agc;
        if (agc < 1e-4f) agc = 1e-4f;
        i *= agc; q *= agc;
        // Costas NCO
        float c = (float)std::cos(ncoPhase), s = (float)std::sin(ncoPhase);
        float xi = i * c + q * s, xq = q * c - i * s;
        ncoPhase += freq;
        if (ncoPhase > M_PI) ncoPhase -= 2 * M_PI; else if (ncoPhase < -M_PI) ncoPhase += 2 * M_PI;
        // matched filter
        int n = (int)rrc.size();
        hi[hpos] = hi[hpos + n] = xi; hq[hpos] = hq[hpos + n] = xq;
        hpos = (hpos + 1) % n;
        float fi = 0, fq = 0;
        for (int k = 0; k < n; k++) { fi += hi[hpos + k] * rrc[k]; fq += hq[hpos + k] * rrc[k]; }
        int w = (int)(sampleIndex % RING);
        ri[w] = ri[w + RING] = fi; rq[w] = rq[w + RING] = fq;
        sampleIndex++;
        // strobe when Q at t+sps/2 can be interpolated
        while (tNext + sps / 2 + 2 < sampleIndex) strobe();
    }

    // cubic interpolation of the filtered stream at absolute time t
    void interp(double t, float& oi, float& oq) {
        long k = (long)std::floor(t); float mu = (float)(t - k);
        float a[4], b[4];
        for (int j = 0; j < 4; j++) { long idx = k - 1 + j; int w = (int)(((idx % RING) + RING) % RING); a[j] = ri[w]; b[j] = rq[w]; }
        auto cub = [mu](const float* y) {
            float c0 = y[1], c1 = 0.5f * (y[2] - y[0]), c2 = y[0] - 2.5f * y[1] + 2 * y[2] - 0.5f * y[3], c3 = 0.5f * (y[3] - y[0]) + 1.5f * (y[1] - y[2]);
            return ((c3 * mu + c2) * mu + c1) * mu + c0;
        };
        oi = cub(a); oq = cub(b);
    }

    void strobe() {
        double t = tNext, h = sps / 2;
        float iNow, qDummy, qNow, iDummy, iMid, qMid, d0, d1;
        interp(t, iNow, qDummy);
        interp(t + h, iDummy, qNow);
        interp(t - h, iMid, d0);            // I between previous and current I symbol
        interp(t, d1, qMid);                // Q between previous and current Q symbol
        (void)qDummy; (void)iDummy; (void)d0; (void)d1;
        // Gardner on both rails (Q offset by half a symbol)
        float e = iMid * (pI - iNow) + qMid * (pQ - qNow);
        e = std::max(-1.f, std::min(1.f, e));
        tInt += 1e-4 * e;
        if (tInt > 0.02 * sps) tInt = 0.02 * sps; else if (tInt < -0.02 * sps) tInt = -0.02 * sps;
        tNext = t + sps + (0.03 * e + tInt);
        // Costas (decision directed QPSK) on the aligned symbol
        float ce = (iNow > 0 ? qNow : -qNow) - (qNow > 0 ? iNow : -iNow);
        ce = std::max(-1.f, std::min(1.f, ce));
        freq += beta * ce / sps;
        ncoPhase += alpha * ce;
        // Frequency lock helper: phase step of the 4th power between symbols, decision free
        {
            float zr = iNow, zi = qNow;
            float r2 = zr * zr - zi * zi, i2 = 2 * zr * zi;
            float r4 = r2 * r2 - i2 * i2, i4 = 2 * r2 * i2;
            float dr = r4 * p4r + i4 * p4i, di = i4 * p4r - r4 * p4i;
            p4r = r4; p4i = i4;
            float fe = std::atan2(di, dr) / 4.f;
            float mag = std::sqrt(dr * dr + di * di);
            if (mag > 1e-6f && variant < 0) freq += fllGain * fe / sps; // only while searching
        }
        // Downlink already removes the Doppler shift while recording; keep the loop within +-3 kHz
        double fmax = 2 * M_PI * 3000.0 / rate;
        if (freq > fmax) freq = fmax; else if (freq < -fmax) freq = -fmax;
        pI = iNow; pQ = qNow;
        // amplitude and a rough SNR
        float am = (std::fabs(iNow) + std::fabs(qNow)) * 0.5f;
        ampAvg += 0.001f * (am - ampAvg);
        float dv = std::fabs(iNow) - ampAvg; ampVar += 0.001f * (dv * dv - ampVar);
        st.symbols++;
        if ((st.symbols & 4095) == 0) st.snr = 10 * std::log10(ampAvg * ampAvg / std::max(1e-9f, ampVar));
        float sc = 48.f / std::max(1e-6f, ampAvg);
        int8_t a = (int8_t)std::max(-127.f, std::min(127.f, iNow * sc));
        int8_t b = (int8_t)std::max(-127.f, std::min(127.f, qNow * sc));
#ifdef LRPT_DEBUG
        { static FILE* dbg = fopen(getenv("LRPT_DUMP"), "wb"); float v[5] = {iNow, qNow, (float)freq, (float)(tNext - t), e}; fwrite(v, 4, 5, dbg); }
#endif
        softPair(a, b);
    }

    // the 8 symmetries of the QPSK constellation
    static inline void transform(int v, int8_t a, int8_t b, int8_t& x, int8_t& y) {
        int8_t p = a, q = b;
        if (v & 4) { p = b; q = a; }
        switch (v & 3) {
            case 0: x = p; y = q; break;
            case 1: x = (int8_t)-q; y = p; break;
            case 2: x = (int8_t)-p; y = (int8_t)-q; break;
            default: x = q; y = (int8_t)-p; break;
        }
    }

    // Variants 0-7: the 8 constellation symmetries. Variants 8-15: the same on pairs shifted by half a
    // symbol (Q of the previous symbol with I of this one), which is what a 90 degree Costas lock does to OQPSK.
    void softPair(int8_t a, int8_t b) {
        int8_t sa = prevB, sb = a; prevB = b;
        history.push_back(a); history.push_back(b);
        if (history.size() > 512) history.erase(history.begin(), history.begin() + 256);
        if (variant < 0) {
            for (int v = 0; v < 16; v++) {
                int8_t x, y; if (v < 8) transform(v, a, b, x, y); else transform(v - 8, sa, sb, x, y);
                hard[v] = (hard[v] << 2) | ((x > 0) << 1) | (y > 0);
                int err = __builtin_popcountll((hard[v] ^ asmPat) & asmMask);
                if (err <= 5) { lock(v); return; }
            }
            return;
        }
        int8_t x, y; if (variant < 8) transform(variant, a, b, x, y); else transform(variant - 8, sa, sb, x, y);
        viterbi(x, y);
        if (++pairsSinceFrame > 3 * 8192) { variant = -1; st.locked = 0; inFrame = false; }
    }

    void lock(int v) {
        variant = v; pairsSinceFrame = 0; sinceFrame = -1; resetViterbi();
        inFrame = false; sr = 0; misses = 0;
        // replay recent history so the decoder sees the sync word from the start
        size_t pairs = history.size() / 2;
        size_t from = pairs > 120 ? pairs - 120 : 1;
        for (size_t k = from; k < pairs; k++) {
            int8_t x, y;
            if (v < 8) transform(v, history[2 * k], history[2 * k + 1], x, y);
            else transform(v - 8, history[2 * k - 1], history[2 * k], x, y);
            viterbi(x, y);
        }
    }

    // ---- Viterbi, soft decision, block traceback ----
    void viterbi(int8_t s0, int8_t s1) {
        uint64_t d = 0;
        for (int ns = 0; ns < 64; ns++) {
            int b = ns & 1;
            int best = 0x7fffffff, bx = 0;
            for (int x = 0; x < 2; x++) {
                int ps = (ns >> 1) | (x << 5);
                uint32_t reg = (ps << 1) | b;
                int e0 = parity(reg & 0x4f), e1 = parity(reg & 0x6d);
                int bm = (e0 ? 127 - s0 : 127 + s0) + (e1 ? 127 - s1 : 127 + s1);
                int m = metric[ps] + bm;
                if (m < best) { best = m; bx = x; }
            }
            nmetric[ns] = best;
            if (bx) d |= 1ULL << ns;
        }
        int mn = nmetric[0]; for (int i = 1; i < 64; i++) mn = std::min(mn, nmetric[i]);
        for (int i = 0; i < 64; i++) metric[i] = nmetric[i] - mn;
        dec.push_back(d);
        if ((int)dec.size() >= TB + CHUNK) {
            int st0 = 0; for (int i = 1; i < 64; i++) if (metric[i] < metric[st0]) st0 = i;
            int s = st0;
            uint8_t out[TB + CHUNK];
            for (int k = (int)dec.size() - 1; k >= 0; k--) {
                out[k] = s & 1;
                int x = (dec[k] >> s) & 1;
                s = (s >> 1) | (x << 5);
            }
            for (int k = 0; k < CHUNK; k++) bit(out[k]);
            dec.erase(dec.begin(), dec.begin() + CHUNK);
        }
    }

    // ---- frame sync on decoded bits ----
    void bit(int b) {
        if (invert) b ^= 1;
        if (!inFrame) {
            sr = (sr << 1) | b;
            // frames follow each other directly: if the sync word does not come, the phase slipped
            if (sinceFrame >= 0 && ++sinceFrame == 32) {
                // at the expected place a sync word with a few bit errors is fine
                int e = __builtin_popcount(sr ^ ASM), ei = __builtin_popcount(sr ^ ~ASM);
                if (e > 8 && ei > 8) { sinceFrame = -1; variant = -1; st.locked = 0; for (auto& h : hard) h = 0; return; }
                if (ei <= 8) invert = !invert;
                inFrame = true; sinceFrame = -1; std::memset(frame, 0, sizeof frame);
                frame[0] = 0x1A; frame[1] = 0xCF; frame[2] = 0xFC; frame[3] = 0x1D; frameBits = 32;
                return;
            }
            if (sinceFrame >= 0) return;
            int e = __builtin_popcount(sr ^ ASM), ei = __builtin_popcount(sr ^ ~ASM);
            if (e <= 3 || ei <= 3) {
                if (ei <= 3) invert = !invert;
                inFrame = true; frameBits = 0; sinceFrame = -1; std::memset(frame, 0, sizeof frame);
                frame[0] = 0x1A; frame[1] = 0xCF; frame[2] = 0xFC; frame[3] = 0x1D; frameBits = 32;
            }
            return;
        }
        if (b) frame[frameBits >> 3] |= 0x80 >> (frameBits & 7);
        frameBits++;
        if (frameBits == 1024 * 8) {
            cadu(frame);
            pairsSinceFrame = 0; st.frames++; st.locked = 1;
            // the next sync word follows right away
            inFrame = false; sr = 0; sinceFrame = 0;
        }
    }

    // ---- CADU -> VCDU -> packets ----
    void cadu(const uint8_t* f) {
        uint8_t v[1020];
        for (int i = 0; i < 1020; i++) v[i] = f[4 + i] ^ pn[i];
        int vcid = v[1] & 0x3f;
        if (vcid == 63) return;
        long cnt = ((long)v[2] << 16) | (v[3] << 8) | v[4];
        if (lastVcduCount >= 0 && ((cnt - lastVcduCount) & 0xffffff) != 1) { pkt.clear(); pktActive = false; }
        lastVcduCount = cnt;
        int fhp = ((v[8] & 7) << 8) | v[9];
        const uint8_t* zone = v + 10; const int zlen = 882;
        if (fhp == 0x7ff) { if (pktActive) feedBytes(zone, zlen); return; }
        if (fhp >= zlen) { pkt.clear(); pktActive = false; return; }
        if (pktActive) feedBytes(zone, fhp);
        pkt.clear(); pktActive = true;
        feedBytes(zone + fhp, zlen - fhp);
    }

    void feedBytes(const uint8_t* p, int n) {
        int i = 0;
        while (i < n) {
            if (!pktActive) return;
            size_t need = pkt.size() < 6 ? 6 : (size_t)(6 + (((pkt[4] << 8) | pkt[5]) + 1));
            int take = (int)std::min((size_t)(n - i), need - pkt.size());
            pkt.insert(pkt.end(), p + i, p + i + take);
            i += take;
            if (pkt.size() == 6) continue;
            if (pkt.size() >= 6) {
                size_t total = 6 + (((pkt[4] << 8) | pkt[5]) + 1);
                if (total > 2100) { pkt.clear(); pktActive = false; return; }
                if (pkt.size() == total) { packet(pkt.data(), total); pkt.clear(); }
            }
        }
    }

    void packet(const uint8_t* p, size_t n) {
        int apid = ((p[0] & 7) << 8) | p[1];
        long seq = ((p[2] & 0x3f) << 8) | p[3];
        if (apid < 64 || apid > 69 || n < 6 + 14 + 2) return;
        const uint8_t* d = p + 6; size_t dn = n - 6;
        int mcu = d[8];
        int q = d[13];
        if (mcu > 195 || q < 1 || q > 100) return;
        int chunk = mcu / 14;
        // line position from the packet counter; the counter period per line (43 shared or 14 per channel) is learnt
        Track& t = tracks[apid];
        if (!t.init) {
            t.init = true; t.firstSeq = seq; t.firstChunk = chunk; t.lastSeq = seq; t.seqU = seq;
            if (firstGlobalSeq < 0) firstGlobalSeq = seq;
            if (firstGlobalSeq == seq && firstGlobalChunk < 0) firstGlobalChunk = chunk;
            long diff = ((seq - firstGlobalSeq) & 0x3fff) + std::max(0, firstGlobalChunk);
            t.line0 = (int)(diff / 43);
        } else {
            t.seqU += (seq - t.lastSeq) & 0x3fff; t.lastSeq = seq;
        }
        long rel = t.seqU - t.firstSeq - (chunk - t.firstChunk);
        if (rel != 0) { if (rel % 43 == 0) t.votes43++; if (rel % 14 == 0) t.votes14++; }
        int period = t.votes14 > t.votes43 ? 14 : 43;
        int line = t.line0 + (int)std::lround((double)rel / period);
        if (line < 0 || line > 4000) return;
        st.packets++;
        mcuRow(apid, line, mcu, q, d + 14, dn - 14);
    }

    void mcuRow(int apid, int line, int mcu, int q, const uint8_t* data, size_t n) {
        Channel& c = ch[apid];
        c.apid = apid;
        int rows = (line + 1) * 8;
        if (c.rows < rows) { c.pixels.resize((size_t)c.width * rows, 0); c.rows = rows; }
        double f = q < 50 ? 5000.0 / q : 200.0 - 2.0 * q;
        float qt[64];
        for (int i = 0; i < 64; i++) qt[i] = (float)std::max(1, (int)std::floor((STDQ[i] * f + 50) / 100));
        BitReader br(data, n);
        int prevDc = 0;
        for (int m = 0; m < 14; m++) {
            int coef[64] = {0};
            int cat = br.decode(dcH); if (cat < 0) return;
            int v = br.bits(cat); if (v < 0) return;
            prevDc += extend(v, cat); coef[0] = prevDc;
            int k = 1;
            while (k < 64) {
                int sym = br.decode(acH); if (sym < 0) return;
                if (sym == 0x00) break;
                if (sym == 0xf0) { k += 16; continue; }
                int run = sym >> 4, size = sym & 15;
                k += run; if (k > 63) break;
                int bv = br.bits(size); if (bv < 0) return;
                coef[ZIGZAG[k]] = extend(bv, size);
                k++;
            }
            float blk[64];
            for (int i = 0; i < 64; i++) blk[i] = coef[i] * qt[i];
            float tmp[64];
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) { float s = 0; for (int u = 0; u < 8; u++) s += cosT[x][u] * blk[y * 8 + u]; tmp[y * 8 + x] = s; }
            int px0 = (mcu + m) * 8; if (px0 + 8 > c.width) return;
            for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) {
                float s = 0; for (int vv = 0; vv < 8; vv++) s += cosT[y][vv] * tmp[vv * 8 + x];
                int val = (int)std::lround(s + 128); val = std::max(0, std::min(255, val));
                c.pixels[(size_t)(line * 8 + y) * c.width + px0 + x] = (uint8_t)val;
            }
        }
    }
};

Decoder::Decoder(double r) : d(new Impl(r)) {}
Decoder::~Decoder() { delete d; }
void Decoder::feed(const int8_t* iq, size_t pairs) {
    for (size_t k = 0; k < pairs; k++) d->sample(iq[2 * k] / 128.f, iq[2 * k + 1] / 128.f);
}
Stats Decoder::stats() const { return d->st; }
std::map<int, Channel>& Decoder::channels() { return d->ch; }

} // namespace lrpt
