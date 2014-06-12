public class BitDecoding {

    public static byte[] decodeString(String s) {
        byte[] bytes = string2Bytes(s);
        // Adding 0x7f modulo max byte value is equivalent to subtracting 1 the same modulo, which is inverse to what happens in encodeBytes
        addModuloByte(bytes, 0x7f);
        return decode7to8(bytes);
    }

    private static void addModuloByte(byte[] data, int increment) {
        for (int i = 0, n = data.length; i < n; i++) {
            data[i] = (byte) ((data[i] + increment) & 0x7f);
        }
    }

    /**
     * Combines the array of strings resulted from encodeBytes() into one long byte array
     */
    private static byte[] string2Bytes(String data) {
        int resultLength = data.length();

        byte[] result = new byte[resultLength];
        for (int i = 0, n = data.length(); i < n; i++) {
            result[i] = (byte) data.charAt(i);
        }

        return result;
    }

    /**
     * Decodes the byte array resulted from encode8to7().
     * <p/>
     * Each byte of the input array has at most 7 valuable bits of information. So the decoding is equivalent to the following: least
     * significant 7 bits of all input bytes are combined into one long bit string. This bit string is then split into groups of 8 bits,
     * each of which forms a byte in the output. If there are any leftovers, they are ignored, since they were added just as a padding and
     * do not comprise a full byte.
     * <p/>
     * Suppose the following encoded byte array is given (bits are numbered the same way as in encode8to7() doc):
     * <p/>
     * 01234567 01234567 01234567 01234567
     * <p/>
     * The output of the following form would be produced:
     * <p/>
     * 01234560 12345601 23456012
     * <p/>
     * Note how all most significant bits and leftovers are dropped, since they don't contain any useful information
     */
    private static byte[] decode7to8(byte[] data) {
        // floor(7 * data.length / 8)
        int resultLength = 7 * data.length / 8;

        byte[] result = new byte[resultLength];

        // We maintain a pointer to an input bit in the same fashion as in encode8to7(): it's represented as two numbers: index of the
        // current byte in the input and index of the bit in the byte
        int byteIndex = 0;
        int bit = 0;

        // A resulting byte is comprised of 8 bits, starting from the current bit. Since each input byte only "contains 7 bytes", a
        // resulting byte always consists of two parts: several most significant bits of the current byte and several least significant bits
        // of the next byte
        for (int i = 0; i < resultLength; i++) {
            int firstPart = (data[byteIndex] & 0xff) >>> bit;
            byteIndex++;
            int secondPart = (data[byteIndex] & ((1 << (bit + 1)) - 1)) << 7 - bit;
            result[i] = (byte) (firstPart + secondPart);

            if (bit == 6) {
                byteIndex++;
                bit = 0;
            } else {
                bit++;
            }
        }

        return result;
    }
}
