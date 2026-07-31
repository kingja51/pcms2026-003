package com.gonet.primary.member.oauth2.service;

import com.gonet.config.oauth2.OAuth2Properties;
import com.gonet.primary.member.oauth2.dto.ExternalProfile;
import com.gonet.primary.member.oauth2.dto.OAuth2Provider;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * RestClient 기반 OAuth2 provider 호출 구현.
 *
 * <p>네이버/카카오/구글 모두 동일 골격:
 * <ol>
 *   <li>token endpoint POST (form-urlencoded) → access_token</li>
 *   <li>userinfo endpoint GET (Bearer) → JSON</li>
 *   <li>provider 별 응답 구조 차이를 {@link #parseProfile} 에서 흡수</li>
 * </ol>
 *
 * <p>실패 응답은 {@link OAuth2Exception} 으로 일원화 — controller 는 사용자 메시지로만 변환.
 */
@Service
public class OAuth2ServiceImpl extends EgovAbstractServiceImpl implements OAuth2Service {

    private static final Logger log = LoggerFactory.getLogger(OAuth2ServiceImpl.class);

    private final OAuth2Properties props;
    private final RestClient       client;

    public OAuth2ServiceImpl(OAuth2Properties props,
                              @Qualifier("oauth2RestClient") RestClient client) {
        this.props  = props;
        this.client = client;
    }

    @Override
    public boolean isProviderConfigured(OAuth2Provider provider) {
        if (!props.isEnabled() || provider == null) return false;
        return switch (provider) {
            case NAVER  -> props.getNaver().isEnabled()
                && hasText(props.getNaver().getClientId())
                && hasText(props.getNaver().getClientSecret());
            case KAKAO  -> props.getKakao().isEnabled()
                && hasText(props.getKakao().getClientId());
            case GOOGLE -> props.getGoogle().isEnabled()
                && hasText(props.getGoogle().getClientId())
                && hasText(props.getGoogle().getClientSecret());
        };
    }

    @Override
    public String buildAuthorizeUrl(OAuth2Provider provider, String state, String redirectUri) {
        String clientId = clientIdOf(provider);
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(provider.authorizeUrl())
            .queryParam("response_type", "code")
            .queryParam("client_id",     clientId)
            .queryParam("redirect_uri",  redirectUri)
            .queryParam("state",         state)
            .queryParam("scope",         provider.defaultScope());
        // 카카오는 prompt=login 권장 (계정 선택 강제), 구글은 access_type=online
        if (provider == OAuth2Provider.GOOGLE) {
            b.queryParam("access_type", "online")
             .queryParam("include_granted_scopes", "true");
        }
        // build().encode() — 공백/콜론 등 미인코딩 값을 자동 percent-encoding.
        // build(true) 는 이미 인코딩된 값을 가정하므로 scope="openid email profile" 의 공백에서 verify 실패.
        return b.build().encode().toUriString();
    }

    @Override
    public ExternalProfile exchangeAndFetchProfile(OAuth2Provider provider, String code, String redirectUri) {
        if (!isProviderConfigured(provider)) {
            throw new OAuth2Exception("provider 설정이 누락되었습니다: " + provider.name());
        }
        String accessToken = exchangeToken(provider, code, redirectUri);
        Map<String, Object> userinfo = fetchUserinfo(provider, accessToken);
        ExternalProfile profile = parseProfile(provider, userinfo);
        if (!profile.hasIdentity()) {
            throw new OAuth2Exception("외부 식별자(provider_user_id) 를 찾을 수 없습니다.");
        }
        return profile;
    }

    // ------------------------------------------------------------------
    // token endpoint POST
    // ------------------------------------------------------------------

    private String exchangeToken(OAuth2Provider provider, String code, String redirectUri) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type",   "authorization_code");
        body.add("client_id",    clientIdOf(provider));
        body.add("redirect_uri", redirectUri);
        body.add("code",         code);
        // secret 미사용 케이스 (kakao 비공개 secret 옵션) — null 이면 생략
        String secret = clientSecretOf(provider);
        if (hasText(secret)) {
            body.add("client_secret", secret);
        }

        try {
            Map<String, Object> resp = client.post()
                .uri(provider.tokenUrl())
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.CONTENT_TYPE,
                    MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=" + StandardCharsets.UTF_8.name())
                .body(body)
                .retrieve()
                .body(Map.class);
            if (resp == null || !resp.containsKey("access_token")) {
                log.warn("OAUTH2_TOKEN_EMPTY provider={} resp={}", provider, resp);
                throw new OAuth2Exception("access_token 발급 실패");
            }
            return String.valueOf(resp.get("access_token"));
        } catch (OAuth2Exception oe) {
            throw oe;
        } catch (Exception ex) {
            log.warn("OAUTH2_TOKEN_FAIL provider={} reason={}", provider, ex.getMessage());
            throw new OAuth2Exception("토큰 교환 실패", ex);
        }
    }

    // ------------------------------------------------------------------
    // userinfo endpoint GET
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchUserinfo(OAuth2Provider provider, String accessToken) {
        try {
            Map<String, Object> resp = client.get()
                .uri(provider.userinfoUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .body(Map.class);
            if (resp == null || resp.isEmpty()) {
                throw new OAuth2Exception("userinfo 응답이 비어 있습니다.");
            }
            return resp;
        } catch (OAuth2Exception oe) {
            throw oe;
        } catch (Exception ex) {
            log.warn("OAUTH2_USERINFO_FAIL provider={} reason={}", provider, ex.getMessage());
            throw new OAuth2Exception("사용자 정보 조회 실패", ex);
        }
    }

    // ------------------------------------------------------------------
    // provider 별 응답 정규화
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private ExternalProfile parseProfile(OAuth2Provider provider, Map<String, Object> body) {
        ExternalProfile p = new ExternalProfile();
        p.setProvider(provider);

        switch (provider) {
            case NAVER -> {
                // {"resultcode":"00","message":"success","response":{"id":"...","email":"...","name":"..."}}
                Object rawResponse = body.get("response");
                if (!(rawResponse instanceof Map)) break;
                Map<String, Object> resp = (Map<String, Object>) rawResponse;
                p.setProviderUserId(asString(resp.get("id")));
                p.setEmail(asString(resp.get("email")));
                p.setName(asString(resp.get("name")));
                p.setNickname(asString(resp.get("nickname")));
            }
            case KAKAO -> {
                // {"id":12345,"kakao_account":{"email":"...","profile":{"nickname":"..."}}}
                p.setProviderUserId(asString(body.get("id")));
                Object rawAcc = body.get("kakao_account");
                if (rawAcc instanceof Map) {
                    Map<String, Object> acc = (Map<String, Object>) rawAcc;
                    p.setEmail(asString(acc.get("email")));
                    Object rawProfile = acc.get("profile");
                    if (rawProfile instanceof Map) {
                        Map<String, Object> prof = (Map<String, Object>) rawProfile;
                        p.setNickname(asString(prof.get("nickname")));
                        p.setName(asString(prof.get("nickname")));   // 카카오는 실명 미제공 — nickname 으로 대체
                    }
                }
            }
            case GOOGLE -> {
                // {"sub":"...","email":"...","name":"...","given_name":"...","family_name":"..."}
                p.setProviderUserId(asString(body.get("sub")));
                p.setEmail(asString(body.get("email")));
                p.setName(asString(body.get("name")));
                p.setNickname(asString(body.get("given_name")));
            }
        }
        return p;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private String clientIdOf(OAuth2Provider provider) {
        return switch (provider) {
            case NAVER  -> props.getNaver().getClientId();
            case KAKAO  -> props.getKakao().getClientId();
            case GOOGLE -> props.getGoogle().getClientId();
        };
    }

    private String clientSecretOf(OAuth2Provider provider) {
        return switch (provider) {
            case NAVER  -> props.getNaver().getClientSecret();
            case KAKAO  -> props.getKakao().getClientSecret();
            case GOOGLE -> props.getGoogle().getClientSecret();
        };
    }

    private static String asString(Object o) {
        return (o == null) ? null : String.valueOf(o);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
