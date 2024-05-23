package com.example.retrospect.keycloak.config;

import java.util.*;

import com.example.retrospect.user.entity.UserEntity;
import com.example.retrospect.user.service.IUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class CustomKeycloakAuthenticationProvider implements AuthenticationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomKeycloakAuthenticationProvider.class);

    private final ApplicationContext appCtx;
    private final Jwt2AuthenticationConverter jwt2AuthenticationConverter;
    private final JwtDecoder jwtDecoder;

    public CustomKeycloakAuthenticationProvider(ApplicationContext appCtx,
                                                Jwt2AuthenticationConverter jwt2AuthenticationConverter, JwtDecoder jwtDecoder) {
        this.appCtx = appCtx;
        this.jwt2AuthenticationConverter = jwt2AuthenticationConverter;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        BearerTokenAuthenticationToken bearer = (BearerTokenAuthenticationToken) authentication;
        Jwt jwt = getJwt(bearer);
        JwtAuthenticationToken token = this.jwt2AuthenticationConverter.convert(jwt);
        IUserService userService = appCtx.getBean(IUserService.class);
        SecurityContextHolder.getContext().setAuthentication(token);
        String email = jwt.getClaimAsString("email");
        Optional<UserEntity> userDetailsOptional = Optional.ofNullable(userService.getEmail(email));
        UserEntity userDetails = userDetailsOptional.orElse(null);


        if (userDetails == null) {
            try {
                Map<String, String> userInfoMapper = new HashMap<>();
                String accessToken = (String) authentication.getPrincipal();
                userInfoMapper.put("email", email);
                userInfoMapper.put("token", accessToken);
                String req = Utils.convertObjectToJson(userInfoMapper);
                LOGGER.debug("The request passed {}", req);
                // Implement logic for handling user not found, e.g., creating a new user or throwing an exception
            }   catch (Exception e) {
                LOGGER.error("Error in saving the roles", e);
            }
        }

        userDetails = userService.getEmail(email);
        Collection<? extends GrantedAuthority> grantedAuthorities = addUserSpecificAuthorities(
                token != null ? token.getAuthorities() : authentication.getAuthorities(), userDetails);
        JwtAuthenticationToken jwtAuthenticationToken = new JwtAuthenticationToken(jwt, grantedAuthorities);
        jwtAuthenticationToken.setDetails(bearer.getDetails());
        return jwtAuthenticationToken;
    }

    private Jwt getJwt(BearerTokenAuthenticationToken bearer) {
        try {
            return this.jwtDecoder.decode(bearer.getToken());
        } catch (BadJwtException failed) {
            throw new InvalidBearerTokenException(failed.getMessage(), failed);
        } catch (JwtException failed) {
            throw new AuthenticationServiceException(failed.getMessage(), failed);
        }
    }

    protected SimpleAuthorityMapper mapAuthorityMapper() {
        SimpleAuthorityMapper grantedAuthorityMapper = new SimpleAuthorityMapper();
        grantedAuthorityMapper.setPrefix("ROLE_");
        grantedAuthorityMapper.setConvertToUpperCase(true);
        return grantedAuthorityMapper;
    }

    protected Collection<? extends GrantedAuthority> addUserSpecificAuthorities(
            Collection<? extends GrantedAuthority> authorities, UserEntity userDetails) {
        List<GrantedAuthority> result = new ArrayList<>(authorities);
        result.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (userDetails != null) {
            for (String role : userDetails.getUserRole().split(",")) {
                LOGGER.info("Role Received {}", role);
                if (role != null)
                    result.add(new SimpleGrantedAuthority(role));
            }
        } else {
            result.add(new SimpleGrantedAuthority("ROLE_GUEST"));
        }
        return result;
    }

    @Override
    public boolean supports(Class<?> aClass) {
        return BearerTokenAuthenticationToken.class.isAssignableFrom(aClass);
    }

    public String getToken(Object userPrincipal) {
        String accessToken = null;
        if (userPrincipal instanceof Jwt) {
            LOGGER.debug("Inside Keycloak Security Context principal");
            Jwt customUserData = (Jwt) userPrincipal;
            if (customUserData != null)
                accessToken = customUserData.getTokenValue();
        }
        return accessToken;
    }
}
