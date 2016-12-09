package indexer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class VByte {
	public static void encode(OutputStream out, long num) {
		while(num > 127) {
			try {
			    out.write((int)(num & 127));
			    num>>>=7;
			} catch (IOException e) {
			    e.printStackTrace();
			}
		}
		try {
		    out.write((int)(num|128));
		} catch (IOException e) {
		    e.printStackTrace();
		}
	}
	public static long decode(InputStream in) {
		long num = 0;
		int shift=0;
		long readByte;
		try {
		    readByte = in.read();
		    while( (readByte & 128)==0) {
			num |= (readByte & 127) << shift;
			readByte = in.read();
			shift+=7;
		    }
		    num |= (readByte & 127) << shift;
		} catch (IOException e) {
		    e.printStackTrace();
		}
		return num;
	}
}
