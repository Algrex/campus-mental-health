package com.algrex.campus.students.repository;

import com.algrex.campus.students.domain.StudentProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal persistence API; no automatic public REST exposure. */
public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {}
