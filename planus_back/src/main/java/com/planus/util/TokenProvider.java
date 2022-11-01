package com.planus.util;

import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import org.hibernate.property.access.internal.PropertyAccessMapImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TokenProvider {

    @Value("${jwt.secret}")
    private String secretKey;

    private static Long accessTokenTime = 60*60*24*30*1000L;

    public String createToken(Authentication authentication, long userId){
        String authorities = authentication
                .getAuthorities()
                .stream().map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        Map<String, Object> kakaoAccount = ((OAuth2User) authentication.getPrincipal()).getAttribute("kakao_account");
        String email = (String) kakaoAccount.get("email");

        Map<String, Object> profile = (Map<String, Object>)kakaoAccount.get("profile");
        String nickname = (String)profile.get("nickname");
        String imageUrl = (String)profile.get("profile_image_url");

        Date validity = getValidity();

        String token = Jwts.builder()
                .setSubject("planus_token")
                .claim("auth", authorities)
                .claim("userId", String.valueOf(userId))
                .claim("nickname", nickname)
                .claim("email", email)
                .claim("imageUrl", imageUrl)
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .setExpiration(validity)
                .compact();
        return token;
    }

    private Date getValidity() {
        Long now = new Date().getTime();
        Date validity = new Date(now + this.accessTokenTime);
        return validity;
    }

    public Authentication getAuthentication(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(secretKey)
                .parseClaimsJws(token)
                .getBody();

        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get("auth")
                        .toString().split(","))
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

        User principal = new User(claims.getSubject(),"",authorities);
        return new UsernamePasswordAuthenticationToken(principal, token, authorities);

    }

    public boolean validateToken(String token){
        try{
            Claims claims = Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody();
            return true;
        }catch(SecurityException | MalformedJwtException | SignatureException e){
            System.out.println("JWT 서명 문제");
        }catch(ExpiredJwtException e){
            System.out.println("jwt 만료");
        }catch(UnsupportedJwtException e){
            System.out.println("지원하지 않는 토큰??");
        }catch(IllegalArgumentException e){
            System.out.println("JWT 토큰 잘못됨");
        }
        return false;
    }

    public long getUserId(String token){
        try{
            Claims claims = Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody();
            return Long.parseLong( String.valueOf(claims.get("userId")));
        }catch (SignatureException e){
            System.out.println("토큰 서명과정에서 에러 발생함");
        }catch (Exception e){
            System.out.println("토큰 파싱과정에서 에러발생(서명아님)");
            e.printStackTrace();
        }
        return -1;
    }

    public String updateTokenNickname(String token, String newNickname){
        Date validity = getValidity();
        Claims claims = Jwts
                .parser()
                .setSigningKey(secretKey)
                .parseClaimsJws(token)
                .getBody();

        String newToken = Jwts.builder()
                .setClaims(claims)
                .claim("nickname", newNickname)
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .setExpiration(validity)
                .compact();

        return newToken;
    }
}
