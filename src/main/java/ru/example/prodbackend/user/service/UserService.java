package ru.example.prodbackend.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.auth.service.JwtService;
import ru.example.prodbackend.auth.service.RefreshTokenService;
import ru.example.prodbackend.exception.UserAlreadyExistsException;
import ru.example.prodbackend.exception.UserNotFoundException;
import ru.example.prodbackend.user.dto.ChangePasswordRequest;
import ru.example.prodbackend.user.dto.UpdateProfileRequest;
import ru.example.prodbackend.user.dto.UpdateProfileResponse;
import ru.example.prodbackend.user.dto.UserProfileResponse;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;


    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UserEntity user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }

    @Transactional
    public UpdateProfileResponse updateProfile(UserEntity principal, UpdateProfileRequest request) {
        UserEntity user = currentUser(principal);
        boolean emailChanged = request.getEmail() != null && !request.getEmail().equals(user.getEmail());

        if (emailChanged && userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("User with this email already exists");
        }

        user.updateProfile(request.getEmail(), request.getFirstName(), request.getLastName());
        userRepository.saveAndFlush(user);
        UpdateProfileResponse response = UpdateProfileResponse.builder()
                .profile(getProfile(user))
                .build();

        if (emailChanged) {
            refreshTokenService.deleteAllByUser(user);
            response.setAccessToken(jwtService.generateToken(user));
            response.setRefreshToken(refreshTokenService.createRefreshToken(user));
        }
        return response;
    }

    @Transactional
    public void changePassword(UserEntity principal, ChangePasswordRequest request) {
        UserEntity user = currentUser(principal);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid current password");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.saveAndFlush(user);
        refreshTokenService.deleteAllByUser(user);
    }


    private UserEntity currentUser(UserEntity principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new UserNotFoundException("Current user not found"));
    }
}
