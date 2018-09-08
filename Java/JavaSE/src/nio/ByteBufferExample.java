package nio;

import java.nio.ByteBuffer;

public class ByteBufferExample {

	public static void main(String[] args) {
		System.out.println(">> alignedSlice, alignmentOffset");
		var bytes = new byte[27];
		for(byte i = 1; i <= bytes.length; ++i)
			bytes[i-1] = i;
		var byteBuf = ByteBuffer.wrap(bytes);
		var intSizedBuf = byteBuf.alignedSlice(Integer.BYTES);

		System.out.println(byteBuf.limit() + "->" + intSizedBuf.limit());
		while(intSizedBuf.hasRemaining())
			System.out.print(intSizedBuf.get() + " ");
		System.out.println();

		for(var i = 0; i < bytes.length; ++i)
			System.out.print(intSizedBuf.alignmentOffset(i, Integer.BYTES) + " ");
		System.out.println();

		byteBuf.get();
		intSizedBuf = byteBuf.alignedSlice(Integer.BYTES);
		while(intSizedBuf.hasRemaining())
			System.out.print(intSizedBuf.get() + " ");
		System.out.println();

		for(var i = 0; i < bytes.length; ++i)
			System.out.print(intSizedBuf.alignmentOffset(i, Integer.BYTES) + " ");
		System.out.println("<- No upper bounds check.");
	}

}
