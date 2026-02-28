package com.fooholdings.fdp.sources.kroger.auth;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class KrogerAuthHeaderUtilTest {

    @Test
    void buildBasicAuthHeader_producesCorrectFormat() {
        String header = KrogerAuthHeaderUtil.buildBasicAuthHeader("myClientId", "mySecret");
        assertThat(header).startsWith("Basic ");
    }

    @Test
    void buildBasicAuthHeader_encodesCredentialsCorrectly() {
        String clientId = "myClientId";
        String secret = "mySecret";
        String header = KrogerAuthHeaderUtil.buildBasicAuthHeader(clientId, secret);

        String encoded = header.replace("Basic ", "");
        String decoded = new String(Base64.getDecoder().decode(encoded));
        assertThat(decoded).isEqualTo(clientId + ":" + secret);
    }

    @Test
    void buildBasicAuthHeader_handlesSpecialCharactersInSecret() {
        String header = KrogerAuthHeaderUtil.buildBasicAuthHeader("id", "s3cr3t!@#");
        assertThat(header).startsWith("Basic ");
        String decoded = new String(Base64.getDecoder().decode(header.replace("Basic ", "")));
        assertThat(decoded).isEqualTo("id:s3cr3t!@#");
    }
}
