package vn.com.rangdong.fastscan.model;

public abstract class AppSettings {
    /**
     * is online-status enabled
     */
    public static boolean ONLINE_STATUS_ENABLE = false;

    // draft feature
    public static final boolean DRAFT_FEATURES_ENABLE = false;


    public static final int PID_CT = 0x01;

    public static final int PID_HSL = 0x02;

    public static final int PID_PANEL = 0x07;

    public static final int PID_LPN = 0x0201;

    public static final int PID_REMOTE = 0x0301;

}
