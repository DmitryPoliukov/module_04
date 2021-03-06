package com.epam.esm.service;

import com.epam.esm.repository.dto.UserDto;

import java.util.List;

public interface UserService {

    UserDto read(int id);

    List<UserDto> readAll(int page, int size);

    UserDto saveUser(UserDto userDto);

    UserDto findByEmail(String email);


}
