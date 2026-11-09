// Host test for the LRPT decoder: decodes a .s8 baseband file and writes one PGM per channel.
#include "../../app/src/main/cpp/lrpt/lrpt.h"
#include <cstdio>
#include <vector>
int main(int argc, char** argv) {
    if (argc < 3) { std::fprintf(stderr, "usage: %s in.s8 outprefix [rate]\n", argv[0]); return 1; }
    double rate = argc > 3 ? std::atof(argv[3]) : 192000.0;
    FILE* f = std::fopen(argv[1], "rb"); if (!f) return 1;
    lrpt::Decoder dec(rate);
    std::vector<int8_t> buf(1 << 16);
    size_t n;
    while ((n = std::fread(buf.data(), 1, buf.size(), f)) > 0) dec.feed(buf.data(), n / 2);
    std::fclose(f);
    auto s = dec.stats();
    std::printf("symbols %ld frames %ld packets %ld snr %.1f\n", s.symbols, s.frames, s.packets, s.snr);
    for (auto& [apid, c] : dec.channels()) {
        char name[512]; std::snprintf(name, sizeof name, "%s_%d.pgm", argv[2], apid);
        FILE* o = std::fopen(name, "wb");
        std::fprintf(o, "P5\n%d %d\n255\n", c.width, c.rows);
        std::fwrite(c.pixels.data(), 1, c.pixels.size(), o); std::fclose(o);
        std::printf("apid %d: %d x %d\n", apid, c.width, c.rows);
    }
}
