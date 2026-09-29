package ru.itmo.labwork.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
@Table(name = "location")
public class Location extends RecordEntity {
  @AssertTrue(message = "x должно быть конечным числом")
  public boolean isFinite() {
    return x == null || Float.isFinite(x);
  }

  @NotNull
  @Column(name = "x", nullable = false)
  private Float x;

  public Float getX() {
    return x;
  }

  public void setX(Float value) {
    x = value;
  }

  @NotNull
  @Column(name = "y", nullable = false)
  private Long y;

  public Long getY() {
    return y;
  }

  public void setY(Long value) {
    y = value;
  }

  @Size(max = 291)
  @Column(name = "name", nullable = true, length = 291)
  private String name;

  public String getName() {
    return name;
  }

  public void setName(String value) {
    name = value;
  }
}
