package com.gonet.primary.member.oauth2.service;

import com.gonet.primary.member.oauth2.dto.MemberOAuth;
import com.gonet.primary.member.oauth2.dto.MemberOAuthSearch;

import java.util.List;

public interface MemberOAuthMngService {
    List<MemberOAuth> search(MemberOAuthSearch search);
    int count(MemberOAuthSearch search);
    MemberOAuth get(String memberOauthId);
    List<MemberOAuth> findForExport(MemberOAuthSearch search);
}
