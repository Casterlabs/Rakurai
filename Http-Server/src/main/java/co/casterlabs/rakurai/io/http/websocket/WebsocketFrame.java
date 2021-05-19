package co.casterlabs.rakurai.io.http.websocket;

import co.casterlabs.rakurai.StringUtil;

public abstract class WebsocketFrame {
    // @formatter:off
    private static final String TOSTRING_TEXT_FORMAT = 
            "WebsocketFrame("  + "\n" + 
            "    type=TEXT"    + "\n" + 
            "    text=%s"      + "\n" + 
            ")"
            ;
    private static final String TOSTRING_BIN_FORMAT = 
            "WebsocketFrame("  + "\n" + 
            "    type=BINARY"  + "\n" + 
            "    len=%d"       + "\n" + 
            "    bytes=%s"     + "\n" + 
            ")"
            ;
    // @formatter:on

    public abstract WebsocketFrameType getFrameType();

    public abstract String getAsText();

    public abstract byte[] getBytes();

    public abstract int getSize();

    @Override
    public String toString() {
        if (this.getFrameType() == WebsocketFrameType.BINARY) {
            return String.format(TOSTRING_BIN_FORMAT, StringUtil.bytesToHex(this.getBytes()), this.getSize());
        } else {
            return String.format(TOSTRING_TEXT_FORMAT, this.getAsText());
        }
    }

}
