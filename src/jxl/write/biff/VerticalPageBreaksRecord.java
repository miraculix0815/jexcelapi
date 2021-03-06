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

package jxl.write.biff;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import jxl.biff.*;
import jxl.read.biff.IVerticalPageBreaks;

/**
 * Contains the list of explicit horizontal page breaks on the current sheet
 */
class VerticalPageBreaksRecord extends WritableRecordData implements IVerticalPageBreaks {
  
  /**
   * The row breaks
   */
  private List<ColumnIndex> columnBreaks = new ArrayList<>();
  
  /**
   * Constructor
   * 
   * @param break the row breaks
   */
  VerticalPageBreaksRecord()
  {
    super(Type.VERTICALPAGEBREAKS);
  }

  /**
   * Gets the binary data to write to the output file
   * 
   * @return the binary data
   */
  @Override
  public byte[] getData()
  {
    byte[] data = new byte[columnBreaks.size() * 6 + 2];

    // The number of breaks on the list
    IntegerHelper.getTwoBytes(columnBreaks.size(), data, 0);
    int pos = 2;

    for (ColumnIndex cb : columnBreaks) {
      IntegerHelper.getTwoBytes(cb.getFirstColumnFollowingBreak(), data, pos);
      IntegerHelper.getTwoBytes(cb.getFirstRow(), data, pos+2);
      IntegerHelper.getTwoBytes(cb.getLastRow(), data, pos+4);
      pos += 6;
    }

    return data;
  }

  @Override
  public List<Integer> getColumnBreaks() {
    return columnBreaks.stream()
            .map(ColumnIndex::getFirstColumnFollowingBreak)
            .collect(Collectors.toList());
  }

  void setColumnBreaks(IVerticalPageBreaks breaks) {
    columnBreaks = breaks.getColumnBreaks().stream()
            .map(i -> colToColumnIndex(i))
            .collect(Collectors.toList());
  }

  void clear() {
    columnBreaks.clear();
  }

  void addBreak(int col) {
    // First check that the row is not already present
    Iterator<ColumnIndex> i = columnBreaks.iterator();

    while (i.hasNext())
      if (i.next().getFirstColumnFollowingBreak() == col)
        return;

    columnBreaks.add(colToColumnIndex(col));
  }

  private ColumnIndex colToColumnIndex(int col) {
    return new ColumnIndex(col, 0, 0xffff);
  }

  void insertColumn(int col) {
    ListIterator<ColumnIndex> ri = columnBreaks.listIterator();
    while (ri.hasNext())
    {
      ColumnIndex val = ri.next();
      if (val.getFirstColumnFollowingBreak() >= col)
        ri.set(val.withFirstColumnFollowingBreak(val.getFirstColumnFollowingBreak()+1));
    }
  }

  void removeColumn(int col) {
    ListIterator<ColumnIndex> ri = columnBreaks.listIterator();
    while (ri.hasNext())
    {
      ColumnIndex val = ri.next();
      if (val.getFirstColumnFollowingBreak() == col)
        ri.remove();
      else if (val.getFirstColumnFollowingBreak() > col)
        ri.set(val.withFirstColumnFollowingBreak(val.getFirstColumnFollowingBreak()-1));
    }
  }

  void write(File outputFile) throws IOException {
    if (columnBreaks.size() > 0)
      outputFile.write(this);
  }
 
}
