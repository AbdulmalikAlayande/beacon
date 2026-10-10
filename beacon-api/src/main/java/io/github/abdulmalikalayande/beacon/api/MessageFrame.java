package io.github.abdulmalikalayande.beacon.api;

public class MessageFrame {
	
	static byte[] frameHeader = {(byte) 0x81, (byte) 0x85};
	
	public static void main(String[] args) {
		
		boolean isFin = (frameHeader[0] & 0x80) != 0;
		int opcode = frameHeader[0] & 0x0F;
		boolean isMasked = (frameHeader[1] & 0x80) != 0;
		int payloadLen = frameHeader[1] & 0x7F;
		
		System.out.println("FIN: " + isFin);
		System.out.println("Opcode: " + opcode + " (Text)");
		System.out.println("Masked: " + isMasked);
		System.out.println("Payload Length: " + payloadLen);
	}
}