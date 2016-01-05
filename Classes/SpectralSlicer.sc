SpectralSlicer {
    var crossovers, numChannels, q, fftSize;
    var <inputBus, <outputBuses, numBands, defname, synths;

    *new {|crossovers, numChannels=2, q=1, fftSize=2048|
        ^super.newCopyArgs(crossovers ? [ 92, 4522, 11071 ], numChannels, q, fftSize).init;
    }

    init {
        numBands    = crossovers.size + 1;
        inputBus    = Bus.audio(Server.default, numChannels);
        outputBuses = { Bus.audio(Server.default, numChannels) }.dup(numBands);
        defname     = "spectral_slicer_band_%"; // TODO: add random id

        this.makeSynthDefs;
    }

    *calcPartitions {|crossovers, fftSize|
        var toBin = 0, fromBin = 0; // start from DC

        var numBins = fftSize div: 2;
        var binRes  = Server.default.sampleRate / fftSize;

        // get the partitions
        var endBins = crossovers.collect {|xfreq|
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
        endBins = endBins ++ numBins;

        ^endBins;
    }

    makeSynthDefs {
        var fromBin = 0; // start from DC
        var endBins, fadeInBins, fadeOutBins;

        endBins = SpectralSlicer.calcPartitions(crossovers, fftSize);

        endBins.do {|toBin, bandIdx|
            // first band
            if(bandIdx == 0) {
                fadeInBins  = 0;
                fadeOutBins = endBins[bandIdx + 1] div: 2;
            };

            // last band
            if(bandIdx == (endBins.size - 1)) {
                fadeInBins  = endBins[bandIdx - 1] div: 2;
                fadeOutBins = 0;
            };

            // n bands
            if(bandIdx != 0 and:{bandIdx != (endBins.size - 1)}) {
                fadeInBins  = endBins[bandIdx - 1] div: 2;
                fadeOutBins = endBins[bandIdx + 1] div: 2;
            };

            SynthDef(defname.format(bandIdx).asSymbol, {
                |
                    amp  = 1,
                    out  = 0,
                    in
                |
                var sig, fftBuf, binRes, chain;

                sig = In.ar(in, numChannels);

                if(numChannels > 1) {
                    fftBuf = { LocalBuf(fftSize) }.dup(numChannels);
                } {
                    fftBuf = LocalBuf(fftSize);
                };

                chain = FFT(fftBuf, sig);
                chain = chain.collect {|monoChain|
                    // indicies for fade in/out curves
                    var fadeInIdx = 0, fadeOutIdx = 0;

                    monoChain.pvcollect(
                        fftSize,
                        {|mag, phase, binIdx, idx|
                            // fade in
                            if(binIdx < fromBin) {
                                mag = mag * sin((fadeInIdx / (fadeInBins - 1)) * 0.5pi).sqrt;
                                fadeInIdx = fadeInIdx + 1;
                            };

                            // fade out
                            // TODO: start fade out from last bin instead? (>=)
                            if(binIdx > toBin) {
                                mag = mag * cos((fadeOutIdx / (fadeOutBins - 1)) * 0.5pi).sqrt;
                                fadeOutIdx = fadeOutIdx + 1;
                            };

                            [mag, phase];
                        },
                        frombin:    fromBin - fadeInBins,
                        tobin:      toBin   + fadeOutBins,
                        zeroothers: 1
                    );
                };

                // [ fromBin, toBin ].debug('bins');

                Out.ar(out, IFFT(chain) * amp);
            }).load(Server.default);

            fromBin = toBin; // start from last partition bin
        };
    }

    play {|addAction=\addToHead, target, time|
        Server.default.makeBundle(time, {
            synths = numBands.collect {|i|
                Synth(
                    defname.format(i),
                    [
                        \in,  inputBus,
                        \out, outputBuses[i]
                    ],
                    target ? Server.default,
                    addAction
                );
            };
        });
    }

    free {
        try {
            synths.do(_.free);
        }
    }
}
