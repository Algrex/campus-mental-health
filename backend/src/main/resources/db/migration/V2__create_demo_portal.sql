ALTER TABLE student_profiles ADD COLUMN college VARCHAR(100) DEFAULT '计算机学院' NOT NULL;
ALTER TABLE student_profiles ADD COLUMN major VARCHAR(100) DEFAULT '计算机科学与技术' NOT NULL;
ALTER TABLE student_profiles ADD COLUMN year_name VARCHAR(30) DEFAULT '大一' NOT NULL;
ALTER TABLE student_profiles ADD COLUMN gender VARCHAR(20) DEFAULT '未填写' NOT NULL;
ALTER TABLE student_profiles ADD COLUMN age INTEGER DEFAULT 20 NOT NULL;
ALTER TABLE student_profiles ADD COLUMN contact_name VARCHAR(100) DEFAULT '家属' NOT NULL;
ALTER TABLE student_profiles ADD COLUMN risk_level VARCHAR(20) DEFAULT 'LOW' NOT NULL;
ALTER TABLE student_profiles ADD COLUMN current_score INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE student_profiles ADD COLUMN last_activity TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL;
ALTER TABLE student_profiles ADD COLUMN source_name VARCHAR(100) DEFAULT '演示录入' NOT NULL;

CREATE TABLE assessments (
  id UUID PRIMARY KEY,
  student_id UUID NOT NULL REFERENCES student_profiles(id),
  answers VARCHAR(100) NOT NULL,
  score INTEGER NOT NULL CHECK (score BETWEEN 0 AND 27),
  risk_level VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  request_id VARCHAR(80) NOT NULL UNIQUE,
  source_name VARCHAR(80) NOT NULL
);
CREATE INDEX idx_assessments_student_date ON assessments(student_id, created_at);

CREATE TABLE check_ins (
  id UUID PRIMARY KEY,
  student_id UUID NOT NULL REFERENCES student_profiles(id),
  mood INTEGER NOT NULL CHECK (mood BETWEEN 1 AND 5),
  sleep_hours DECIMAL(4,1) NOT NULL CHECK (sleep_hours BETWEEN 0 AND 24),
  stress INTEGER NOT NULL CHECK (stress BETWEEN 1 AND 5),
  note VARCHAR(600) NOT NULL,
  check_date DATE NOT NULL,
  source_name VARCHAR(80) NOT NULL,
  UNIQUE(student_id, check_date)
);

CREATE TABLE risk_snapshots (
  id UUID PRIMARY KEY,
  student_id UUID NOT NULL REFERENCES student_profiles(id),
  score INTEGER NOT NULL,
  risk_level VARCHAR(20) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_risk_snapshots_date ON risk_snapshots(created_at);

CREATE TABLE alerts (
  id UUID PRIMARY KEY,
  student_id UUID NOT NULL REFERENCES student_profiles(id),
  risk_level VARCHAR(20) NOT NULL,
  status VARCHAR(30) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  assigned_to VARCHAR(100),
  close_reason VARCHAR(600)
);

CREATE TABLE interventions (
  id UUID PRIMARY KEY,
  alert_id UUID NOT NULL REFERENCES alerts(id),
  kind VARCHAR(50) NOT NULL,
  content VARCHAR(1200) NOT NULL,
  next_date DATE,
  author_name VARCHAR(100) NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE notifications (
  id UUID PRIMARY KEY,
  alert_id UUID REFERENCES alerts(id),
  recipient VARCHAR(80) NOT NULL,
  title VARCHAR(150) NOT NULL,
  content VARCHAR(500) NOT NULL,
  is_read BOOLEAN DEFAULT FALSE NOT NULL,
  created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_notifications_recipient ON notifications(recipient, is_read);
CREATE TABLE app_meta (key_name VARCHAR(80) PRIMARY KEY, value_text VARCHAR(500) NOT NULL);
