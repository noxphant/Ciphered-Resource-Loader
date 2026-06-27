package top.wyatt.core.pack;

public final class CipheredPackFormat {
    private CipheredPackFormat() {}

    public static final byte[] MAGIC = {'C', 'R', 'L', 'P'};
    public static final int MAGIC_LENGTH = MAGIC.length;
    public static final int CURRENT_VERSION = 1;
}