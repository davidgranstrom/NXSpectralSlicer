SpectralSlicer {

    *ar {|sig, crossovers, fftSize=4096, q=1|
        var fromBin      = 0; // start from DC
        var numChannels  = sig.size;

        var endPointBins = SpectralSlicer.calcEndPointBins(crossovers ? [ 92, 4522, 11071 ], fftSize);
        var binRes       = Server.default.sampleRate / fftSize;

        var fadeOutMult  = 1.15;
        var fadeInMult   = 0.85;

        // return array of bands
        ^endPointBins.collect {|toBin, bandIdx|
            var fadeInBins, fadeOutBins;
            var chain, fftBuf;

            // first band
            if(bandIdx == 0) {
                fadeInBins  = 0;
                fadeOutBins = toBin * fadeOutMult;
            };

            // last band
            if(bandIdx == (endPointBins.size - 1)) {
                fadeInBins  = fromBin * fadeInMult;
                fadeOutBins = 0;
            };

            // n bands
            if(bandIdx != 0 and:{bandIdx != (endPointBins.size - 1)}) {
                fadeInBins  = fromBin * fadeInMult;
                fadeOutBins = toBin * fadeOutMult;
            };

            fadeInBins  = fadeInBins.floor.asInteger;
            fadeOutBins = fadeOutBins.floor.asInteger;

            if(numChannels > 1) {
                fftBuf = { LocalBuf(fftSize) }.dup(numChannels);
            } {
                fftBuf = LocalBuf(fftSize);
            };

            chain = FFT(fftBuf, sig);
            chain = chain.collect {|monoChain|
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

            // start next iteration from last bin
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
