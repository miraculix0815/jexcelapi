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

package jxl.read.biff;

import java.util.*;
import java.util.stream.Collectors;
import jxl.biff.*;

/**
 * Contains the cell dimensions of this worksheet
 */
public class VerticalPageBreaksRecord extends RecordData implements IVerticalPageBreaks {

  /**
   * The row page breaks
   */
  private final List<ColumnIndex> columnBreaks = new ArrayList<>();

  /**
   * Dummy indicators for overloading the constructor
   */
  private static class Biff7 {};
  public static final Biff7 biff7 = new Biff7();

  public VerticalPageBreaksRecord() {
    super(Type.VERTICALPAGEBREAKS);
  }
  /**
   * Constructs the dimensions from the raw data
   *
   * @param t the raw data
   */
  public VerticalPageBreaksRecord(Record t)
  {
    super(t);

    byte[] data = t.getData();

    int numbreaks = IntegerHelper.getInt(data[0], data[1]);
    int pos = 2;

    for (int i = 0; i < numbreaks; i++)
    {
      columnBreaks.add(new ColumnIndex(
              IntegerHelper.getInt(data[pos], data[pos + 1]),
              IntegerHelper.getInt(data[pos + 2], data[pos + 3]),
              IntegerHelper.getInt(data[pos + 4], data[pos + 5])));
      pos += 6;
    }
  }

  /**
   * Constructs the dimensions from the raw data
   *
   * @param t the raw data
   * @param biff7 an indicator to initialise this record for biff 7 format
   */
  public VerticalPageBreaksRecord(Record t, Biff7 biff7)
  {
    super(t);

    byte[] data = t.getData();
    int numbreaks = IntegerHelper.getInt(data[0], data[1]);
    int pos = 2;
    for (int i = 0; i < numbreaks; i++)
    {
      columnBreaks.add(new ColumnIndex(
              IntegerHelper.getInt(data[pos], data[pos + 1]),
              0,
              0xffff));
      pos += 2;
    }
  }

  /**
   * Gets the row breaks
   *
   * @return the row breaks on the current sheet
   */
  @Override
  public List<Integer> getColumnBreaks()
  {
    return columnBreaks.stream()
            .map(ColumnIndex::getFirstColumnFollowingBreak)
            .collect(Collectors.toList());
  }

}
