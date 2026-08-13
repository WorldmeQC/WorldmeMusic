package top.worldme.music.model;

/**
 * 网易云登录账号信息。
 *
 * @author Worldme
 * @since 1.0.0
 */
public class AccountInfo {

    private String cookie = "";
    private long loginTime;
    private String nickname = "";
    private long userId;

    public String getCookie() {
        return cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    public long getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(long loginTime) {
        this.loginTime = loginTime;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public boolean isLoggedIn() {
        return cookie != null && !cookie.isBlank();
    }
}
