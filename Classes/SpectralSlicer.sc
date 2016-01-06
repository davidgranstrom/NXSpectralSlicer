SpectralSlicer {
    // Caution: Calling this method may suspend the client while sending the
    // synthdef to the server due to the large processing graph.
    //
    *ar {|sig, crossovers, fftSize=2048, q=1|
        var fromBin      = 0; // start from DC
        var overlapSize  = 4;
        var numChannels  = sig.size;
        var endPointBins = SpectralSlicer.calcEndPointBins(crossovers ? [ 92, 4522, 11071 ], fftSize);

        // return array of bands
        ^endPointBins.collect {|toBin, bandIdx|
            var fadeInBins, fadeOutBins;
            var chain, fftBuf;

            // first band
            if(bandIdx == 0) {
                fadeInBins  = 0;
                // fadeOutBins = endPointBins[bandIdx + 1] div: overlapSize;
                fadeOutBins = toBin * 1.25;
            };

            // last band
            if(bandIdx == (endPointBins.size - 1)) {
                // fadeInBins  = endPointBins[bandIdx - 1] div: overlapSize;
                fadeInBins  = fromBin * 0.25;
                fadeOutBins = 0;
            };

            // n bands
            if(bandIdx != 0 and:{bandIdx != (endPointBins.size - 1)}) {
                // fadeInBins  = endPointBins[bandIdx - 1] div: overlapSize;
                // fadeOutBins = endPointBins[bandIdx + 1] div: overlapSize;
                fadeInBins  = fromBin * 0.25;
                fadeOutBins = toBin * 1.25;
            };

            fadeInBins  = fadeInBins.round.asInteger;
            fadeOutBins = fadeOutBins.round.asInteger;

            if(numChannels > 1) {
                fftBuf = { LocalBuf(fftSize) }.dup(numChannels);
            } {
                fftBuf = LocalBuf(fftSize);
            };

            // fadeInBins.debug("fadeInBins" + bandIdx);
            // fadeOutBins.debug("fadeOutBins" + bandIdx);
            // (fadeInBins * (Server.default.sampleRate / fftSize)).debug("fadeInFreq" + bandIdx);
            // (fadeOutBins * (Server.default.sampleRate / fftSize)).debug("fadeOutFreq" + bandIdx);

            chain = FFT(fftBuf, sig);
            chain = chain.collect {|monoChain|
                // indicies for fade in/out curves
                var fadeInIdx = 0, fadeOutIdx = 0;

                monoChain.pvcollect(
                    fftSize,
                    {|mag, phase, binIdx, idx|
                        // fade in
                        if(binIdx < fromBin) {
                            mag = mag * (sin((fadeInIdx / (fadeInBins - 1)) * 0.5pi).sqrt ** q);
                            fadeInIdx = fadeInIdx + 1;
                        };

                        // fade out
                        // TODO: start fade out from last bin instead? (>=)
                        if(binIdx > toBin) {
                            mag = mag * (cos((fadeOutIdx / (fadeOutBins - 1)) * 0.5pi).sqrt ** q);
                            fadeOutIdx = fadeOutIdx + 1;
                        };

                        [mag, phase];
                    },
                    frombin:    fromBin - fadeInBins,
                    tobin:      toBin   + fadeOutBins,
                    zeroothers: 1
                );
            };

            fromBin = toBin;

            IFFT(chain);
        };
    }

    *calcEndPointBins {|crossovers, fftSize|
        var toBin = 0, fromBin = 0; // start from DC

        var numBins = fftSize div: 2;
        var binRes  = Server.default.sampleRate / fftSize;

        // get the partitions
        var endPointBins = crossovers.collect {|xfreq|
            toBin = block {|break|
                numBins.do {|binIdx|
                    var freq = binRes * binIdx;

                    if(freq >= xfreq) {
                        break.(binIdx);
                    }
                };
            };
        };

        // last end point is the full spectrum
        endPointBins = endPointBins ++ numBins;

        ^endPointBins;
    }
}
