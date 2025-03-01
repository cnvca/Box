package okhttp3.dnsoverhttps;

import java.io.EOFException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import okio.Buffer;
import okio.ByteString;

class DnsRecordCodec {
    public static final int TYPE_A = 0x0001;
    public static final int TYPE_AAAA = 0x001c;

    public static ByteString encodeQuery(String host, int type) {
        Buffer buf = new Buffer();

        buf.writeShort(0); // query id
        buf.writeShort(256); // flags with recursion
        buf.writeShort(1); // question count
        buf.writeShort(0); // answerCount
        buf.writeShort(0); // authorityResourceCount
        buf.writeShort(0); // additional

        Buffer nameBuf = new Buffer();
        String[] labels = host.split("\\.");
        for (String label : labels) {
            nameBuf.writeByte((byte) label.length());
            nameBuf.writeUtf8(label);
        }
        nameBuf.writeByte(0); // end

        nameBuf.copyTo(buf, 0, nameBuf.size());
        buf.writeShort(type);
        buf.writeShort(1); // CLASS_IN

        return buf.readByteString();
    }

    public static List<InetAddress> decodeAnswers(String hostname, ByteString byteString) throws UnknownHostException {
        List<InetAddress> result = new ArrayList<>();

        Buffer buf = new Buffer();
        buf.write(byteString);

        buf.readShort(); // query id
        int flags = buf.readShort() & 0xffff;
        if ((flags >> 15) == 0) {
            throw new IllegalArgumentException("not a response");
        }

        byte responseCode = (byte) (flags & 0xf);
        if (responseCode == 3) { // NXDOMAIN
            throw new UnknownHostException(hostname + ": NXDOMAIN");
        } else if (responseCode == 2) { // SERVFAIL
            throw new UnknownHostException(hostname + ": SERVFAIL");
        }

        buf.readShort(); // question count
        int answerCount = buf.readShort() & 0xffff;
        buf.readShort(); // authority record count
        buf.readShort(); // additional record count

        for (int i = 0; i < answerCount; i++) {
            skipName(buf); // name
            int type = buf.readShort() & 0xffff;
            buf.readShort(); // class
            buf.readInt(); // ttl
            int length = buf.readShort() & 0xffff;

            if (type == TYPE_A || type == TYPE_AAAA) {
                byte[] bytes = new byte[length];
                buf.read(bytes);
                result.add(InetAddress.getByAddress(bytes));
            } else {
                buf.skip(length);
            }
        }

        return result;
    }

    private static void skipName(Buffer in) throws EOFException {
        int length = in.readByte() & 0xff;

        if (length >= 0xc0) {
            in.skip(1); // compressed name pointer
        } else {
            while (length > 0) {
                in.skip(length);
                length = in.readByte() & 0xff;
            }
        }
    }
}
