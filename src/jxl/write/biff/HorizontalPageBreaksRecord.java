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
import jxl.read.biff.IHorizontalPageBreaks;

/**
 * Contains the list of explicit horizontal page breaks on the current sheet
 */
class HorizontalPageBreaksRecord extends WritableRecordData implements IHorizontalPageBreaks {
  
  /**
   * The row breaks
   */
  private List<RowIndex> rowBreaks = new ArrayList<>();
  
  /**
   * Constructor
   * 
   * @param break the row breaks
   */
  HorizontalPageBreaksRecord()
  {
    super(Type.HORIZONTALPAGEBREAKS);
  }

  /**
   * Gets the binary data to write to the output file
   * 
   * @return the binary data
   */
  @Override
  public byte[] getData()
  {
    byte[] data = new byte[rowBreaks.size() * 6 + 2];

    // The number of breaks on the list
    IntegerHelper.getTwoBytes(rowBreaks.size(), data, 0);
    int pos = 2;

    for (RowIndex rb : rowBreaks) {
      IntegerHelper.getTwoBytes(rb.getFirstRowBelowBreak(), data, pos);
      IntegerHelper.getTwoBytes(rb.getFirstColumn(), data, pos+2);
      IntegerHelper.getTwoBytes(rb.getLastColumn(), data, pos+4);
      pos += 6;
    }

    return data;
  }

  @Override
  public List<Integer> getRowBreaks() {
    return rowBreaks.stream()
            .map(RowIndex::getFirstRowBelowBreak)
            .collect(Collectors.toList());
  }

  void setRowBreaks(IHorizontalPageBreaks breaks) {
    rowBreaks = breaks.getRowBreaks().stream()
            .map(i -> rowToRowIndex(i))
            .collect(Collectors.toList());
  }

  void clear() {
    rowBreaks.clear();
  }

  void addBreak(int row) {
    // First check that the row is not already present
    Iterator<RowIndex> i = rowBreaks.iterator();

    while (i.hasNext())
      if (i.next().getFirstRowBelowBreak() == row)
        return;

    rowBreaks.add(rowToRowIndex(row));
  }

  private RowIndex rowToRowIndex(int col) {
    return new RowIndex(col, 0, 0xffff);
  }
  
  void insertRow(int row) {
    ListIterator<RowIndex> ri = rowBreaks.listIterator();
    while (ri.hasNext())
    {
      RowIndex val = ri.next();
      if (val.getFirstRowBelowBreak() >= row)
        ri.set(val.withFirstRowBelowBreak(val.getFirstRowBelowBreak()+1));
    }
  }

  void removeRow(int row) {
    ListIterator<RowIndex> ri = rowBreaks.listIterator();
    while (ri.hasNext())
    {
      RowIndex val = ri.next();
      if (val.getFirstRowBelowBreak() == row)
        ri.remove();
      else if (val.getFirstRowBelowBreak() > row)
        ri.set(val.withFirstRowBelowBreak(val.getFirstRowBelowBreak()-1));
    }
  }

  void write(File outputFile) throws IOException {
    if (rowBreaks.size() > 0)
      outputFile.write(this);
  }
  
}
