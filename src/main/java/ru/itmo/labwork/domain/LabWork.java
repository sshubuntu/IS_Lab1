package ru.itmo.labwork.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
@Table(name = "lab_work")
public class LabWork extends RecordEntity {
  @NotBlank(message = "Название лабораторной работы не может быть пустым")
  @Size(max = 1000000, message = "Название не должно превышать {max} символов")
  @Column(name = "name", nullable = false, length = 1000000)
  private String name;

  public String getName() {
    return name;
  }

  public void setName(String value) {
    name = value;
  }

  @NotNull(message = "Координаты обязательны для заполнения")
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "coordinates_id", nullable = false)
  private Coordinates coordinates;

  public Coordinates getCoordinates() {
    return coordinates;
  }

  public void setCoordinates(Coordinates value) {
    coordinates = value;
  }

  @org.hibernate.annotations.Generated(org.hibernate.annotations.GenerationTime.INSERT)
  @Column(name = "creation_date", nullable = false, insertable = false, updatable = false)
  @NotNull(message = "Дата создания обязательна")
  private java.time.LocalDateTime creationDate = java.time.LocalDateTime.now();

  public java.time.LocalDateTime getCreationDate() {
    return creationDate;
  }

  public void setCreationDate(java.time.LocalDateTime value) {
    creationDate = value;
  }

  @NotBlank(message = "Описание не может быть пустым")
  @Size(max = 1000000, message = "Описание не должно превышать {max} символов")
  @Column(name = "description", nullable = false, length = 1000000)
  private String description;

  public String getDescription() {
    return description;
  }

  public void setDescription(String value) {
    description = value;
  }

  @NotNull(message = "Сложность обязательна для заполнения")
  @Enumerated(EnumType.STRING)
  @Column(name = "difficulty", nullable = false)
  private Difficulty difficulty;

  public Difficulty getDifficulty() {
    return difficulty;
  }

  public void setDifficulty(Difficulty value) {
    difficulty = value;
  }

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "discipline_id", nullable = true)
  private Discipline discipline;

  public Discipline getDiscipline() {
    return discipline;
  }

  public void setDiscipline(Discipline value) {
    discipline = value;
  }

  @Positive(message = "Минимальный балл должен быть больше 0")
  @Column(name = "minimal_point", nullable = false)
  private int minimalPoint;

  public int getMinimalPoint() {
    return minimalPoint;
  }

  public void setMinimalPoint(int value) {
    minimalPoint = value;
  }

  @Positive(message = "Максимум личностных качеств должен быть больше 0")
  @Column(name = "personal_qualities_maximum", nullable = false)
  private long personalQualitiesMaximum;

  public long getPersonalQualitiesMaximum() {
    return personalQualitiesMaximum;
  }

  public void setPersonalQualitiesMaximum(long value) {
    personalQualitiesMaximum = value;
  }

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "author_id", nullable = true)
  private Person author;

  public Person getAuthor() {
    return author;
  }

  public void setAuthor(Person value) {
    author = value;
  }
}
