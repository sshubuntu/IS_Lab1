package ru.itmo.labwork.web;

import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import ru.itmo.labwork.repository.UserStore;

@Named
@RequestScoped
public class Register {
  @Inject private UserStore users;
  private String username = "", password = "", confirm = "";

  public String submit() {
    String user = username == null ? "" : username.trim();
    if (user.length() < 3) return fail("Логин должен быть не короче 3 символов");
    if (password == null || password.length() < 4)
      return fail("Пароль должен быть не короче 4 символов");
    if (!password.equals(confirm)) return fail("Пароли не совпадают");
    if (users.exists(user)) return fail("Логин уже занят");
    users.register(user, password);
    username = "";
    password = "";
    confirm = "";
    FacesContext.getCurrentInstance()
        .addMessage(
            null,
            new FacesMessage(
                FacesMessage.SEVERITY_INFO, "Аккаунт создан. Теперь можно войти.", null));
    return null;
  }

  private String fail(String text) {
    FacesContext.getCurrentInstance()
        .addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, text, null));
    return null;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String v) {
    username = v;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String v) {
    password = v;
  }

  public String getConfirm() {
    return confirm;
  }

  public void setConfirm(String v) {
    confirm = v;
  }
}
