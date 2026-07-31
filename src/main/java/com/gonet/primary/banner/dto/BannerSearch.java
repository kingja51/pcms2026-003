package com.gonet.primary.banner.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BannerSearch extends PageRequest {

    private String siteId;
    private String bannerLocation;
    private String useYn;
}
