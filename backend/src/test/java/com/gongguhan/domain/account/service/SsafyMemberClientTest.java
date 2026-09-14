package com.gongguhan.domain.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SsafyMemberClientTest {

    @Test
    void memberBaseUrlStripsEduPrefix() {
        assertEquals("https://finopenapi.ssafy.io/ssafy/api/v1",
                SsafyMemberClient.memberBaseUrl("https://finopenapi.ssafy.io/ssafy/api/v1/edu"));
        assertEquals("https://finopenapi.ssafy.io/ssafy/api/v1",
                SsafyMemberClient.memberBaseUrl("https://finopenapi.ssafy.io/ssafy/api/v1/edu/"));
    }

    @Test
    void memberBaseUrlKeepsUrlWithoutEduPrefix() {
        assertEquals("https://finopenapi.ssafy.io/ssafy/api/v1",
                SsafyMemberClient.memberBaseUrl("https://finopenapi.ssafy.io/ssafy/api/v1"));
    }
}
