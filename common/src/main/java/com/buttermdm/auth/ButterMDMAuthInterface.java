package com.buttermdm.auth;

import com.buttermdm.persistence.domain.User;

public interface ButterMDMAuthInterface {
    User findUser(String login);
    boolean authenticate(User user, String password);
}
