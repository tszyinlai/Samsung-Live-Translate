package com.example.livetranslate;

import org.junit.Test;
import static org.junit.Assert.*;

public class CaptionHistoryTest {
    @Test public void confirmedLineCanBeTranslated() {
        CaptionHistory h = new CaptionHistory();
        CaptionHistory.Line l = h.add("We have an appointment.");
        h.complete(l,"我們有一個預約。");
        assertTrue(h.asText().contains("We have an appointment."));
        assertTrue(h.asText().contains("我們有一個預約。"));
    }
    @Test public void ignoresEmptyLinesAndCanClear() {
        CaptionHistory h = new CaptionHistory();
        assertNull(h.add("  "));
        h.add("Hello");
        h.clear();
        assertEquals("",h.asText());
    }
    @Test public void memoryIsBounded() {
        CaptionHistory h = new CaptionHistory();
        for(int i=0;i<200;i++) h.add("phrase #"+i);
        assertFalse(h.asText().contains("phrase #0\n"));
        assertTrue(h.asText().contains("phrase #199"));
    }
}
