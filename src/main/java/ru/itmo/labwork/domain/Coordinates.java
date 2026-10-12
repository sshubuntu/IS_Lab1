package ru.itmo.labwork.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
@Table(name = "coordinates")
public class Coordinates extends RecordEntity {
  @AssertTrue(message = "Значение y должно быть конечным числом (не NaN, не Infinity)")
  public boolean isFinite() {
    return y == null || Float.isFinite(y);
  }

  @Column(name = "x", nullable = false)
  private int x;

  public int getX() {
    return x;
  }

  public void setX(int value) {
    x = value;
  }

  @NotNull(message = "Поле y обязательно для заполнения")
  @Column(name = "y", nullable = false)
  private Float y;

  public Float getY() {
    return y;
  }

  public void setY(Float value) {
    y = value;
  }
}
