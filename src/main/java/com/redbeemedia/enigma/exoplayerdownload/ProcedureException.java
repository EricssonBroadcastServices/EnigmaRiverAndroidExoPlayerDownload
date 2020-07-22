package com.redbeemedia.enigma.exoplayerdownload;

import com.redbeemedia.enigma.core.error.EnigmaError;

/*package-protected*/ class ProcedureException extends Exception {
    public final EnigmaError error;

    public ProcedureException(EnigmaError error) {
        this.error = error;
    }
}
