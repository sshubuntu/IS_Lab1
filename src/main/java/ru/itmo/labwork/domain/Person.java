package ru.itmo.labwork.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
@Table(name = "person")
public class Person extends RecordEntity {
  @NotBlank(message = "Имя не может быть пустым")
  @Size(max = 1000000, message = "Имя не должно превышать {max} символов")
  @Column(name = "name", nullable = false, length = 1000000)
  private String name;

  public String getName() {
    return name;
  }

  public void setName(String value) {
    name = value;
  }

  @Enumerated(EnumType.STRING)
  @Column(name = "eye_color", nullable = true)
  private Color eyeColor;

  public Color getEyeColor() {
    return eyeColor;
  }

  public void setEyeColor(Color value) {
    eyeColor = value;
  }

  @Enumerated(EnumType.STRING)
  @Column(name = "hair_color", nullable = true)
  private Color hairColor;

  public Color getHairColor() {
    return hairColor;
  }

  public void setHairColor(Color value) {
    hairColor = value;
  }

  @NotNull(message = "Локация обязательна для заполнения")
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "location_id", nullable = false)
  private Location location;

  public Location getLocation() {
    return location;
  }

  public void setLocation(Location value) {
    location = value;
  }

  @Column(name = "birthday", nullable = true)
  private java.time.LocalDateTime birthday;

  public java.time.LocalDateTime getBirthday() {
    return birthday;
  }

  public void setBirthday(java.time.LocalDateTime value) {
    birthday = value;
  }

  @NotNull(message = "Национальность обязательна для заполнения")
  @Enumerated(EnumType.STRING)
  @Column(name = "nationality", nullable = false)
  private Country nationality;

  public Country getNationality() {
    return nationality;
  }

  public void setNationality(Country value) {
    nationality = value;
  }
}
