package com.carddemo.cbact04c;
import static org.junit.jupiter.api.Assertions.*;
import java.math.*;import org.junit.jupiter.api.Test;
import com.carddemo.cbact04c.io.*;import com.carddemo.cbact04c.util.*;
class CodecTest {
 @Test void overpunchedValuesRoundTrip(){assertEquals("00000001940{",ZonedDecimal.format(new BigDecimal("1940.00"),10));assertEquals(new BigDecimal("1940.00"),ZonedDecimal.parse("00000001940{"));assertEquals(new BigDecimal("-12.39"),ZonedDecimal.parse("00000000012R"));}
 @Test void realRecordsParseAtCopybookOffsets(){var t=RecordCodecs.tranCat("000000000010100010000000000{0000000000000000000000");assertEquals("00000000001",t.acctId());assertEquals("01",t.typeCd());assertEquals("0001",t.catCd());assertEquals(new BigDecimal("0.00"),t.balance());var x=RecordCodecs.xref("050002445376574000000005000000000050");assertEquals("00000000005",x.acctId());var d=RecordCodecs.disc("A00000000001000100150{0000000000000000000000000000");assertEquals("A000000000",d.key().groupId());assertEquals(new BigDecimal("15.00"),d.rate());}
 @Test void shortXrefIsPadded(){assertEquals(11,RecordCodecs.xref("CARD").acctId().length());}
}
