package com.algrex.campus.students.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Minimal persistence foundation; member B will extend it with the agreed data dictionary. */
@Entity
@Table(name = "student_profiles")
public class StudentProfile {
  @Id private UUID id;

  @Column(name = "student_code", nullable = false, unique = true, length = 64)
  private String studentCode;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  protected StudentProfile() {}

  public StudentProfile(UUID id, String studentCode, String displayName) {
    this.id = id;
    this.studentCode = studentCode;
    this.displayName = displayName;
  }

  public UUID getId() {
    return id;
  }

  public String getStudentCode() {
    return studentCode;
  }

  public String getDisplayName() {
    return displayName;
  }
}
