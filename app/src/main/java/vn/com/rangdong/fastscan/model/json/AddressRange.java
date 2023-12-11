package vn.com.rangdong.fastscan.model.json;

import java.io.Serializable;

public class AddressRange implements Serializable {
    public int low;
    public int high;

    public AddressRange() {
    }

    public AddressRange(int low, int high) {
        this.low = low;
        this.high = high;
    }
}
