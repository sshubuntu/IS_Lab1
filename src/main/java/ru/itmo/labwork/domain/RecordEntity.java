package ru.itmo.labwork.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;

@MappedSuperclass
public abstract class RecordEntity implements Serializable {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Positive
  private Integer id;

  @Version private long version;

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public long getVersion() {
    return version;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + " #" + id;
  }
}
