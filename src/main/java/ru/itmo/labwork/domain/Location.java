package ru.itmo.labwork.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
@Table(name = "location")
public class Location extends RecordEntity {
  @AssertTrue(message = "Значение x должно быть конечным числом (не NaN, не Infinity)")
  public boolean isFinite() {
    return x == null || Float.isFinite(x);
  }

  @NotNull(message = "Поле x обязательно для заполнения")
  @Column(name = "x", nullable = false)
  private Float x;

  public Float getX() {
    return x;
  }

  public void setX(Float value) {
    x = value;
  }

  @NotNull(message = "Поле y обязательно для заполнения")
  @Column(name = "y", nullable = false)
  private Long y;

  public Long getY() {
    return y;
  }

  public void setY(Long value) {
    y = value;
  }

  @Size(max = 291, message = "Название не должно превышать {max} символов")
  @Column(name = "name", nullable = true, length = 291)
  private String name;

  public String getName() {
    return name;
  }

  public void setName(String value) {
    name = value;
  }
}
