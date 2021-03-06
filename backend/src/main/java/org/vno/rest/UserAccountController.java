package org.vno.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vno.domain.Repo;
import org.vno.domain.Role;
import org.vno.domain.UserAccount;
import org.vno.repository.UserAccountRepository;

import java.util.Collections;
import java.util.HashSet;

/**
 * @author kk
 */
@RequestMapping(value = "/user", produces = "application/json")
@RestController
public class UserAccountController {

    private final UserAccountRepository userAccountRepository;

    @Autowired
    public UserAccountController(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    /**
     * @param id required user id
     * @return user info or 404 not found
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    ResponseEntity<?> get(@PathVariable Long id) {
        UserAccount user = userAccountRepository.findOneWithRepos(id);
        if (null == user) {
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        } else {
            user.setPassword("");
            return ResponseEntity.ok(user);
        }
    }

    /**
     * @return signed-in user info
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/")
    ResponseEntity<?> get() {
        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        UserAccount user = userAccountRepository.findByUsername(authentication
                .getName());
        user.setPassword("");
        if (user.getRepos() != null) {
            for (Repo r : user.getRepos()) {
                r.setBranches(null);
                r.setTags(null);
            }
        }
        return ResponseEntity.ok(user);
    }

    /**
     * Registers new user
     * TODO: permit all or secure to admin level
     *
     * @param user user info, including password
     * @return 400 if username or email are presented, 200 otherwise
     */
    @PostMapping("/register/")
    ResponseEntity<?> register(@RequestBody UserAccount user) {
        if (null != userAccountRepository.findByUsernameOrEmail(
                user.getUsername(), user.getEmail())) {
            return new ResponseEntity<>("Already exists",
                    HttpStatus.BAD_REQUEST);
        }
        user.setRoles(new HashSet<>(Collections.singleton(Role.USER)));
        userAccountRepository.save(user);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
