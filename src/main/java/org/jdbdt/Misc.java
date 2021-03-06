/*
 * The MIT License
 *
 * Copyright (c) 2016-2018 Eduardo R. B. Marques
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.jdbdt;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


/**
 * Utility class grouping miscellaneous functionality.
 * 
 * @since 1.0
 *
 */
final class Misc {
  /**
   * Private constructor to prevent instantiation. 
   */
  private Misc() { } 

  @SuppressWarnings("javadoc")
  private static final char[] HEX_CHARS = {
      '0', '1', '2', '3', '4', '5', '6', '7', 
      '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
  };

  /**
   * Convert byte array to a "hexa"-string.
   * @param data Byte array to convert.
   * @return "Hexa-string" representation.
   */
  static String toHexString(byte[] data) {
    char[] chArray = new char[data.length * 2];
    int pos = 0;
    for (byte b : data) {
      chArray[pos++] = HEX_CHARS[(b >> 4) & 0x0f];
      chArray[pos++] = HEX_CHARS[b & 0x0f];
    }
    return new String(chArray);
  }

  /**
   * Convert a "hexa"-string to a byte array.
   * @param str Input string.
   * @return Corresponding array of bytes.
   */
  static byte[] fromHexString(String str) {
    if (str.length() % 2 != 0) {
      throw new InvalidOperationException("Hex-string has odd length!");
    }
    byte[] data = new byte[str.length() / 2];
    int spos = 0;
    for (int dpos = 0; dpos < data.length; dpos++) {
      int d1 = Character.digit(str.charAt(spos++), 16);
      int d2 = Character.digit(str.charAt(spos++), 16);
      if (d1 < 0 || d2 < 0) {
        throw new InvalidOperationException("Mal-formed hex-string!");
      }
      data[dpos] = (byte) ((d1 << 4) | d2);
    }
    return data;
  }

  /** SHA-1 digest constant. */
  private static final String SHA1_DIGEST = "SHA-1";

  /**
   * Compute SHA-1 hash value for a given input stream.
   * @param in Input stream
   * @return SHA-1 hash value (array of 20 bytes).
   */
  static byte[] sha1(InputStream in) {
    try {
      MessageDigest md = MessageDigest.getInstance(SHA1_DIGEST); 
      byte[] buffer = new byte[4096];
      int bytes;
      while ( (bytes = in.read(buffer)) > 0) {
        md.update(buffer, 0, bytes);
      }
      return md.digest();
    }
    catch(NoSuchAlgorithmException | IOException e) {
      throw new InternalErrorException(e);
    }
  }

  /**
   * Obtain string for SQL argument list from array.
   * @param <T> Type of data.
   * @param values Array of values.
   * @return CSV string
   */
  @SafeVarargs
  static <T> String sqlArgumentList(T... values) {
    StringBuilder sb = new StringBuilder();
    if (values.length != 0) {
      sb.append(values[0]);
      for (int i=1; i < values.length; i++) {
        sb.append(',').append(' ').append(values[i]);
      }
    }
    return sb.toString();
  }
}
