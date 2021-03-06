package com.epam.esm.filter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.epam.esm.exception.PermissionException;
import com.epam.esm.repository.dto.UserCredentialDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

public class CustomAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private static final long EXPIRATION_IN_MINUTES_ACCESS = 1;
    private static final long EXPIRATION_IN_MINUTES_REFRESH = 100;
    private static final String JWT_SECRET = "secret";

    private final AuthenticationManager authenticationManager;

    public CustomAuthenticationFilter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request,
                                                HttpServletResponse response) throws AuthenticationException {
        UserCredentialDto userCredentialDto;
        try {
            String jsonString = request.getReader().lines().collect(Collectors.joining());
            StringReader reader = new StringReader(jsonString);
            ObjectMapper mapper = new ObjectMapper();
            userCredentialDto = mapper.readValue(reader, UserCredentialDto.class);
        } catch (IOException e) {
            throw  new PermissionException("Incorrect input data");
        }
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(userCredentialDto.getEmail(), userCredentialDto.getPassword());
        return authenticationManager.authenticate(authenticationToken);
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain, Authentication authResult) throws IOException {
        User user = (User) authResult.getPrincipal();
        Algorithm algorithm = Algorithm.HMAC256(JWT_SECRET.getBytes());
        String accessToken = JWT.create()
                .withSubject(user.getUsername())
                .withExpiresAt(Date.from(LocalDateTime.now().plusMinutes(EXPIRATION_IN_MINUTES_ACCESS)
                        .atZone(ZoneId.systemDefault()).toInstant()))
                .withClaim("roles", user.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()))
                .sign(algorithm);
        String refreshToken = JWT.create()
                .withSubject(user.getUsername())
                .withExpiresAt(Date.from(LocalDateTime.now().plusMinutes(EXPIRATION_IN_MINUTES_REFRESH)
                        .atZone(ZoneId.systemDefault()).toInstant()))
                .sign(algorithm);
        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", accessToken);
        tokens.put("refreshToken", refreshToken);
        response.setContentType(APPLICATION_JSON_VALUE);
        new ObjectMapper().writeValue(response.getOutputStream(), tokens);
    }
}
