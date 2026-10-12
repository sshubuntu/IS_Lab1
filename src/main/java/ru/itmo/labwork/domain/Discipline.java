package ru.itmo.labwork.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
@Table(name = "discipline")
public class Discipline extends RecordEntity {
  @NotBlank(message = "Название дисциплины не может быть пустым")
  @Size(max = 1000000, message = "Название не должно превышать {max} символов")
  @Column(name = "name", nullable = false, length = 1000000)
  private String name;

  public String getName() {
    return name;
  }

  public void setName(String value) {
    name = value;
  }

  @Column(name = "lecture_hours", nullable = true)
  private Long lectureHours;

  public Long getLectureHours() {
    return lectureHours;
  }

  public void setLectureHours(Long value) {
    lectureHours = value;
  }

  @Column(name = "practice_hours", nullable = false)
  private int practiceHours;

  public int getPracticeHours() {
    return practiceHours;
  }

  public void setPracticeHours(int value) {
    practiceHours = value;
  }

  @Column(name = "self_study_hours", nullable = false)
  private long selfStudyHours;

  public long getSelfStudyHours() {
    return selfStudyHours;
  }

  public void setSelfStudyHours(long value) {
    selfStudyHours = value;
  }

  @Column(name = "labs_count", nullable = false)
  private long labsCount;

  public long getLabsCount() {
    return labsCount;
  }

  public void setLabsCount(long value) {
    labsCount = value;
  }
}
