-- Foundation only: no real student records or demonstration accounts are seeded.
CREATE TABLE student_profiles (
  id UUID PRIMARY KEY,
  student_code VARCHAR(64) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  CONSTRAINT uk_student_profiles_code UNIQUE (student_code)
);
