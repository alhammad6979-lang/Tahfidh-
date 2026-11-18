package com.example

import com.example.data.QuranReferenceData
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun quranReferenceData_has114SurahsAnd6236Ayat() {
        assertEquals(114, QuranReferenceData.allSurahs.size)
        assertEquals(6236, QuranReferenceData.allSurahs.sumOf { it.ayatCount })
        assertEquals(30, QuranReferenceData.allJuz.size)
    }
}
