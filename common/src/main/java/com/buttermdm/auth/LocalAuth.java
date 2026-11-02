package com.buttermdm.auth;

import com.google.inject.Inject;
import com.buttermdm.persistence.UnsecureDAO;
import com.buttermdm.persistence.UserDAO;
import com.buttermdm.persistence.domain.User;
import com.buttermdm.util.PasswordUtil;

import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
public class LocalAuth implements ButterMDMAuthInterface {

    private UnsecureDAO userDAO;

    @Inject
    public LocalAuth(UnsecureDAO userDAO) {
        this.userDAO = userDAO;
    }

    @Override
    public User findUser(String login) {
        return userDAO.findByLoginOrEmail(login);
    }

    @Override
    public boolean authenticate(User user, String password) {
        boolean match = PasswordUtil.passwordMatch(password, user.getPassword());
        if (!match) {
            userDAO.setUserLoginFailTime(user, System.currentTimeMillis());
        }
        return match;
    }
}
