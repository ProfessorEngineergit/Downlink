// Meteor-M LRPT decoder: OQPSK 72 ksym/s baseband in, MSU-MR channel images out.
// Written for Downlink from the CCSDS and Meteor LRPT descriptions; no external dependencies.
#pragma once
#include <cstddef>
#include <cstdint>
#include <map>
#include <vector>

namespace lrpt {

struct Channel {
    int apid = 0;
    int width = 1568;
    int rows = 0;                  // pixel rows
    std::vector<uint8_t> pixels;   // width * rows, greyscale
};

struct Stats {
    long symbols = 0;
    long frames = 0;      // CADUs with a valid sync word
    long packets = 0;     // MSU-MR image packets decoded
    float snr = 0;        // rough estimate from the soft symbol spread, dB
    int locked = 0;       // 1 while frames keep arriving
};

class Decoder {
public:
    explicit Decoder(double sampleRate = 192000.0);
    ~Decoder();
    // interleaved signed 8-bit I/Q pairs
    void feed(const int8_t* iq, size_t pairs);
    Stats stats() const;
    std::map<int, Channel>& channels();
private:
    struct Impl;
    Impl* d;
};

} // namespace lrpt
