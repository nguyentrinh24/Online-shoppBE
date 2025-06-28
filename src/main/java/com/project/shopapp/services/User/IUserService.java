package com.project.shopapp.services.User;

import com.project.shopapp.dtos.UpdateUserDTO;
import com.project.shopapp.dtos.UserDTO;
import com.project.shopapp.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IUserService {
    User createUser(UserDTO userDTO) throws Exception;
    String login(String phoneNumber, String password) throws Exception;
    User getUserDetailsFromToken(String token) throws Exception;
    User updateUser(Long userId, UpdateUserDTO updatedUserDTO) throws Exception;
    
    // Admin methods
    Page<User> getAllUsers(String keyword, Pageable pageable) throws Exception;
    void deleteUser(Long userId) throws Exception;
    User updateUserRole(Long userId, String newRole) throws Exception;
}
