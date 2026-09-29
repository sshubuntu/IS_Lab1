package ru.itmo.labwork.repository;

import jakarta.annotation.*;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import org.flywaydb.core.Flyway;

@Singleton
@Startup
public class Database {
  public static String env(String name, String fallback) {
    return System.getenv().getOrDefault(name, fallback);
  }

  @PostConstruct
  public void start() {
    String url =
        env(
            "DB_URL",
            "jdbc:postgresql://" + env("DB_HOST", "pg") + ":5432/" + env("DB_NAME", "studs"));
    String user = env("DB_USER", "studs"), password = env("DB_PASSWORD", "");
    Flyway.configure()
        .dataSource(url, user, password)
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .load()
        .migrate();
  }
}