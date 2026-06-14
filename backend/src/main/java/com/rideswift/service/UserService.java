package com.rideswift.service;

import com.rideswift.dto.request.UpdateUserRequest;
import com.rideswift.dto.response.UserResponse;
import com.rideswift.exception.ConflictException;
import com.rideswift.exception.ResourceNotFoundException;
import com.rideswift.model.User;
import com.rideswift.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return UserResponse.from(loadUser(id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAll() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = loadUser(id);
        if (StringUtils.hasText(request.name())) {
            user.setName(request.name());
        }
        if (StringUtils.hasText(request.phone()) && !request.phone().equals(user.getPhone())) {
            if (userRepository.existsByPhone(request.phone())) {
                throw new ConflictException("Phone number already in use");
            }
            user.setPhone(request.phone());
        }
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void delete(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
        userRepository.deleteById(id);
    }

    private User loadUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }
}
