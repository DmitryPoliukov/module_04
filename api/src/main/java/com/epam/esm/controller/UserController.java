package com.epam.esm.controller;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.epam.esm.hateoas.HateoasAdder;
import com.epam.esm.repository.dto.UserDto;
import com.epam.esm.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Class {@code UserController} is an endpoint of the API which allows to perform operations on users.
 * Annotated by {@link RestController} with no parameters to provide an answer in application/json.
 * Annotated by {@link RequestMapping} with parameter value = "/users".
 * So that {@code UserController} is accessed by sending request to /users.
 *
 * @author Dmitry Poliukov
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final HateoasAdder<UserDto> userHateoasAdder;

    public UserController(UserService userService, HateoasAdder<UserDto> userHateoasAdder) {
        this.userService = userService;
        this.userHateoasAdder = userHateoasAdder;
    }

    private static final String JWT_SECRET = "secret";
    private static final String BEARER = "Bearer ";
    private static final long EXPIRATION_IN_MINUTES_ACCESS = 1;

    /**
     * Method for getting user by ID.
     *
     * @param id ID of user
     * @return Found user entity with hateoas
     */
    @GetMapping("/{id}")
    public UserDto read(@PathVariable int id) {
        UserDto userDto = userService.read(id);
      userHateoasAdder.addLinks(userDto);
        return userDto;
    }

    /**
     * Method for getting all users from data source.
     *
     * @param page the number of page for pagination
     * @param size the size of page for pagination
     * @return List of found users with hateoas
     */
    @GetMapping
    public List<UserDto> readAll(@RequestParam(value = "page", defaultValue = "1", required = false) @Min(1) int page,
                                  @RequestParam(value = "size", defaultValue = "5", required = false) @Min(1) int size) {
        List<UserDto> userDtos = userService.readAll(page, size);
        userDtos.forEach(userHateoasAdder::addLinks);
        return userDtos;

    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto saveUser(@Valid @RequestBody UserDto userDto) {
        UserDto savedUser = userService.saveUser(userDto);
        userHateoasAdder.addLinks(savedUser);
        return savedUser;
    }

    @GetMapping("/token/refresh")
    public void refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String authorizationHeader = request.getHeader(AUTHORIZATION);
        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER)) {
            try {
                String refreshToken = authorizationHeader.substring(BEARER.length());
                Algorithm algorithm = Algorithm.HMAC256(JWT_SECRET.getBytes());
                JWTVerifier verifier = JWT.require(algorithm).build();
                DecodedJWT decodedJWT = verifier.verify(refreshToken);
                String email = decodedJWT.getSubject();
                UserDto user = userService.findByEmail(email);
                String accessToken = JWT.create()
                        .withSubject(email)
                        .withExpiresAt(Date.from(LocalDateTime.now().plusMinutes(EXPIRATION_IN_MINUTES_ACCESS)
                                .atZone(ZoneId.systemDefault()).toInstant()))
                        .withClaim("role", Collections.singletonList(user.getRole().toString()))
                        .sign(algorithm);
                Map<String, String> tokens = new HashMap<>();
                tokens.put("accessToken", accessToken);
                tokens.put("refreshToken", refreshToken);
                response.setContentType(APPLICATION_JSON_VALUE);
                new ObjectMapper().writeValue(response.getOutputStream(), tokens);
            } catch (Exception e) {
                response.setHeader("error", e.getMessage());
                response.setStatus(FORBIDDEN.value());
                Map<String, String> error = new HashMap<>();
                error.put("error_message", e.getMessage());
                response.setContentType(APPLICATION_JSON_VALUE);
                new ObjectMapper().writeValue(response.getOutputStream(), error);
            }
        } else {
            throw new RuntimeException("Refresh token is missing");
        }

    }
}
