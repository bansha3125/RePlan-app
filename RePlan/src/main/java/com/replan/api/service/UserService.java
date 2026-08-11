package com.replan.api.service;

import com.replan.api.entity.User;
import com.replan.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User getOrCreateUser(String deviceUuid) {
        return userRepository.findByDeviceUuid(deviceUuid)
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .deviceUuid(deviceUuid)
                                .build()
                ));
    }
}