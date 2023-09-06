package com.example.customtelinkapp.model;

import java.io.Serializable;

public class OOBPair implements Serializable {
    /**
     * manual input in OOBEditActivity
     */
    public static final int IMPORT_MODE_MANUAL = 0;

    /**
     * batch import from valid formatted file
     */
    public static final int IMPORT_MODE_FILE = 1;

    /**
     * device UUID
     */
    public byte[] deviceUUID;

    /**
     * OOB value, used when device is static-oob supported
     */
    public byte[] oob;

    /**
     * @see #IMPORT_MODE_FILE
     * @see #IMPORT_MODE_MANUAL
     */
    public int importMode;

    /**
     * import time
     */
    public long timestamp;
}
