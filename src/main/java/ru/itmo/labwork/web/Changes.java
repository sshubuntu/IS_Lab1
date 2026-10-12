package ru.itmo.labwork.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.push.Push;
import jakarta.faces.push.PushContext;
import jakarta.inject.Inject;

/**
 * Рассылает всем открытым клиентам сигнал о том, что состав объектов изменился. Страницы
 * подписываются на канал через {@code f:websocket} и перезагружают только таблицу.
 */
@ApplicationScoped
public class Changes {
  @Inject
  @Push(channel = "labwork")
  private PushContext channel;

  public void fire() {
    channel.send("changed");
  }
}
