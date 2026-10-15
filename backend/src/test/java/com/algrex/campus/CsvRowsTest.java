package com.algrex.campus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.algrex.campus.demo.CsvRows;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvRowsTest {
  @Test
  void quotedFieldsBomEscapedQuotesAndWindowsNewlinesArePreserved() {
    assertThat(
            CsvRows.parse("\ufeffa,b\r\n\"x,y\",\"say \"\"hi\"\"\"\r\n\"line1\nline2\",z\r\n\r\n"))
        .containsExactly(
            List.of("a", "b"), List.of("x,y", "say \"hi\""), List.of("line1\nline2", "z"));
    assertThat(CsvRows.parse("a,b,")).containsExactly(List.of("a", "b", ""));
    assertThat(CsvRows.parse("")).isEmpty();
    assertThatThrownBy(() -> CsvRows.parse("a,\"unclosed"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
