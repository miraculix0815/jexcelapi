/**********************************************************************
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
import static java.lang.Math.max;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import jxl.*;
import jxl.CellReferenceHelper;
import jxl.HeaderFooter;
import jxl.biff.*;
import jxl.biff.drawing.*;
import jxl.common.*;
import jxl.format.*;
import jxl.format.CellFormat;
import jxl.format.Font;
import jxl.read.biff.*;
import jxl.write.*;

/**
 * A writable sheet.  This class contains implementation of all the
 * writable sheet methods which may be invoke by the API
 */
class WritableSheetImpl implements WritableSheet
{
  /**
   * The logger
   */
  private static final Logger logger = Logger.getLogger(WritableSheetImpl.class);

  /**
   * The name of this sheet
   */
  private String name;
  /**
   * A handle to the output file which the binary data is written to
   */
  private final File outputFile;
  /**
   * The rows within this sheet
   */
  private final List<RowRecord> rows;
  /**
   * A handle to workbook format records
   */
  private final FormattingRecords formatRecords;
  /**
   * A handle to the shared strings used by this workbook
   */
  private final SharedStrings     sharedStrings;

  /**
   * The list of non-default column formats
   */
  private final TreeSet<ColumnInfoRecord> columnFormats;

  /**
   * The list of autosized columns
   */
  private TreeSet<Integer> autosizedColumns;

  /**
   * The list of hyperlinks
   */
  private final ArrayList<WritableHyperlink> hyperlinks;

  /**
   * The list of merged ranged
   */
  private final MergedCells mergedCells;

  /**
   * A number of rows.  This is a count of the maximum row number + 1
   */
  private int numRows;

  /**
   * The number of columns.  This is a count of the maximum column number + 1
   */
  private int numColumns;

  /**
   * The environment specific print record, copied from the read spreadsheet
   */
  private PLSRecord plsRecord;

  /**
   * The buttons property set
   */
  private ButtonPropertySetRecord buttonPropertySet;

  /**
   * A flag indicating that this sheet is a chart only
   */
  private boolean chartOnly;

  /**
   * The data validations on this page.  Used to store data validations
   * from a read sheet
   */
  private DataValidation dataValidation;

  /**
   * Array of row page breaks
   */
  private final HorizontalPageBreaksRecord rowBreaks = new HorizontalPageBreaksRecord();

  /**
   * Array of column page breaks
   */
  private final VerticalPageBreaksRecord columnBreaks = new VerticalPageBreaksRecord();

  /**
   * The drawings on this sheet
   */
  private final ArrayList<DrawingGroupObject> drawings;

  /**
   * The images on this sheet.  This is a subset of the drawings list
   */
  private final ArrayList<WritableImage> images;

  /**
   * The conditional formats on this sheet
   */
  private final ArrayList<ConditionalFormat> conditionalFormats;

  /**
   * The autofilter
   */
  private AutoFilter autoFilter;

  /**
   * The writable cells on this sheet which may have validation added
   * to them
   */
  private final ArrayList<WritableCell> validatedCells;

  /**
   * The combo box object used for list validations on this sheet
   */
  private ComboBox comboBox;

  /**
   * Drawings modified flag.  Set to true if the drawings list has
   * been modified
   */
  private boolean drawingsModified;

  /**
   * The maximum row outline level
   */
  private int maxRowOutlineLevel;

  /**
   * The maximum column outline level
   */
  private int maxColumnOutlineLevel;

  /**
   * The settings for this sheet
   */
  private SheetSettings settings;

  /**
   * The sheet writer engine
   */
  private final SheetWriter sheetWriter;

  /**
   * The settings for the workbook
   */
  private final WorkbookSettings workbookSettings;

  /**
   * The workbook
   */
  private final WritableWorkbookImpl workbook;

  /**
   * The maximum number of rows excel allows in a worksheet
   */
  final static int MAX_ROWS_PER_SHEET = 65536;

  /**
   * The maximum number of characters permissible for a sheet name
   */
  private final static int maxSheetNameLength = 31;

  /**
   * The illegal characters for a sheet name
   */
  private final static char[] illegalSheetNameCharacters =
    new char[] {'*', ':', '?', '\\'};

  /**
   * The supported file types
   */
  private static final String[] imageTypes = new String[] {"png"};

  /**
   * The comparator for column info record
   */
  private static class ColumnInfoComparator<T> implements Comparator<T>
  {
    /**
     * Equals method
     *
     * @param o the object to compare
     * @return TRUE if equal, FALSE otherwise
     */
    @Override
    public boolean equals(Object o)
    {
      return o == this;
    }

    /**
     * Comparison function for to ColumnInfoRecords
     *
     * @param o2 first object to compare
     * @param o1 second object to compare
     * @return the result of the comparison
     */
    @Override
    public int compare(Object o1, Object o2)
    {
      if (o1 == o2)
      {
        return 0;
      }

      Assert.verify(o1 instanceof ColumnInfoRecord);
      Assert.verify(o2 instanceof ColumnInfoRecord);

      ColumnInfoRecord ci1 = (ColumnInfoRecord) o1;
      ColumnInfoRecord ci2 = (ColumnInfoRecord) o2;

      return ci1.getColumn() - ci2.getColumn();
    }
  }

  /**
   * Constructor
   *
   * @param fr the formatting records used by the workbook
   * @param of the output file to write the binary data
   * @param f the fonts used by the workbook
   * @param n the name of this sheet
   * @param ss the shared strings used by the workbook
   * @param ws the workbook settings
   */
  WritableSheetImpl(String n,
                           File of,
                           FormattingRecords fr,
                           SharedStrings ss,
                           WorkbookSettings ws,
                           WritableWorkbookImpl ww)
  {
    name = validateName(n);
    outputFile = of;
    rows = new ArrayList<>();
    numRows = 0;
    numColumns = 0;
    chartOnly = false;
    workbook = ww;

    formatRecords      = fr;
    sharedStrings      = ss;
    workbookSettings   = ws;
    drawingsModified   = false;
    columnFormats      = new TreeSet<>(new ColumnInfoComparator<>());
    autosizedColumns   = new TreeSet<>();
    hyperlinks         = new ArrayList<>();
    mergedCells        = new MergedCells(this);
    drawings           = new ArrayList<>();
    images             = new ArrayList<>();
    conditionalFormats = new ArrayList<>();
    validatedCells     = new ArrayList<>();
    settings           = new SheetSettings(this);


    sheetWriter = new SheetWriter(outputFile,
                                  this,
                                  workbookSettings);
  }

  /**
   * Returns the cell for the specified location eg. "A4", using the
   * CellReferenceHelper
   *
   * @param loc the cell reference
   * @return the cell at the specified co-ordinates
   */
  @Override
  public Cell getCell(String loc)
  {
    return getCell(CellReferenceHelper.getColumn(loc),
                   CellReferenceHelper.getRow(loc));
  }

  /**
   * Returns the cell specified at this row and at this column
   *
   * @param column the column number
   * @param row the row number
   * @return the cell at the specified co-ordinates
   */
  @Override
  public Cell getCell(int column, int row)
  {
    return getWritableCell(column, row);
  }

  /**
   * Returns the cell for the specified location eg. "A4".  Note that this
   * method is identical to calling getCell(CellReferenceHelper.getColumn(loc),
   * CellReferenceHelper.getRow(loc)) and its implicit performance
   * overhead for string parsing.  As such,this method should therefore
   * be used sparingly
   *
   * @param loc the cell reference
   * @return the cell at the specified co-ordinates
   */
  @Override
  public WritableCell getWritableCell(String loc)
  {
    return getWritableCell(CellReferenceHelper.getColumn(loc),
                           CellReferenceHelper.getRow(loc));
  }

  /**
   * Returns the cell specified at this row and at this column
   *
   * @param column the column number
   * @param row the row number
   * @return the cell at the specified co-ordinates
   */
  @Override
  public WritableCell getWritableCell(int column, int row)
  {
    WritableCell c = null;

    if (row < rows.size() && rows.get(row) != null)
    {
      c = rows.get(row).getCell(column);
    }

    if (c == null)
    {
      c = new EmptyCell(column, row);
    }

    return c;
  }

  /**
   * Returns the number of rows in this sheet
   *
   * @return the number of rows in this sheet
   */
  @Override
  public int getRows()
  {
    return numRows;
  }

  /**
   * Returns the number of columns in this sheet
   *
   * @return the number of columns in this sheet
   */
  @Override
  public int getColumns()
  {
    return numColumns;
  }

  /**
   * Gets the cell whose contents match the string passed in.
   * If no match is found, then null is returned.  The search is performed
   * on a row by row basis, so the lower the row number, the more
   * efficiently the algorithm will perform
   *
   * @param  contents the string to match
   * @return the Cell whose contents match the paramter, null if not found
   */
  @Override
  public Cell findCell(String contents)
  {
    CellFinder cellFinder = new CellFinder(this);
    return cellFinder.findCell(contents);
  }

  /**
   * Gets the cell whose contents match the string passed in.
   * If no match is found, then null is returned.  The search is performed
   * on a row by row basis, so the lower the row number, the more
   * efficiently the algorithm will perform
   *
   * @param contents the string to match
   * @param firstCol the first column within the range
   * @param firstRow the first row of the range
   * @param lastCol the last column within the range
   * @param lastRow the last row within the range
   * @param reverse indicates whether to perform a reverse search or not
   * @return the Cell whose contents match the parameter, null if not found
   */
  @Override
  public Cell findCell(String contents,
                       int firstCol,
                       int firstRow,
                       int lastCol,
                       int lastRow,
                       boolean reverse)
  {
    CellFinder cellFinder = new CellFinder(this);
    return cellFinder.findCell(contents,
                               firstCol,
                               firstRow,
                               lastCol,
                               lastRow,
                               reverse);
  }

  /**
   * Gets the cell whose contents match the regular expressionstring passed in.
   * If no match is found, then null is returned.  The search is performed
   * on a row by row basis, so the lower the row number, the more
   * efficiently the algorithm will perform
   *
   * @param pattern the regular expression string to match
   * @param firstCol the first column within the range
   * @param firstRow the first row of the range
   * @param lastRow the last row within the range
   * @param lastCol the last column within the ranage
   * @param reverse indicates whether to perform a reverse search or not
   * @return the Cell whose contents match the parameter, null if not found
   */
  @Override
  public Cell findCell(Pattern pattern,
                       int firstCol,
                       int firstRow,
                       int lastCol,
                       int lastRow,
                       boolean reverse)
  {
    CellFinder cellFinder = new CellFinder(this);
    return cellFinder.findCell(pattern,
                               firstCol,
                               firstRow,
                               lastCol,
                               lastRow,
                               reverse);
  }

  /**
   * Gets the cell whose contents match the string passed in.
   * If no match is found, then null is returned.  The search is performed
   * on a row by row basis, so the lower the row number, the more
   * efficiently the algorithm will perform.  This method differs
   * from the findCell methods in that only cells with labels are
   * queried - all numerical cells are ignored.  This should therefore
   * improve performance.
   *
   * @param contents the string to match
   * @return the Cell whose contents match the paramter, null if not found
   */
  @Override
  public LabelCell findLabelCell(String contents)
  {
    CellFinder cellFinder = new CellFinder(this);
    return cellFinder.findLabelCell(contents);
  }

  /**
   * Gets all the cells on the specified row
   *
   * @param row the rows whose cells are to be returned
   * @return the cells on the given row
   */
  @Override
  public Cell[] getRow(int row)
  {
    // Find the last non-null cell
    boolean found = false;
    int col = numColumns - 1;
    while (col >= 0 && !found)
    {
      if (getCell(col, row).getType() != CellType.EMPTY)
      {
        found = true;
      }
      else
      {
        col--;
      }
    }

    // Only create entries for non-empty cells
    Cell[] cells = new Cell[col+1];

    for (int i = 0; i <= col; i++)
    {
      cells[i] = getCell(i, row);
    }
    return cells;
  }

  /**
   * Gets all the cells on the specified column
   *
   * @param col the column whose cells are to be returned
   * @return the cells on the specified column
   */
  @Override
  public Cell[] getColumn(int col)
  {
    // Find the last non-null cell
    boolean found = false;
    int row = numRows - 1;

    while (row >= 0 && !found)
    {
      if (getCell(col, row).getType() != CellType.EMPTY)
      {
        found = true;
      }
      else
      {
        row--;
      }
    }

    // Only create entries for non-empty cells
    Cell[] cells = new Cell[row+1];

    for (int i = 0; i <= row; i++)
    {
      cells[i] = getCell(col, i);
    }
    return cells;
  }

  /**
   * Gets the name of this sheet
   *
   * @return the name of the sheet
   */
  @Override
  public String getName()
  {
    return name;
  }

  /**
   * Inserts a blank row into this spreadsheet.  If the row is out of range
   * of the rows in the sheet, then no action is taken
   *
   * @param row the row to insert
   */
  @Override
  public void insertRow(int row) throws RowsExceededException
  {
    if (numRows >= MAX_ROWS_PER_SHEET)
      throw new RowsExceededException();

    if (row < 0)
      return;

    // drawings are not tracked by numRows and have to be managed outside of
    // the RowRecords
    for (DrawingGroupObject dgo : drawings)
      if (dgo.getY() >= row)
        if (dgo.getY() + dgo.getHeight() + 1 < MAX_ROWS_PER_SHEET)
          dgo.setY(dgo.getY() + 1);

    if (row >= numRows)
      return;

    rows.add(row, null);

    // Increment all the internal row number by one
    for (int i = row+1; i <= numRows; i++)
    {
      if (rows.get(i) != null)
      {
        rows.get(i).incrementRow();
      }
    }

    // Adjust any hyperlinks
    Iterator<WritableHyperlink> i = hyperlinks.iterator();
    while (i.hasNext())
    {
      HyperlinkRecord hr = i.next();
      hr.insertRow(row);
    }

    // Adjust any data validations
    if (dataValidation != null)
    {
      dataValidation.insertRow(row);
    }

    if (validatedCells != null && validatedCells.size() > 0)
    {
      for (Iterator<WritableCell> vci = validatedCells.iterator(); vci.hasNext();)
      {
        CellValue cv = (CellValue) vci.next();
        CellFeatures cf = cv.getCellFeatures();
        if (cf.getDVParser() != null)
        {
          cf.getDVParser().insertRow(row);
        }
      }
    }

    // Adjust any merged cells
    mergedCells.insertRow(row);

    // Adjust any page breaks
    rowBreaks.insertRow(row);

    // Adjust any conditional formats
    for (ConditionalFormat cf : conditionalFormats)
      cf.insertRow(row);

    // Handle interested cell references on the main workbook
    if (workbookSettings.getFormulaAdjust())
      workbook.rowInserted(this, row);

    settings.insertRowInPrintArea(row);

    // Adjust the maximum row record
    numRows++;
  }

  /**
   * Inserts a blank column into this spreadsheet.  If the column is out of
   * range of the columns in the sheet, then no action is taken.  If the
   * max column on the sheet has been reached, then the last column entry
   * gets dropped
   *
   * @param col the column to insert
   */
  @Override
  public void insertColumn(int col)
  {
    if (col < 0 || col >= numColumns)
    {
      return;
    }

    // Iterate through all the row records adding in the column
    for (int i = 0 ; i < numRows ; i++)
    {
      if (rows.get(i) != null)
      {
        rows.get(i).insertColumn(col);
      }
    }

    for (HyperlinkRecord hr : hyperlinks)
      hr.insertColumn(col);

    {
      // Iterate through the column views, incrementing the column number
      Iterator<ColumnInfoRecord> i = columnFormats.iterator();
      while (i.hasNext())
      {
        ColumnInfoRecord cir = i.next();

        if (cir.getColumn() >= col)
        {
          cir.incrementColumn();
        }
      }
    }

    // Iterate through the autosized columns, incrementing the column number
    if (autosizedColumns.size() > 0)
    {
      TreeSet<Integer> newAutosized = new TreeSet<>();
      Iterator<Integer> i = autosizedColumns.iterator();
      while (i.hasNext())
      {
        Integer colnumber = i.next();

        if (colnumber >= col)
        {
          newAutosized.add(colnumber + 1);
        }
        else
        {
          newAutosized.add(colnumber);
        }
      }
      autosizedColumns = newAutosized;
    }

    // Handle any data validations
    if (dataValidation != null)
    {
      dataValidation.insertColumn(col);
    }

    if (validatedCells != null && validatedCells.size() > 0)
    {
      for (WritableCell cv : validatedCells) {
        CellFeatures cf = cv.getCellFeatures();
        if (cf.getDVParser() != null)
          cf.getDVParser().insertColumn(col);
      }
    }

    // Adjust any merged cells
    mergedCells.insertColumn(col);

    // Adjust any page breaks
    columnBreaks.insertColumn(col);

    // Adjust any conditional formats
    for (ConditionalFormat cf : conditionalFormats)
      cf.insertColumn(col);

    // Handle interested cell references on the main workbook
    if (workbookSettings.getFormulaAdjust())
    {
      workbook.columnInserted(this, col);
    }

    settings.insertColumnInPrintArea(col);

    numColumns++;

    for (DrawingGroupObject dgo : drawings)
      if (dgo.getX() >= col)
        dgo.setX(dgo.getX() + 1);

  }

  /**
   * Removes a column from this spreadsheet.  If the column is out of range
   * of the columns in the sheet, then no action is taken
   *
   * @param col the column to remove
   */
  @Override
  public void removeColumn(int col)
  {
    if (col < 0 || col >= numColumns)
    {
      return;
    }

    // Iterate through all the row records removing the column
    for (int i = 0 ; i < numRows ; i++)
    {
      if (rows.get(i) != null)
      {
        rows.get(i).removeColumn(col);
      }
    }

    {
      // Adjust any hyperlinks
      Iterator<WritableHyperlink> i = hyperlinks.iterator();
      while (i.hasNext())
      {
        HyperlinkRecord hr = i.next();

        if (hr.getColumn()      == col &&
            hr.getLastColumn()  == col)
        {
          // The row with the hyperlink on has been removed, so get
          // rid of it from the list
          i.remove();
        }
        else
        {
          hr.removeColumn(col);
        }
      }
    }

    // Adjust any data validations
    if (dataValidation != null)
    {
      dataValidation.removeColumn(col);
    }

    if (validatedCells != null && validatedCells.size() > 0)
    {
      for (WritableCell cv : validatedCells) {
        CellFeatures cf = cv.getCellFeatures();
        if (cf.getDVParser() != null)
          cf.getDVParser().removeColumn(col);
      }
    }

    // Adjust any merged cells
    mergedCells.removeColumn(col);

    // Adjust any page breaks
    columnBreaks.removeColumn(col);

    {
      // Iterate through the column views, decrementing the column number
      Iterator<ColumnInfoRecord> i = columnFormats.iterator();
      ColumnInfoRecord removeColumn = null;
      while (i.hasNext())
      {
        ColumnInfoRecord cir = i.next();

        if (cir.getColumn() == col)
          removeColumn = cir;
        else if (cir.getColumn() > col)
          cir.decrementColumn();
      }

      if (removeColumn != null)
        columnFormats.remove(removeColumn);
    }

    // Iterate through the autosized columns, decrementing the column number
    if (autosizedColumns.size() > 0)
    {
      TreeSet<Integer> newAutosized = new TreeSet<>();
      Iterator<Integer> i = autosizedColumns.iterator();
      while (i.hasNext())
      {
        Integer colnumber = i.next();

        if (colnumber == col)
        {
          // do nothing
        }
        else if (colnumber > col)
          newAutosized.add(colnumber - 1);
        else
          newAutosized.add(colnumber);
      }
      autosizedColumns = newAutosized;
    }

    // Adjust any conditional formats
    for (ConditionalFormat cf : conditionalFormats)
      cf.removeColumn(col);

    // Handle interested cell references on the main workbook
    if (workbookSettings.getFormulaAdjust())
    {
      workbook.columnRemoved(this, col);
    }

    settings.removeColumnFromPrintArea(col);

    numColumns--;

    for (DrawingGroupObject dgo : drawings)
      if (dgo.getX() >= col)
        dgo.setX(max(dgo.getX() - 1, 0));
  }

  /**
   * Removes a row from this spreadsheet.  If the row is out of
   * range of the columns in the sheet, then no action is taken
   *
   * @param row the row to remove
   */
  @Override
  public void removeRow(int row)
  {
    if (row < 0)
      return;

    // drawings are not tracked by numRows and have to be managed outside of
    // the RowRecords
    for (DrawingGroupObject dgo : drawings)
      if (dgo.getY() >= row)
        if (dgo.getY() + dgo.getHeight() - 1 >= 0)
          dgo.setY(dgo.getY() - 1);

    if (row >= numRows)
    {
      // Call rowRemoved anyway, to adjust the named cells
      if (workbookSettings.getFormulaAdjust())
        workbook.rowRemoved(this, row);

      return;
    }

    rows.remove(row);

    // Decrement all the internal row numbers by one
    rows.stream()
            .skip(row)
            .filter(Objects::nonNull)
            .forEach(RowRecord::decrementRow);

    // Adjust any hyperlinks
    Iterator<WritableHyperlink> i = hyperlinks.iterator();
    while (i.hasNext())
    {
      HyperlinkRecord hr = i.next();

      if (hr.getRow()      == row &&
          hr.getLastRow()  == row)
        // The row with the hyperlink on has been removed, so get
        // rid of it from the list
        i.remove();
      else
        hr.removeRow(row);
    }

    // Adjust any data validations
    if (dataValidation != null)
    {
      dataValidation.removeRow(row);
    }

    if (validatedCells != null && validatedCells.size() > 0)
    {
      for (WritableCell cv : validatedCells) {
        CellFeatures cf = cv.getCellFeatures();
        if (cf.getDVParser() != null)
          cf.getDVParser().removeRow(row);
      }
    }

    // Adjust any merged cells
    mergedCells.removeRow(row);

    // Adjust any page breaks
    rowBreaks.removeRow(row);

    // Adjust any conditional formats
    for (ConditionalFormat cf : conditionalFormats)
      cf.removeRow(row);

    // Handle interested cell references on the main workbook
    if (workbookSettings.getFormulaAdjust())
    {
      workbook.rowRemoved(this, row);
    }

    settings.removeRowFromPrintArea(row);

    // Adjust the maximum row record
    numRows--;

    for (DrawingGroupObject dgo : drawings)
      if (dgo.getY() >= row)
        dgo.setY(max(dgo.getY() - 1, 0));
  }

  /**
   * Adds the cell to this sheet.  If the cell has already been added to
   * this sheet or another sheet, a WriteException is thrown.  If the
   * position to be occupied by this cell is already taken, the incumbent
   * cell is replaced.
   * The cell is then marked as referenced, and its formatting information
   * registered with the list of formatting records updated if necessary
   * The RowsExceededException may be caught if client code wishes to
   * explicitly trap the case where too many rows have been written
   * to the current sheet.  If this behaviour is not desired, it is
   * sufficient simply to handle the WriteException, since this is a base
   * class of RowsExceededException
   *
   * @exception WriteException
   * @exception RowsExceededException
   * @param cell the cell to add
   */
  @Override
  public void addCell(WritableCell cell)
    throws WriteException, RowsExceededException
  {
    if (cell.getType() == CellType.EMPTY)
      if (cell.getCellFormat() == null) {
        // remove blank cells with no particular cell formatting
        removeCell(cell.getRow(), cell.getColumn());

        return;
      }

    CellValue cv = (CellValue) cell;

    if (cv.isReferenced())
      throw new JxlWriteException(JxlWriteException.cellReferenced);

    int row = cell.getRow();
    RowRecord rowrec = getRowRecord(row);

    CellValue curcell = rowrec.getCell(cv.getColumn());
    boolean curSharedValidation = (curcell != null &&
      curcell.getCellFeatures() != null &&
      curcell.getCellFeatures().getDVParser() != null &&
      curcell.getCellFeatures().getDVParser().extendedCellsValidation());

    // Check for shared data validations, but only if the cell being added
    // has a data validation
    if (cell.getCellFeatures() != null &&
        cell.getCellFeatures().hasDataValidation() &&
        curSharedValidation)
    {
      DVParser dvp = curcell.getCellFeatures().getDVParser();
      logger.warn("Cannot add cell at " +
                  CellReferenceHelper.getCellReference(cv) +
                  " because it is part of the shared cell validation group " +
                  CellReferenceHelper.getCellReference(dvp.getFirstColumn(),
                                                       dvp.getFirstRow()) +
                  "-" +
                  CellReferenceHelper.getCellReference(dvp.getLastColumn(),
                                                       dvp.getLastRow()));
      return;
    }

    // Apply any shared validation from the current cell to this cell
    if (curSharedValidation)
    {
      WritableCellFeatures wcf = cell.getWritableCellFeatures();

      if (wcf == null)
      {
        wcf = new WritableCellFeatures();
        cell.setCellFeatures(wcf);
      }

      wcf.shareDataValidation(curcell.getCellFeatures());
    }

    rowrec.addCell(cv);

    // Adjust the max rows and max columns accordingly
    numRows = Math.max(row+1, numRows);
    numColumns = Math.max(numColumns, rowrec.getMaxColumn());

    // Indicate this cell is now part of a worksheet, so that it can't be
    // added anywhere else
    cv.setCellDetails(formatRecords, sharedStrings, this);
  }

  /**
   * Gets the row record at the specified row number, growing the
   * array as needs dictate
   *
   * @param row the row number we are interested in
   * @return the row record at the specified row
   * @exception RowsExceededException
   */
  RowRecord getRowRecord(int row) throws RowsExceededException
  {
    if (row >= MAX_ROWS_PER_SHEET)
    {
      throw new RowsExceededException();
    }

    // Grow the array of rows if needs be
    while (row >= rows.size())
      rows.add(null);

    RowRecord rowrec = rows.get(row);

    if (rowrec == null)
    {
      rowrec = new RowRecord(row, this);
      rows.set(row, rowrec);
    }

    return rowrec;
  }

  /**
   * Gets the row record for the specified row
   *
   * @param r the row
   * @return the row record
   */
  RowRecord getRowInfo(int r)
  {
    if (r < 0 || r > rows.size())
    {
      return null;
    }

    return rows.get(r);
  }

  /**
   * Gets the column info record for the specified column
   *
   * @param c the column
   * @return the column record
   */
  ColumnInfoRecord getColumnInfo(int c)
  {
    Iterator<ColumnInfoRecord> i = columnFormats.iterator();
    ColumnInfoRecord cir = null;
    boolean stop = false;

    while (i.hasNext() && !stop)
    {
      cir = i.next();

      if (cir.getColumn() >= c)
        stop = true;
    }

    if (!stop)
      return null;

    return cir.getColumn() == c ? cir : null;
  }

  /**
   * Sets the name of this worksheet
   *
   * @param n the name of this sheet
   */
  @Override
  public void setName(String n)
  {
    name = n;
  }

  /**
   * Sets the width (in characters) for a particular column in this sheet
   *
   * @param col the column whose width to set
   * @param width the width of the column in characters
   */
  @Override
  public void setColumnView(int col, int width)
  {
    CellView cv = new CellView();
    cv.setSize(width * 256);
    setColumnView(col, cv);
  }

  /**
   * Sets the view for this column
   *
   * @param col the column on which to set the view
   * @param view the view to set
   */
  @Override
  public void setColumnView(int col, CellView view)
  {
    XFRecord xfr =  (XFRecord) view.getFormat();
    if (xfr == null)
    {
      Styles styles = getWorkbook().getStyles();
      xfr = styles.getNormalStyle();
    }

    try
    {
      if (!xfr.isInitialized())
      {
        formatRecords.addStyle(xfr);
      }

      int width = view.depUsed() ? view.getDimension() * 256 : view.getSize();

      if (view.isAutosize())
      {
        autosizedColumns.add(col);
      }

      ColumnInfoRecord cir = new ColumnInfoRecord(col,
                                                  width,
                                                  xfr);

      if (view.isHidden())
        cir.setHidden(true);

      if (!columnFormats.contains(cir))
        columnFormats.add(cir);
      else
      {
        columnFormats.remove(cir);
        columnFormats.add(cir);
      }
    }
    catch (NumFormatRecordsException e)
    {
      logger.warn("Maximum number of format records exceeded.  Using " +
                  "default format.");

      ColumnInfoRecord cir = new ColumnInfoRecord
        (col, view.getDimension()*256, WritableWorkbook.NORMAL_STYLE);
      if (!columnFormats.contains(cir))
        columnFormats.add(cir);
    }
  }

  /**
   * Sets the height of the specified row, as well as its collapse status
   *
   * @param row the row to be formatted
   * @param height the row height in 1/20ths of a  point
   * @exception RowsExceededException
   * @deprecated use the override which takes a CellView object
   */
  @Override
  public void setRowView(int row, int height) throws RowsExceededException
  {
    CellView cv = new CellView();
    cv.setSize(height);
    cv.setHidden(false);
    setRowView(row, cv);
  }

  /**
   * Sets the height of the specified row, as well as its collapse status
   *
   * @param row the row to be formatted
   * @param collapsed indicates whether the row is collapsed
   * @exception jxl.write.biff.RowsExceededException
   * @deprecated use the override which takes a CellView object
   */
  @Override
  public void setRowView(int row, boolean collapsed)
    throws RowsExceededException
  {
    CellView cv = new CellView();
    cv.setHidden(collapsed);
    setRowView(row, cv);
  }

  /**
   * Sets the view for this column
   *
   * @param row the column on which to set the view
   * @param view the view to set
   * @exception RowsExceededException
   */
  @Override
  public void setRowView(int row, CellView view) throws RowsExceededException
  {
    RowRecord rowrec = getRowRecord(row);

    XFRecord xfr =  (XFRecord) view.getFormat();

    try
    {
      if (xfr != null)
      {
        if (!xfr.isInitialized())
        {
          formatRecords.addStyle(xfr);
        }
      }
    }
    catch (NumFormatRecordsException e)
    {
      logger.warn("Maximum number of format records exceeded.  Using " +
                  "default format.");

      xfr = null;
    }

    rowrec.setRowDetails(view.getSize(),
                         false,
                         view.isHidden(),
                         0,
                         false,
                         xfr);
    numRows = Math.max(numRows, row + 1);
  }

  /**
   * Writes out this sheet.  This functionality is delegated off to the
   * SheetWriter class in order to reduce the bloated nature of this source
   * file
   *
   * @exception IOException
   */
  public void write() throws IOException
  {
    boolean dmod = drawingsModified;
    if (workbook.getDrawingGroup() != null)
    {
      dmod |= workbook.getDrawingGroup().hasDrawingsOmitted();
    }

    if (autosizedColumns.size() > 0)
    {
      autosizeColumns();
    }

    sheetWriter.setWriteData(rows.toArray(RowRecord[]::new),
                             rowBreaks,
                             columnBreaks,
                             hyperlinks,
                             mergedCells,
                             columnFormats,
                             maxRowOutlineLevel,
                             maxColumnOutlineLevel);
    sheetWriter.setDimensions(getRows(), getColumns());
    sheetWriter.setSettings(settings);
    sheetWriter.setPLS(plsRecord);
    sheetWriter.setDrawings(drawings, dmod);
    sheetWriter.setButtonPropertySet(buttonPropertySet);
    sheetWriter.setDataValidation(dataValidation, validatedCells);
    sheetWriter.setConditionalFormats(conditionalFormats);
    sheetWriter.setAutoFilter(autoFilter);

    sheetWriter.write();
  }

  /**
   * Copies the specified sheet, row by row and cell by cell
   *
   * @param s the sheet to copy
   */
  void copy(Sheet s)
  {
    // Copy the settings
    settings = new SheetSettings(s.getSettings(), this);

    SheetCopier si = new SheetCopier(s, this, rowBreaks, columnBreaks);
    si.setColumnFormats(columnFormats);
    si.setFormatRecords(formatRecords);
    si.setHyperlinks(hyperlinks);
    si.setMergedCells(mergedCells);
    si.setSheetWriter(sheetWriter);
    si.setDrawings(drawings);
    si.setImages(images);
    si.setConditionalFormats(conditionalFormats);
    si.setValidatedCells(validatedCells);

    si.copySheet();

    dataValidation = si.getDataValidation();
    comboBox = si.getComboBox();
    plsRecord = si.getPLSRecord();
    chartOnly = si.isChartOnly();
    buttonPropertySet = si.getButtonPropertySet();
    numRows = si.getRows();
    autoFilter = si.getAutoFilter();
    maxRowOutlineLevel = si.getMaxRowOutlineLevel();
    maxColumnOutlineLevel = si.getMaxColumnOutlineLevel();
  }

  /**
   * Copies the specified sheet, row by row and cell by cell
   *
   * @param s the sheet to copy
   */
  void copy(WritableSheet s)
  {
    settings = new SheetSettings(s.getSettings(), this);
    WritableSheetImpl si = (WritableSheetImpl) s;

    WritableSheetCopier sc = new WritableSheetCopier(s, this);
    sc.setColumnFormats(si.columnFormats, columnFormats);
    sc.setMergedCells(si.mergedCells, mergedCells);
    sc.setRows(si.rows.toArray(RowRecord[]::new));
    sc.setRowBreaks(si.rowBreaks, rowBreaks);
    sc.setColumnBreaks(si.columnBreaks, columnBreaks);
    sc.setDataValidation(si.dataValidation);
    sc.setSheetWriter(sheetWriter);
    sc.setDrawings(si.drawings, drawings, images);
    sc.setWorkspaceOptions(si.getWorkspaceOptions());
    sc.setPLSRecord(si.plsRecord);
    sc.setButtonPropertySetRecord(si.buttonPropertySet);
    sc.setHyperlinks(si.hyperlinks, hyperlinks);
    sc.setValidatedCells(validatedCells);

    sc.copySheet();

    dataValidation = sc.getDataValidation();
    plsRecord = sc.getPLSRecord();
    buttonPropertySet = sc.getButtonPropertySet();
  }

  /**
   * Gets the header.  Called when copying sheets
   *
   * @return the page header
   */
  final HeaderRecord getHeader()
  {
    return sheetWriter.getHeader();
  }

  /**
   * Gets the footer.  Called when copying sheets
   *
   * @return the page footer
   */
  final FooterRecord getFooter()
  {
    return sheetWriter.getFooter();
  }

  /**
   * Gets the hyperlinks on this sheet
   *
   * @return an array of hyperlinks
   */
  @Override
  public Hyperlink[] getHyperlinks()
  {
    Hyperlink[] hl = new Hyperlink[hyperlinks.size()];

    for (int i = 0; i < hyperlinks.size(); i++)
      hl[i] = hyperlinks.get(i);

    return hl;
  }

  /**
   * Gets the cells which have been merged on this sheet
   *
   * @return an array of range objects
   */
  @Override
  public Range[] getMergedCells()
  {
    return mergedCells.getMergedCells();
  }

  /**
   * Gets the writable  hyperlinks on this sheet
   *
   * @return an array of hyperlinks
   */
  @Override
  public WritableHyperlink[] getWritableHyperlinks()
  {
    WritableHyperlink[] hl = new WritableHyperlink[hyperlinks.size()];

    for (int i = 0; i < hyperlinks.size(); i++)
      hl[i] = hyperlinks.get(i);

    return hl;
  }

  /**
   * Removes the specified hyperlink.  Note that if you merely set the
   * cell contents to be an Empty cell, then the cells containing the
   * hyperlink will still be active.  The contents of the cell which
   * activate the hyperlink are removed.
   * The hyperlink passed in must be a hyperlink retrieved using the
   * getHyperlinks method
   *
   * @param h the hyperlink to remove.
   * @param preserveLabel if TRUE preserves the label contents, if FALSE
   * removes them
   */
  @Override
  public void removeHyperlink(WritableHyperlink h)
  {
    removeHyperlink(h, false);
  }

  /**
   * Removes the specified hyperlink.  Note that if you merely set the
   * cell contents to be an Empty cell, then the cells containing the
   * hyperlink will still be active.
   * If the preserveLabel field is set, the cell contents of the
   * hyperlink are preserved, although the hyperlink is deactivated.  If
   * this value is FALSE, the cell contents are removed
   * The hyperlink passed in must be a hyperlink retrieved using the
   * getHyperlinks method
   *
   * @param h the hyperlink to remove.
   * @param preserveLabel if TRUE preserves the label contents, if FALSE
   * removes them
   */
  @Override
  public void removeHyperlink(WritableHyperlink h, boolean preserveLabel)
  {
    // Remove the hyperlink
    hyperlinks.remove(hyperlinks.indexOf(h));

    if (!preserveLabel)
    {
      // Set the cell contents for the hyperlink - including any formatting
      // information - to be empty
      Assert.verify(rows.size() > h.getRow() && rows.get(h.getRow()) != null);
      rows.get(h.getRow()).removeCell(h.getColumn());
    }
  }

  /**
   * Adds the specified hyperlink
   *
   * @param the hyperlink
   * @exception WriteException
   * @exception RowsExceededException
   */
  @Override
  public void addHyperlink(WritableHyperlink h)
    throws WriteException, RowsExceededException
  {
    // First set the label on the sheet
    Cell c = getCell(h.getColumn(), h.getRow());

    String contents = null;
    if (h.isFile() || h.isUNC())
    {
      String cnts = ( (HyperlinkRecord) h).getContents();
      if (cnts == null)
        contents = h.getFile().toString();
      else
        contents = cnts;
    }
    else if (h.isURL())
    {
      String cnts = ( (HyperlinkRecord) h).getContents();
      if (cnts == null)
        contents = h.getURL().toString();
      else
        contents=cnts;
    }
    else if (h.isLocation())
      contents = ( (HyperlinkRecord) h).getContents();

    // If the cell type is a label, then preserve the cell contents
    // and most of the format (apart from the font)
    // otherwise overwrite the cell content and the format with the contents
    // and the standard hyperlink format
    if (c.getType() == CellType.LABEL)
    {
      Label l = (Label) c;
      l.setString(contents);
      WritableCellFormat wcf = new WritableCellFormat(l.getCellFormat());
      ( (XFRecord) wcf).setFont(WritableWorkbook.HYPERLINK_FONT);
      l.setCellFormat(wcf);
    }
    else
    {
      Label l = new Label(h.getColumn(), h.getRow(), contents,
                          WritableWorkbook.HYPERLINK_STYLE);
      addCell(l);
    }

    // Set all other cells within range to be empty
    for (int i = h.getRow(); i <= h.getLastRow(); i++)
      for (int j = h.getColumn(); j <= h.getLastColumn(); j++)
        if (i != h.getRow() && j != h.getColumn())
          // Set the cell to be empty
          if (rows.size() < h.getLastColumn() && rows.get(i) != null)
            rows.get(i).removeCell(j);

    ((HyperlinkRecord) h).initialize(this);
    hyperlinks.add(h);
  }

  /**
   * Merges the specified cells.  Any clashes or intersections between
   * merged cells are resolved when the spreadsheet is written out
   *
   * @param col1 the column number of the top left cell
   * @param row1 the row number of the top left cell
   * @param col2 the column number of the bottom right cell
   * @param row2 the row number of the bottom right cell
   * @return the Range object representing the merged cells
   * @exception jxl.write.WriteException
   * @exception jxl.write.biff.RowsExceededException
   */
  @Override
  public Range mergeCells(int col1, int row1, int col2, int row2)
    throws WriteException, RowsExceededException
  {
    // First check that the cells make sense
    if (col2 < col1 || row2 < row1)
    {
      logger.warn("Cannot merge cells - top left and bottom right "+
                  "incorrectly specified");
    }

    // Make sure the spreadsheet is up to size
    if (col2 >= numColumns || row2 >= numRows)
    {
      addCell(new Blank(col2, row2));
    }

    SheetRangeImpl range = new SheetRangeImpl(this, col1, row1, col2, row2);
    mergedCells.add(range);

    return range;
  }

  /**
   * Sets a row grouping
   *
   * @param row1 the first row of the group
   * @param row2 the last row of the group
   * @param collapsed should the group be collapsed?
   * @exception WriteException
   * @exception RowsExceededException
   */
  @Override
  public void setRowGroup(int row1, int row2,
                          boolean collapsed)
    throws WriteException, RowsExceededException
  {
    if (row2 < row1)
    {
      logger.warn("Cannot merge cells - top and bottom rows incorrectly " +
                  "specified");
    }

    for (int i = row1; i <= row2; i++)
    {
      RowRecord row = getRowRecord(i);
      numRows = Math.max(i+1, numRows);
      row.incrementOutlineLevel();
      row.setCollapsed(collapsed);
      maxRowOutlineLevel = Math.max(maxRowOutlineLevel,
                                    row.getOutlineLevel());
    }
  }

  /**
   * Unsets a row grouping
   *
   * @param row1 the first row to unset
   * @param row2 the last row to unset
   * @exception WriteException
   * @exception RowsExceededException
   */
  @Override
  public void unsetRowGroup(int row1, int row2)
    throws WriteException, RowsExceededException
  {
    if (row2 < row1)
    {
      logger.warn("Cannot merge cells - top and bottom rows incorrectly " +
                  "specified");
    }

    // Make sure the spreadsheet is up to size
    if (row2 >= numRows)
    {
      logger.warn("" + row2 +
                  " is greater than the sheet bounds");
      row2 = numRows - 1;
    }

    for (int i = row1; i <= row2; i++)
    {
      rows.get(i).decrementOutlineLevel();
    }

    // Recalculate the max outline level
    maxRowOutlineLevel = 0;
    for (int i = rows.size(); i-- > 0; )
    {
      maxRowOutlineLevel = Math.max(maxRowOutlineLevel,
                                    rows.get(i).getOutlineLevel());
    }
  }

  /**
   * Sets a column grouping
   *
   * @param col1 the first column of the group
   * @param col2 the last column of the group
   * @param collapsed should the group be collapsed?
   * @exception WriteException
   * @exception RowsExceededException
   */
  @Override
  public void setColumnGroup(int col1, int col2, boolean collapsed)
    throws WriteException, RowsExceededException
  {
    if (col2 < col1)
    {
      logger.warn("Cannot merge cells - top and bottom rows incorrectly " +
                  "specified");
    }

    for (int i = col1; i <= col2; i++)
    {
      ColumnInfoRecord cir = getColumnInfo(i);

      // Create the column info record if not present using a default
      // cell view
      if (cir == null)
      {
        setColumnView(i, new CellView());
        cir = getColumnInfo(i);
      }

      cir.incrementOutlineLevel();
      cir.setCollapsed(collapsed);
      maxColumnOutlineLevel = Math.max(maxColumnOutlineLevel,
                                       cir.getOutlineLevel());
    }
  }

  /**
   * Unsets a column grouping
   *
   * @param col1 the first column to unset
   * @param col2 the last column to unset
   * @exception WriteException
   * @exception RowsExceededException
   */
  @Override
  public void unsetColumnGroup(int col1, int col2)
    throws WriteException, RowsExceededException
  {
    if (col2 < col1)
    {
      logger.warn("Cannot merge cells - top and bottom rows incorrectly " +
                  "specified");
    }

    for (int i = col1; i <= col2; i++)
    {
      ColumnInfoRecord cir = getColumnInfo(i);
      cir.decrementOutlineLevel();
    }

    // Recalculate the max outline level
    maxColumnOutlineLevel = 0;
    for (Iterator<ColumnInfoRecord> it = columnFormats.iterator(); it.hasNext(); )
    {
      ColumnInfoRecord cir = it.next();
      maxColumnOutlineLevel = Math.max(maxColumnOutlineLevel,
                                       cir.getOutlineLevel());
    }
  }

  /**
   * Unmerges the specified cells.  The Range passed in should be one that
   * has been previously returned as a result of the getMergedCells method
   *
   * @param r the range of cells to unmerge
   */
  @Override
  public void unmergeCells(Range r)
  {
    mergedCells.unmergeCells(r);
  }

  /**
   * Sets the header for this page
   *
   * @param l the print header to print on the left side
   * @param c the print header to print in the centre
   * @param r the print header to print on the right hand side
   * @deprecated Use the sheet settings bean
   */
  @Override
  public void setHeader(String l, String c, String r)
  {
    HeaderFooter header = new HeaderFooter();
    header.getLeft().append(l);
    header.getCentre().append(c);
    header.getRight().append(r);
    settings.setHeader(header);
  }

  /**
   * Sets the footer for this page
   *
   * @param l the print header to print on the left side
   * @param c the print header to print in the centre
   * @param r the print header to print on the right hand side
   * @deprecated Use the sheet settings bean
   */
  @Override
  public void setFooter(String l, String c, String r)
  {
    HeaderFooter footer = new HeaderFooter();
    footer.getLeft().append(l);
    footer.getCentre().append(c);
    footer.getRight().append(r);
    settings.setFooter(footer);
  }

  /**
   * Sets the page setup details
   *
   * @param p  the page orientation
   * @deprecated Use the SheetSettings bean
   */
  @Override
  public void setPageSetup(PageOrientation p)
  {
    settings.setOrientation(p);
  }

  /**
   * Sets the page setup details
   *
   * @param p  the page orientation
   * @param hm the header margin, in inches
   * @param fm the footer margin, in inches
   * @deprecated Use the SheetSettings bean
   */
  @Override
  public void setPageSetup(PageOrientation p, double hm, double fm)
  {
    settings.setOrientation(p);
    settings.setHeaderMargin(hm);
    settings.setFooterMargin(fm);
  }

  /**
   * Sets the page setup details
   *
   * @param p  the page orientation
   * @param ps the paper size
   * @param hm the header margin, in inches
   * @param fm the footer margin, in inches
   * @deprecated Use the SheetSettings bean
   */
  @Override
  public void setPageSetup(PageOrientation p, PaperSize ps,
                           double hm, double fm)
  {
    settings.setPaperSize(ps);
    settings.setOrientation(p);
    settings.setHeaderMargin(hm);
    settings.setFooterMargin(fm);
  }

  /**
   * Gets the settings for this sheet
   *
   * @return the page settings bean
   */
  @Override
  public SheetSettings getSettings()
  {
    return settings;
  }

  /**
   * Gets the workbook settings
   */
  WorkbookSettings getWorkbookSettings()
  {
    return workbookSettings;
  }

  /**
   * Forces a page break at the specified row
   *
   * @param row the row to break at
   */
  @Override
  public void addRowPageBreak(int row)
  {
    rowBreaks.addBreak(row);
  }

  /**
   * Forces a page break at the specified column
   *
   * @param col the column to break at
   */
  @Override
  public void addColumnPageBreak(int col)
  {
    columnBreaks.addBreak(col);
  }

  /**
   * Accessor for the charts.  Used when copying
   *
   * @return the charts on this sheet
   */
  Chart[] getCharts()
  {
    return sheetWriter.getCharts();
  }

  /**
   * Accessor for the drawings.  Used when copying
   *
   * @return the drawings on this sheet
   */
  private DrawingGroupObject[] getDrawings()
  {
    DrawingGroupObject[] dr = new DrawingGroupObject[drawings.size()];
    dr = drawings.toArray(dr);
    return dr;
  }

  /**
   * Check all the merged cells for borders.  Although in an OO sense the
   * logic should belong in this class, in order to reduce the bloated
   * nature of the source code for this object this logic has been delegated
   * to the SheetWriter
   */
  void checkMergedBorders()
  {
    sheetWriter.setWriteData(rows.toArray(RowRecord[]::new),
                             rowBreaks,
                             columnBreaks,
                             hyperlinks,
                             mergedCells,
                             columnFormats,
                             maxRowOutlineLevel,
                             maxColumnOutlineLevel);
    sheetWriter.setDimensions(getRows(), getColumns());
    sheetWriter.checkMergedBorders();
  }

  /**
   * Accessor for the workspace options
   *
   * @return the workspace options
   */
  private WorkspaceInformationRecord getWorkspaceOptions()
  {
    return sheetWriter.getWorkspaceOptions();
  }

  /**
   * Rationalizes the sheets xf index mapping
   * @param xfMapping the index mapping for XFRecords
   * @param fontMapping the index mapping for fonts
   * @param formatMapping the index mapping for formats
   */
  void rationalize(IndexMapping xfMapping,
                   IndexMapping fontMapping,
                   IndexMapping formatMapping)
  {
    // Rationalize the column formats
    for (ColumnInfoRecord cir : columnFormats)
      cir.rationalize(xfMapping);

    // Rationalize the row formats
    for (RowRecord row : rows)
      if (row != null)
        row.rationalize(xfMapping);

    // Rationalize any data that appears on the charts
    Chart[] charts = getCharts();
    for (Chart chart : charts)
      chart.rationalize(xfMapping, fontMapping, formatMapping);
  }

  /**
   * Accessor for the workbook
   * @return the workbook
   */
  WritableWorkbookImpl getWorkbook()
  {
    return workbook;
  }

  /**
   * Gets the column format for the specified column
   *
   * @param col the column number
   * @return the column format, or NULL if the column has no specific format
   * @deprecated Use getColumnView instead
   */
  @Override
  public CellFormat getColumnFormat(int col)
  {
    return getColumnView(col).getFormat();
  }

  /**
   * Gets the column width for the specified column
   *
   * @param col the column number
   * @return the column width, or the default width if the column has no
   *         specified format
   * @deprecated Use getColumnView instead
   */
  @Override
  public int getColumnWidth(int col)
  {
    return getColumnView(col).getDimension();
  }

  /**
   * Gets the column width for the specified column
   *
   * @param row the column number
   * @return the row height, or the default height if the column has no
   *         specified format
   * @deprecated Use getRowView instead
   */
  @Override
  public int getRowHeight(int row)
  {
    return getRowView(row).getDimension();
  }

  /**
   * Accessor for the chart only method
   *
   * @return TRUE if this is a chart only, FALSE otherwise
   */
  boolean isChartOnly()
  {
    return chartOnly;
  }

  /**
   * Gets the row view for the specified row
   *
   * @param col the row number
   * @return the row format, or the default format if no override is
             specified
   */
  @Override
  public CellView getRowView(int row)
  {
    CellView cv = new CellView();

    try
    {
      RowRecord rr = getRowRecord(row);

      if (rr.isDefaultHeight())
      {
        cv.setDimension(settings.getDefaultRowHeight());
        cv.setSize(settings.getDefaultRowHeight());
      }
      else if (rr.isCollapsed())
        cv.setHidden(true);
      else
      {
        cv.setDimension(rr.getRowHeight());
        cv.setSize(rr.getRowHeight());
      }
      return cv;
    }
    catch (RowsExceededException e)
    {
      // Simple return the default
      cv.setDimension(settings.getDefaultRowHeight());
      cv.setSize(settings.getDefaultRowHeight());
      return cv;
    }
  }

  /**
   * Gets the column width for the specified column
   *
   * @param col the column number
   * @return the column format, or the default format if no override is
             specified
   */
  @Override
  public CellView getColumnView(int col)
  {
    ColumnInfoRecord cir = getColumnInfo(col);
    CellView cv = new CellView();

    if (cir != null)
    {
      cv.setDimension(cir.getWidth()/256);
      cv.setSize(cir.getWidth());
      cv.setHidden(cir.getHidden());
      cv.setFormat(cir.getCellFormat());
    }
    else
    {
      cv.setDimension(settings.getDefaultColumnWidth()/256);
      cv.setSize(settings.getDefaultColumnWidth() * 256);
    }

    return cv;
  }

  /**
   * Adds an image to this sheet
   *
   * @param image the image to add
   */
  @Override
  public void addImage(WritableImage image)
  {
    boolean supported = false;
    Path imageFile = image.getImageFile();
    String fileType = "?";

    if (imageFile != null)
    {
      String fileName = imageFile.getFileName().toString();
      int fileTypeIndex = fileName.lastIndexOf('.');
      fileType = fileTypeIndex != -1 ?
        fileName.substring(fileTypeIndex+1) : "";

      for (int i = 0 ; i < imageTypes.length && !supported ; i++)
      {
        if (fileType.equalsIgnoreCase(imageTypes[i]))
        {
          supported = true;
        }
      }
    }
    else
    {
      supported = true;
    }

    if (supported)
    {
      workbook.addDrawing(image);
      drawings.add(image);
      images.add(image);
    }
    else
    {
      StringBuilder message = new StringBuilder("Image type ");
      message.append(fileType);
      message.append(" not supported.  Supported types are ");
      message.append(imageTypes[0]);
      for (int i = 1 ; i < imageTypes.length ; i++)
      {
        message.append(", ");
        message.append(imageTypes[i]);
      }
      logger.warn(message.toString());
    }
  }

  /**
   * Gets the number of images on this sheet
   *
   * @return the number of images on this sheet
   */
  @Override
  public int getNumberOfImages()
  {
    return images.size();
  }

  /**
   * Accessor for a particular image on this sheet
   *
   * @param i the 0-based image index number
   * @return the image with the specified index number
   */
  @Override
  public WritableImage getImage(int i)
  {
    return images.get(i);
  }

  /**
   * Accessor for a particular image on this sheet
   *
   * @param i the 0-based image index number
   * @return the image with the specified index number
   */
  public Image getDrawing(int i)
  {
    return images.get(i);
  }

  /**
   * Removes the specified image from this sheet.  The image passed in
   * must be the same instance as that retrieved from a getImage call
   *
   * @param wi the image to remove
   */
  @Override
  public void removeImage(WritableImage wi)
  {
    drawings.remove(wi);
    images.remove(wi);
    drawingsModified = true;
    workbook.removeDrawing(wi);
  }

  /**
   * Validates the sheet name
   */
  private String validateName(String n)
  {
    if (n.length() > maxSheetNameLength)
    {
      logger.warn("Sheet name " + n + " too long - truncating");
      n = n.substring(0, maxSheetNameLength);
    }

    if (n.charAt(0) == '\'')
    {
      logger.warn("Sheet naming cannot start with \' - removing");
      n = n.substring(1);
    }

    for (int i = 0 ; i < illegalSheetNameCharacters.length ; i++)
    {
      String newname = n.replace(illegalSheetNameCharacters[i], '@');
      if (n != newname)
      {
        logger.warn(illegalSheetNameCharacters[i] +
        " is not a valid character within a sheet name - replacing");
      }
      n = newname;
    }

    return n;
  }

  /**
   * Adds a drawing to the list - typically used for comments
   *
   * @param the drawing to add
   */
  void addDrawing(DrawingGroupObject o)
  {
    drawings.add(o);
    Assert.verify(!(o instanceof Drawing));
  }

  /**
   * Removes a drawing to the list - typically used for comments
   *
   * @param the drawing to add
   */
  void removeDrawing(DrawingGroupObject o)
  {
    int origSize = drawings.size();
    drawings.remove(o);
    int newSize = drawings.size();
    drawingsModified = true;
    Assert.verify(newSize == origSize -1);
  }

  /**
   * Removes the data validation for the specified cell.  Called from
   * CellValue in response to a cell being replaced
   *
   * @param cv the cell being removed
   */
  void removeDataValidation(CellValue cv)
  {
    if (dataValidation != null)
    {
      dataValidation.removeDataValidation(cv.getColumn(), cv.getRow());
    }

    if (validatedCells != null)
    {
      boolean result = validatedCells.remove(cv);

      if (!result)
      {
        logger.warn("Could not remove validated cell " +
                    CellReferenceHelper.getCellReference(cv));
      }
    }
  }

  /**
   * Accessor for the page breaks on this sheet
   *
   * @return the page breaks on this sheet
   */
  @Override
  public IHorizontalPageBreaks getRowPageBreaks()
  {
    return rowBreaks;
  }

  /**
   * Accessor for the page breaks on this sheet
   *
   * @return the page breaks on this sheet
   */
  @Override
  public IVerticalPageBreaks getColumnPageBreaks()
  {
    return columnBreaks;
  }

  /**
   * Flags the added cell as having data validation
   *
   * @param cell the cell with data validation
   */
  void addValidationCell(CellValue cv)
  {
    validatedCells.add(cv);
  }

  /**
   * Accessor for the combo box object used for list data validations on this
   * sheet
   *
   * @return the combo box
   */
  ComboBox getComboBox()
  {
    return comboBox;
  }

  /**
   * Sets the combo box object used for list validations on this sheet
   *
   * @param cb the combo box
   */
  void setComboBox(ComboBox cb)
  {
    comboBox = cb;
  }

  /**
   * Gets the data validation.  Retrieved by CellValue when copying sheets
   */
  public DataValidation getDataValidation()
  {
    return dataValidation;
  }

  /**
   * Performs the column autosizing
   */
  private void autosizeColumns()
  {
    Iterator<Integer> i = autosizedColumns.iterator();
    while (i.hasNext())
    {
      Integer col = i.next();
      autosizeColumn(col);
    }
  }

  /**
   * Autosizes the specified column
   *
   * @param col the column to autosize
   */
  private void autosizeColumn(int col)
  {
    int maxWidth = 0;
    ColumnInfoRecord cir = getColumnInfo(col);
    Font columnFont = cir.getCellFormat().getFont();
    Font defaultFont = WritableWorkbook.NORMAL_STYLE.getFont();

    for (int i = 0 ; i < numRows; i++)
    {
      Cell cell = null;
      if (rows.get(i) != null)
      {
        cell = rows.get(i).getCell(col);
      }

      if (cell != null)
      {
        String contents = cell.getContents();
        Font font = cell.getCellFormat().getFont();

        Font activeFont = font.equals(defaultFont) ? columnFont : font;

        int pointSize = activeFont.getPointSize();
        int numChars = contents.length();

        if (activeFont.isItalic() ||
            activeFont.getBoldWeight() > 400) // magic value for normal bold
        {
          numChars += 2;
        }

        int points = numChars * pointSize;
        maxWidth = Math.max(maxWidth, points * 256);
      }
    }
    cir.setWidth(maxWidth / defaultFont.getPointSize());
  }

  /**
   * Imports a sheet from a different workbook
   *
   * @param s the sheet to import
   */
  void importSheet(Sheet s)
  {
    // Copy the settings
    settings = new SheetSettings(s.getSettings(), this);

    SheetCopier si = new SheetCopier(s, this, rowBreaks, columnBreaks);
    si.setColumnFormats(columnFormats);
    si.setFormatRecords(formatRecords);
    si.setHyperlinks(hyperlinks);
    si.setMergedCells(mergedCells);
    si.setSheetWriter(sheetWriter);
    si.setDrawings(drawings);
    si.setImages(images);
    si.setValidatedCells(validatedCells);

    si.importSheet();

    dataValidation = si.getDataValidation();
    comboBox = si.getComboBox();
    plsRecord = si.getPLSRecord();
    chartOnly = si.isChartOnly();
    buttonPropertySet = si.getButtonPropertySet();
    numRows = si.getRows();
    maxRowOutlineLevel = si.getMaxRowOutlineLevel();
    maxColumnOutlineLevel = si.getMaxColumnOutlineLevel();
  }

  /**
   * Extend the data validation contained in the specified cell across and
   * downwards
   *
   * @param c the number of cells accross to apply this data validation
   * @param r the number of cells downwards to apply this data validation
   */
  @Override
  public void applySharedDataValidation(WritableCell c,
                                        int extraCols,
                                        int extraRows)
    throws WriteException
  {
    // Check that the cell being applied has a data validation
    if (c.getWritableCellFeatures() == null ||
        !c.getWritableCellFeatures().hasDataValidation())
    {
      logger.warn("Cannot extend data validation for " +
                  CellReferenceHelper.getCellReference(c.getColumn(),
                                                       c.getRow()) +
                  " as it has no data validation");
      return;
    }

    // Check that none of the other cells in the range have any
    // cell validations
    int startColumn = c.getColumn();
    int startRow = c.getRow();
    int endRow = Math.min(numRows - 1, startRow + extraRows);
    for (int y = startRow; y <= endRow; y++)
    {
      if (rows.get(y) != null)
      {
        int endCol = Math.min(rows.get(y).getMaxColumn() -1,
                             startColumn + extraCols);
        for (int x = startColumn; x <= endCol; x++)
        {
          // Ignore the first cell
          if (x == startColumn && y == startRow)
          {
            continue; // continue statements - they're no better than gotos
          }

          WritableCell c2 = rows.get(y).getCell(x);

          // Check that the target cell does not have any data validation
          if (c2 != null &&
              c2.getWritableCellFeatures() != null &&
              c2.getWritableCellFeatures().hasDataValidation())
          {
            logger.warn("Cannot apply data validation from " +
                        CellReferenceHelper.getCellReference(startColumn,
                                                             startRow) +
                        " to " +
                        CellReferenceHelper.getCellReference
                          (startColumn + extraCols,
                           startRow + extraRows) +
                        " as cell " +
                        CellReferenceHelper.getCellReference(x, y) +
                        " already has a data validation");
            return;
          }
        }
      }
    }

    // Extend the range on the source data validation
    WritableCellFeatures sourceDataValidation = c.getWritableCellFeatures();
    sourceDataValidation.getDVParser().extendCellValidation(extraCols,
                                                            extraRows);

    // Go through all the additional cells and add the data validation cell
    for (int y = startRow; y <= startRow + extraRows; y++)
    {
      RowRecord rowrec = getRowRecord(y); // create the row if it doesn't exist
      for (int x = startColumn; x <= startColumn + extraCols; x++)
      {
        // Ignore the first cell
        if (x == startColumn && y == startRow)
        {
          continue; // continue statements - they're no better than gotos
        }

        WritableCell c2 = rowrec.getCell(x);

        // Check that the target cell does not have any data validation
        if (c2 == null)
        {
          Blank b = new Blank(x, y);
          WritableCellFeatures validation = new WritableCellFeatures();
          validation.shareDataValidation(sourceDataValidation);
          b.setCellFeatures(validation);
          addCell(b);
        }
        else
        {
          // add the shared data validation to the existing cell
          WritableCellFeatures validation = c2.getWritableCellFeatures();

          if (validation != null)
          {
            validation.shareDataValidation(sourceDataValidation);
          }
          else
          {
            validation = new WritableCellFeatures();
            validation.shareDataValidation(sourceDataValidation);
            c2.setCellFeatures(validation);
          }
        }
      }
    }
  }

 /**
   * Remove the shared data validation from multiple cells.  The cell passed
   * in is the top left cell.  The data validation is removed from this
   * cell and all cells which share the same validation.
   *
   * @param cell the top left cell containing the shared data validation
   */
  @Override
  public void removeSharedDataValidation(WritableCell cell)
    throws WriteException
  {
    WritableCellFeatures wcf = cell.getWritableCellFeatures();
    if (wcf == null ||
        !wcf.hasDataValidation())
    {
      return;
    }

    DVParser dvp = wcf.getDVParser();

    // If the cell is not part of an extended validation, then simply call
    // the atomic remove validation from the cell features
    if (!dvp.extendedCellsValidation())
    {
      wcf.removeDataValidation();
      return;
    }

    // Check that the cell validation being removed is in the top left of the
    // validated area
    if (dvp.extendedCellsValidation())
    {
      if (cell.getColumn() != dvp.getFirstColumn() ||
          cell.getRow() != dvp.getFirstRow())
      {
        logger.warn("Cannot remove data validation from " +
                    CellReferenceHelper.getCellReference(dvp.getFirstColumn(),
                                                         dvp.getFirstRow()) +
                    "-" +
                    CellReferenceHelper.getCellReference(dvp.getLastColumn(),
                                                         dvp.getLastRow()) +
                    " because the selected cell " +
                    CellReferenceHelper.getCellReference(cell) +
                    " is not the top left cell in the range");
        return;
      }
    }

    for (int y = dvp.getFirstRow(); y <= dvp.getLastRow(); y++)
    {
      for (int x = dvp.getFirstColumn(); x <= dvp.getLastColumn(); x++)
      {
        CellValue c2 = rows.get(y).getCell(x);

        // It's possible that some cells in the shared data range might
        // be null eg. in the event of an insertRow or insertColumn
        if (c2 != null)
        {
          c2.getWritableCellFeatures().removeSharedDataValidation();
          c2.removeCellFeatures();
        }
      }
    }

    // Remove this shared validation from any data validations that were
    // copied in
    if (dataValidation != null)
    {
      dataValidation.removeSharedDataValidation(dvp.getFirstColumn(),
                                                dvp.getFirstRow(),
                                                dvp.getLastColumn(),
                                                dvp.getLastRow());
    }
  }

  private void removeCell(int row, int column) throws RowsExceededException {
    if (row >= rows.size())
      return;

    RowRecord rowrec = getRowRecord(row);
    rowrec.removeCell(column);
  }

}
