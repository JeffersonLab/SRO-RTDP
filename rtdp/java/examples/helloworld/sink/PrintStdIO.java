package org.jlab.ersap.actor.helloworld.sink;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.HexFormat;

//import org.apache.commons.codec.binary.Hex;

public class PrintStdIO {

    public void printStdIo (String value) {
        System.out.println(HexFormat.of().formatHex(value.getBytes()));
    }
}
