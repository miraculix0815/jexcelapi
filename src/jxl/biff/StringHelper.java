/*********************************************************************
*
*      Copyright (C) 2002 Andrew Khan
*
* This library is free software; you can redistribute it and/or
* modify it under the terms of the GNU Lesser General Public
* License as published by the Free Software Foundation; either
* version 2.1 of the License, or (at your option) any later version.
*
* This library is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this library; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
***************************************************************************/

package jxl.biff;

import java.io.UnsupportedEncodingException;

import jxl.common.Logger;

import jxl.WorkbookSettings;

/**
 * Helper function to convert Java string objects to and from the byte
 * representations
 */
public final class StringHelper
{
  /**
   * The logger
   */
  private static Logger logger = Logger.getLogger(StringHelper.class);

  // Due to a a Sun bug in some versions of JVM 1.4, the UnicodeLittle
  // encoding doesn't always work.  Making this a public static field
  // enables client code access to this (but in an undocumented and
  // unsupported fashion).  Suggested alternative values for this
  // are  "UTF-16LE" or "UnicodeLittleUnmarked"
  public static String UNICODE_ENCODING = "UnicodeLittle";

  /**
   * Private default constructor to prevent instantiation
   */
  private StringHelper()
  {
  }

  /**
   * Gets the bytes of the specified string.  This will simply return the ASCII
   * values of the characters in the string
   *
   * @param s the string to convert into bytes
   * @return the ASCII values of the characters in the string
   * @deprecated
   */
  public static byte[] getBytes(String s)
  {
    return s.getBytes();
  }

  /**
   * Gets the bytes of the specified string.  This will simply return the ASCII
   * values of the characters in the string
   *
   * @param s the string to convert into bytes
   * @return the ASCII values of the characters in the string
   */
  public static byte[] getBytes(String s, WorkbookSettings ws)
  {
    try
    {
      return s.getBytes(ws.getEncoding());
    }
    catch (UnsupportedEncodingException e)
    {
      // fail silently
      return null;
    }
  }

  /**
   * Converts the string into a little-endian array of Unicode bytes
   *
   * @param s the string to convert
   * @return the unicode values of the characters in the string
   */
  public static byte[] getUnicodeBytes(String s)
  {
    try
    {
      byte[] b = s.getBytes(UNICODE_ENCODING);

      // Sometimes this method writes out the unicode
      // identifier
      if (b.length == (s.length() * 2 + 2))
      {
        byte[] b2 = new byte[b.length - 2];
        System.arraycopy(b, 2, b2, 0, b2.length);
        b = b2;
      }
      return b;
    }
    catch (UnsupportedEncodingException e)
    {
      // Fail silently
      return null;
    }
  }


  /**
   * Gets the ASCII bytes from the specified string and places them in the
   * array at the specified position
   *
   * @param pos the position at which to place the converted data
   * @param s the string to convert
   * @param d the byte array which will contain the converted string data
   */
  public static void getBytes(String s, byte[] d, int pos)
  {
    byte[] b = getBytes(s);
    System.arraycopy(b, 0, d, pos, b.length);
  }

/**
   * Gets the ASCII bytes from the specified string and places them in the
   * array at the specified position
   *
   * @param pos the position at which to place the converted data
   * @param s the string to convert
   * @param d the byte array which will contain the converted string data
   */
  public static void getBytes(String s, byte[] d, int pos, WorkbookSettings ws)
  {
    byte[] b = getBytes(s, ws);
    System.arraycopy(b, 0, d, pos, b.length);
  }

  /**
   * Inserts the unicode byte representation of the specified string into the
   * array passed in
   *
   * @param pos the position at which to insert the converted data
   * @param s the string to convert
   * @param d the byte array which will hold the string data
   */
  public static void getUnicodeBytes(String s, byte[] d, int pos)
  {
    byte[] b = getUnicodeBytes(s);
    System.arraycopy(b, 0, d, pos, b.length);
  }

  /**
   * Gets a string from the data array using the character encoding for
   * this workbook
   *
   * @param pos The start position of the string
   * @param length The number of bytes to transform into a string
   * @param d The byte data
   * @param ws the workbook settings
   * @return the string built up from the raw bytes
   */
  public static String getString(byte[] d, int length, int pos,
                                 WorkbookSettings ws)
  {
    if( length == 0 )
    {
      return "";  // Reduces number of new Strings
    }

    try
    {
      return new String(d, pos, length, ws.getEncoding());
      //      byte[] b = new byte[length];
      //      System.arraycopy(d, pos, b, 0, length);
      //      return new String(b, ws.getEncoding());
    }
    catch (UnsupportedEncodingException e)
    {
      logger.warn(e.toString());
      return "";
    }
  }

  public static String readBiff8String(byte[] data) {
    return readBiff8String(data, 0);
  }

  public static String readBiff8String(byte[] data, int offset) {
    int numberOfChars = IntegerHelper.getInt(data[offset], data[offset+1]);

    if (numberOfChars == 0)
      return "";

    int optionFlags = data[offset+2];
    boolean compressedUFT16 = (optionFlags & 0x01) == 0;
    boolean containsAsianPhoneticSettings = ((optionFlags & 0x04) != 0);
    boolean containsRichTextSettings = ((optionFlags & 0x08) != 0);

    int start = 3;
    if (containsRichTextSettings) {
      int numberOfRtRuns = IntegerHelper.getInt(data[offset+start], data[offset+start+1]);
      start += 2;
    }

    if (containsAsianPhoneticSettings) {
      int phoneticSize = IntegerHelper.getInt(data[offset+start], data[offset+start+1], data[offset+start+2], data[offset+start+3]);
      start += 4;
    }

    if (compressedUFT16)
      return getCompressedUnicodeString(data, offset+start, numberOfChars);
    else
      return getUnicodeString(data, offset+start, numberOfChars);
  }

  public static String readShortBiff8String(byte[] data) {
    int length = data.length;
    boolean compressedUFT16 = (data[0] & 0x01) == 0;
    if (compressedUFT16)
      return getCompressedUnicodeString(data, 1, length - 1);
    else
      return getUnicodeString(data, 1, (length-1) / 2);
  }


  /**
   * Gets a string from the data array when compressed
   *
   * A compressed string, omits the high bytes of all characters, if they are
   * all zero. See "The Microsoft Excel File Format"
   * Chapter 2.5.3 Unicode Strings (BIFF8).
   *
   * @param d The byte data
   * @param length The number of characters to be converted into a string
   * @param start The start position of the string
   * @return the string built up from the unicode characters
   */
  public static String getCompressedUnicodeString(byte[] d, int start, int length) {
    byte[] b = new byte[length * 2];
    for (int i = 0; i < length; i++)
      b[i*2] = d[i + start];

    return getUnicodeString(b, 0, length);
  }

  /**
   * Gets a string from the data array
   *
   * @param start The start position of the string
   * @param length The number of characters to be converted into a string
   * @param d The byte data
   * @return the string built up from the unicode characters
   */
  public static String getUnicodeString(byte[] d, int start, int length)
  {
    try
    {
      byte[] b = new byte[length * 2];
      System.arraycopy(d, start, b, 0, length * 2);
      return new String(b, UNICODE_ENCODING);
    }
    catch (UnsupportedEncodingException e)
    {
      // Fail silently
      return "";
    }
  }

  /**
   * Replaces all instances of search with replace in the input.
   * Even though later versions of java can use string.replace()
   * this is included Java 1.2 compatibility
   *
   * @param input the format string
   * @param search the Excel character to be replaced
   * @param replace the java equivalent
   * @return the input string with the specified substring replaced
   */
  public static final String replace(String input,
                                     String search,
                                     String replace)
  {
    String fmtstr = input;
    int pos = fmtstr.indexOf(search);
    while (pos != -1)
    {
      StringBuffer tmp = new StringBuffer(fmtstr.substring(0, pos));
      tmp.append(replace);
      tmp.append(fmtstr.substring(pos + search.length()));
      fmtstr = tmp.toString();
      pos = fmtstr.indexOf(search, pos+replace.length());
    }
    return fmtstr;
  }
}


