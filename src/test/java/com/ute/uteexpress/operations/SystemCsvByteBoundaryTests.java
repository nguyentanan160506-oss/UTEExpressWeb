package com.ute.uteexpress.operations;

import com.ute.uteexpress.service.AdminRuleException;
import com.ute.uteexpress.service.reports.ReportCsvService;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

/** Unit tests of the byte-buffer backstop, distinct from the real HTTP/JDBC row-limit tests. */
class SystemCsvByteBoundaryTests {
    void line(ByteArrayOutputStream output,String value) {ReflectionTestUtils.invokeMethod(ReportCsvService.class,"line",output,List.of(value));}
    @Test void exactFourMibIsAcceptedAndOneExtraRowCannotPartiallyAppend() {
        var output=new ByteArrayOutputStream();output.writeBytes(new byte[ReportCsvService.MAX_BYTES-5]);line(output,"a");assertEquals(ReportCsvService.MAX_BYTES,output.size());
        assertThrows(AdminRuleException.class,()->line(output,"b"));assertEquals(ReportCsvService.MAX_BYTES,output.size());
    }
    @Test void utf8ByteCountIncludesMultibyteCharactersQuotesAndCrLf() {
        var output=new ByteArrayOutputStream();output.writeBytes(new byte[ReportCsvService.MAX_BYTES-7]);line(output,"界");assertEquals(ReportCsvService.MAX_BYTES,output.size());
    }
    @Test void byteOverflowIsRejectedBeforeWritingAnyOfTheRejectedLine() {
        var output=new ByteArrayOutputStream();output.writeBytes(new byte[ReportCsvService.MAX_BYTES-6]);int before=output.size();
        assertThrows(AdminRuleException.class,()->line(output,"界"));assertEquals(before,output.size());
    }
}
