package ru.itmo.labwork;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.*;
import org.junit.jupiter.api.*;
import ru.itmo.labwork.domain.*;
import ru.itmo.labwork.service.Catalog;

class ValidationTest {
  static ValidatorFactory factory;
  static Validator v;

  @BeforeAll
  static void setup() {
    factory = Validation.buildDefaultValidatorFactory();
    v = factory.getValidator();
  }

  @AfterAll
  static void close() {
    factory.close();
  }

  @Test
  void invalidLab() {
    assertTrue(v.validate(new LabWork()).size() >= 5);
  }

  @Test
  void locationLimitAndNonFinite() {
    Location l = new Location();
    l.setX(Float.NaN);
    l.setY(1L);
    l.setName("x".repeat(292));
    assertEquals(2, v.validate(l).size());
  }

  @Test
  void nullableFields() {
    Discipline d = new Discipline();
    d.setName("Алгоритмы");
    assertTrue(v.validate(d).isEmpty());
  }

  @Test
  void personRequiredLocationAndCountry() {
    Person p = new Person();
    p.setName("Анна");
    assertEquals(2, v.validate(p).size());
  }

  @Test
  void allNestedStringsAreFilterable() {
    assertTrue(
        Catalog.stringColumns("LabWork")
            .containsAll(
                java.util.List.of(
                    "name",
                    "description",
                    "discipline.name",
                    "author.name",
                    "author.location.name",
                    "author.nationality")));
  }

  @Test
  void unknownKindsRejected() {
    assertThrows(IllegalArgumentException.class, () -> Catalog.type("LabWork; delete"));
  }
}
