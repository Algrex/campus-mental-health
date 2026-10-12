package com.algrex.campus.demo;

import java.util.ArrayList;
import java.util.List;

/** Reads comma-separated rows, including quoted fields and escaped double quotes. */
public final class CsvRows {
  private CsvRows() {}
  public static List<List<String>> parse(String text) {
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean quoted = false;
    String input = text.replace("\ufeff", "");
    for (int i = 0; i < input.length(); i++) {
      char value = input.charAt(i);
      if (value == '"') {
        if (quoted && i + 1 < input.length() && input.charAt(i + 1) == '"') { field.append('"'); i++; }
        else { quoted = !quoted; }
      } else if (value == ',' && !quoted) {
        row.add(field.toString().trim()); field.setLength(0);
      } else if ((value == '\n' || value == '\r') && !quoted) {
        if (value == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') { i++; }
        row.add(field.toString().trim()); field.setLength(0);
        if (row.stream().anyMatch(item -> !item.isBlank())) { rows.add(row); }
        row = new ArrayList<>();
      } else { field.append(value); }
    }
    if (quoted) { throw new IllegalArgumentException("CSV 引号未闭合"); }
    if (!field.isEmpty() || !row.isEmpty()) { row.add(field.toString().trim()); rows.add(row); }
    return rows;
  }
}
