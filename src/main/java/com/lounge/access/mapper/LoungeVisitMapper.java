package com.lounge.access.mapper;

import com.lounge.access.dto.EligibilityResponse;
import com.lounge.access.dto.RedeemPassResponse;
import com.lounge.access.dto.VisitStatus;
import com.lounge.access.entity.LoungeVisit;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = VisitStatus.class)
public interface LoungeVisitMapper {

    @Mapping(target = "visitId", source = "id")
    @Mapping(target = "eligible", expression = "java(visit.getStatus() != VisitStatus.DENIED)")
    @Mapping(target = "reason", source = "reasonMessage")
    @Mapping(target = "denialReason", source = "denialReason")
    @Mapping(target = "visitsRemaining", source = "visitsRemainingSnapshot")
    @Mapping(target = "loungePassToken", source = "passToken")
    @Mapping(target = "passExpiresAt", source = "passExpiresAt")
    EligibilityResponse toEligibilityResponse(LoungeVisit visit);

    @Mapping(target = "visitId", source = "id")
    @Mapping(target = "message", constant = "Entry completed. Pass redeemed.")
    RedeemPassResponse toRedeemPassResponse(LoungeVisit visit);
}
