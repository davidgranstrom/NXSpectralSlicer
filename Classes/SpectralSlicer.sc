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

    makeSynthDefs {
        var toBin, fromBin = 0; // start from DC

        var numBins = fftSize div: 2;
        var binRes  = Server.default.sampleRate / fftSize;

        numBands.do {|i|
            // get the partitions
            if(i < (numBands - 1)) {
                toBin = block {|break|
                    numBins.do {|binIdx|
                        var freq = binRes * binIdx;

                        if(freq >= crossovers[i]) {
                            break.(binIdx);
                        }
                    };
                };
            } {
                // last partiation, last crossover -> full sr
                toBin = numBins;
            };

            SynthDef(defname.format(i).asSymbol, {
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
                    monoChain.pvcollect(
                        fftSize,
                        {|mag, phase, binIdx| [mag, phase] }, // unaltered
                        frombin:    fromBin,
                        tobin:      toBin,
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
