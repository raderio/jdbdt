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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@SuppressWarnings("javadoc")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MiscTest {

  private static final byte[] ZBA = { };
  private static final String ZSTR = "";
  
  @Test
  public void testToHexString1() {
    long value = 0xa0_0a_1c_c1_19_91_f9_9fL;
    byte[] data = ByteBuffer.allocate(8).putLong(value).array();
    String hs = Misc.toHexString(data);
    assertEquals(Long.toHexString(value), hs);
  }
  
  @Test
  public void testToHexString2() {
    assertEquals(ZSTR, Misc.toHexString(ZBA));
  }
  
  @Test
  public void testFromHexString1() {
    long value = 0xa0_0a_1c_c1_19_91_f9_9fL;
    byte[] expected = ByteBuffer.allocate(8).putLong(value).array();
    byte[] actual = Misc.fromHexString(Long.toHexString(value));
    assertArrayEquals(expected, actual);
  }
  
  @Test
  public void testFromHexString2() {
    long value = 0xa0_0a_1c_c1_19_91_f9_9fL;
    byte[] expected = ByteBuffer.allocate(8).putLong(value).array();
    byte[] actual = Misc.fromHexString(Long.toHexString(value).toUpperCase());
    assertArrayEquals(expected, actual);
  }
  
  @Test
  public void testFromHexString3() {
    assertArrayEquals(ZBA, Misc.fromHexString(ZSTR));
  }
  
  @Test(expected=InvalidOperationException.class)
  public void testFromHexString4() {
    Misc.fromHexString("a1f");
  }
  
  @Test(expected=InvalidOperationException.class)
  public void testFromHexString5() {
    Misc.fromHexString("a1fg");
  }
  
  @Test(expected=InvalidOperationException.class)
  public void testFromHexString6() {
    Misc.fromHexString("a1f/");
  }
  
  private static void testSHA1(String inputAsString, String expectedSHA1AsString) {
    byte[] expected = Misc.fromHexString(expectedSHA1AsString);
    byte[] actual =  Misc.sha1(new ByteArrayInputStream(inputAsString.getBytes()));
    assertArrayEquals(expected, actual);
  }
  
  @Test
  public void testSHA1EmptyInput() {
    testSHA1("", "da39a3ee5e6b4b0d3255bfef95601890afd80709");
  }
  
  @Test
  public void testSHA1NonEmptyInput() {
    testSHA1("JDBDT: Java Database Delta Testing\n", "cff338d07e764c00e05c9d92abf35898f452301e");
  }
  
  @Test (expected=InternalErrorException.class)
  public void testSHA1WithFileReadError() throws IOException {
    InputStream in = mock(InputStream.class);
    when(in.read(any())).thenThrow(new IOException());
    Misc.sha1(in);
  }
  
  @Test(expected=NullPointerException.class)
  public void testSqlArgList1() {
    Misc.sqlArgumentList((Object[]) null);
  }
  
  @Test
  public void testSqlArgList2() {
    assertEquals("", Misc.sqlArgumentList());
  }
  
  @Test
  public void testSqlArgList3() {
    assertEquals("1", Misc.sqlArgumentList(1));
  }
  
  @Test
  public void testSqlArgList4() {
    assertEquals("1, a2, a3", Misc.sqlArgumentList(1, "a2", "a3"));
  }
}
