CREATE DATABASE IF NOT EXISTS school;
USE school;

CREATE TABLE teachers (
    teacher_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    subject_specialty VARCHAR(80) NOT NULL,
    hire_date DATE NOT NULL
);
CREATE INDEX idx_teachers_subject ON teachers (subject_specialty);
CREATE INDEX idx_teachers_name ON teachers (name);

CREATE TABLE students (
    student_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    grade VARCHAR(20) NOT NULL,
    enrolled_date DATE NOT NULL
);
CREATE INDEX idx_students_grade ON students (grade);
CREATE INDEX idx_students_enrolled ON students (enrolled_date);

CREATE TABLE classes (
    class_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    teacher_id INT NOT NULL,
    room VARCHAR(30),
    FOREIGN KEY (teacher_id) REFERENCES teachers(teacher_id)
);
CREATE INDEX idx_classes_teacher ON classes (teacher_id);
CREATE UNIQUE INDEX ux_classes_name_teacher ON classes (name, teacher_id);

CREATE TABLE subjects (
    subject_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE,
    description TEXT
);

CREATE TABLE marks (
    mark_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    subject_id INT NOT NULL,
    marks_obtained INT NOT NULL,
    max_marks INT NOT NULL,
    exam_date DATE NOT NULL,
    FOREIGN KEY (student_id) REFERENCES students(student_id),
    FOREIGN KEY (subject_id) REFERENCES subjects(subject_id),
    CHECK (marks_obtained >= 0),
    CHECK (max_marks > 0),
    CHECK (marks_obtained <= max_marks)
);
CREATE INDEX idx_marks_student ON marks (student_id);
CREATE INDEX idx_marks_subject ON marks (subject_id);
CREATE INDEX idx_marks_exam_date ON marks (exam_date);

CREATE TABLE attendance (
    attendance_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    attendance_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    FOREIGN KEY (student_id) REFERENCES students(student_id),
    CHECK (status IN ('Present','Absent','Leave'))
);
CREATE INDEX idx_attendance_student_date ON attendance (student_id, attendance_date);

CREATE TABLE parents (
    parent_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    relation VARCHAR(50) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(150),
    FOREIGN KEY (student_id) REFERENCES students(student_id)
);
CREATE INDEX idx_parents_student ON parents (student_id);
CREATE INDEX idx_parents_relation ON parents (relation);

CREATE TABLE exams (
    exam_id INT AUTO_INCREMENT PRIMARY KEY,
    subject_id INT NOT NULL,
    exam_name VARCHAR(120) NOT NULL,
    exam_date DATE NOT NULL,
    total_marks INT NOT NULL,
    FOREIGN KEY (subject_id) REFERENCES subjects(subject_id),
    CHECK (total_marks > 0)
);
CREATE INDEX idx_exams_subject ON exams (subject_id);
CREATE INDEX idx_exams_date ON exams (exam_date);
