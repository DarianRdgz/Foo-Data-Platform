package com.fooholdings.fdp.sources.kroger.auth;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fooholdings.fdp.sources.kroger.dto.token.KrogerTokenResponse;

@ExtendWith(MockitoExtension.class)
class KrogerTokenServiceTest {

    @Mock
    private KrogerAuthClient authClient;

    private KrogerTokenService tokenService;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        tokenService = new KrogerTokenService(authClient);
    }

    private KrogerTokenResponse mockResponse(String token, long expiresIn) {
        KrogerTokenResponse resp = new KrogerTokenResponse();
        resp.setAccessToken(token);
        resp.setExpiresIn(expiresIn);
        resp.setTokenType("Bearer");
        return resp;
    }

    @Test
    void getValidToken_fetchesTokenOnFirstCall() {
        when(authClient.fetchToken()).thenReturn(mockResponse("token-1", 1800));

        String token = tokenService.getValidToken();

        assertThat(token).isEqualTo("token-1");
        verify(authClient, times(1)).fetchToken();
    }

    @Test
    void getValidToken_doesNotRefreshWhenTokenIsValid() {
        when(authClient.fetchToken()).thenReturn(mockResponse("token-1", 1800));

        tokenService.getValidToken();
        tokenService.getValidToken();
        tokenService.getValidToken();

        // Token is valid for 1800s — should only fetch once
        verify(authClient, times(1)).fetchToken();
    }

    @Test
    void getValidToken_refreshesWhenTokenIsExpired() {
        // First call returns an already-expired token (expiresIn=0 means it expires immediately)
        when(authClient.fetchToken())
                .thenReturn(mockResponse("token-expired", 0))
                .thenReturn(mockResponse("token-fresh", 1800));

        tokenService.getValidToken(); // fetches token-expired
        String second = tokenService.getValidToken(); // should refresh since token-expired is expired

        assertThat(second).isEqualTo("token-fresh");
        verify(authClient, times(2)).fetchToken();
    }

    @Test
    void forceRefresh_alwaysFetchesNewToken() {
        when(authClient.fetchToken()).thenReturn(mockResponse("token-1", 1800));
        tokenService.getValidToken();

        when(authClient.fetchToken()).thenReturn(mockResponse("token-2", 1800));
        String refreshed = tokenService.forceRefresh();

        assertThat(refreshed).isEqualTo("token-2");
        verify(authClient, times(2)).fetchToken();
    }

    @Test
    void getCurrentState_returnsNullBeforeFirstFetch() {
        assertThat(tokenService.getCurrentState()).isNull();
    }

    @Test
    void getCurrentState_returnsStateAfterFetch() {
        when(authClient.fetchToken()).thenReturn(mockResponse("token-1", 1800));
        tokenService.getValidToken();

        assertThat(tokenService.getCurrentState()).isNotNull();
        assertThat(tokenService.getCurrentState().accessToken()).isEqualTo("token-1");
    }
}
