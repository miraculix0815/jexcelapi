package jxl.write.biff;

import java.io.IOException;
import java.nio.file.Files;
import jxl.WorkbookSettings;
import jxl.biff.FormattingRecords;
import static jxl.biff.Type.HORIZONTALPAGEBREAKS;
import static jxl.read.biff.HorizontalPageBreaksRecordTest.biff8pb;
import jxl.write.*;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * created 2015-03-08
 * @author Jan Schlößin
 */
public class HorizontalPageBreaksRecordTest {
  
  @Test
  public void testCreationOfBiff8() {
    int [] pageBreaks = new int[] {13, 18};
    HorizontalPageBreaksRecord wpb = new HorizontalPageBreaksRecord();
    wpb.setRowBreaks(biff8pb);
    assertArrayEquals(new byte[]{(byte) HORIZONTALPAGEBREAKS.value, 0, 14, 0, 2, 0, 13, 0, 0, 0, (byte) 255, (byte) 255, 18, 0, 0, 0, (byte) 255, (byte) 255},
            wpb.getBytes());
  }
  
  @Test
  public void testInsertionOfRowBreaks() {
    WritableSheetImpl w = new WritableSheetImpl("a", null, null, null, null, null);
    assertTrue(w.getRowPageBreaks().getRowBreaks().isEmpty());
    w.addRowPageBreak(5);
    assertEquals(5, (int) w.getRowPageBreaks().getRowBreaks().get(0));
  }
  
  @Test
  public void testDoubleInsertionOfRowBreaks() {
    WritableSheetImpl w = new WritableSheetImpl("a", null, null, null, null, null);
    w.addRowPageBreak(5);
    assertEquals(1, w.getRowPageBreaks().getRowBreaks().size());
    w.addRowPageBreak(5);
    assertEquals(1, w.getRowPageBreaks().getRowBreaks().size());
  }
  
  @Test
  public void testInsertionOfRow() throws WriteException, IOException {
    WritableSheetImpl w = new WritableSheetImpl("[a]", null, new FormattingRecords(null), new SharedStrings(), new WorkbookSettings(), new WritableWorkbookImpl(Files.newOutputStream(Files.createTempFile(null, null)), true, new WorkbookSettings()));
    w.addCell(new Blank(10, 10));
    w.addRowPageBreak(5);
    w.addRowPageBreak(6);
    w.insertRow(6);
    assertArrayEquals(new Integer[] {5,7}, w.getRowPageBreaks().getRowBreaks().toArray());
    w.insertRow(0);
    assertArrayEquals(new Integer[] {6,8}, w.getRowPageBreaks().getRowBreaks().toArray());
  }
  
  @Test
  public void testRemovalOfRow() throws WriteException, IOException {
    WritableSheetImpl w = new WritableSheetImpl("[a]", null, new FormattingRecords(null), new SharedStrings(), new WorkbookSettings(), new WritableWorkbookImpl(Files.newOutputStream(Files.createTempFile(null, null)), true, new WorkbookSettings()));
    w.addCell(new Blank(10, 10));
    w.addRowPageBreak(6);
    w.addRowPageBreak(8);
    w.removeRow(0);
    assertArrayEquals(new Integer[] {5,7}, w.getRowPageBreaks().getRowBreaks().toArray());
    w.removeRow(6);
    assertArrayEquals(new Integer[] {5,6}, w.getRowPageBreaks().getRowBreaks().toArray());
    w.removeRow(6);
    assertArrayEquals(new Integer[] {5}, w.getRowPageBreaks().getRowBreaks().toArray());
  }
  
}