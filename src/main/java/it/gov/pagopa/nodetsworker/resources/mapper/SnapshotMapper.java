package it.gov.pagopa.nodetsworker.resources.mapper;

import it.gov.pagopa.nodetsworker.repository.model.PositionPaymentSSEntity;
import it.gov.pagopa.nodetsworker.resources.response.PositionPaymentSSInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.factory.Mappers;

import java.io.Serializable;
import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA)
public interface SnapshotMapper extends Serializable {

    SnapshotMapper INSTANCE = Mappers.getMapper(SnapshotMapper.class);

    List<PositionPaymentSSInfo> toPositionPaymentSSInfoList(List<PositionPaymentSSEntity> positionPaymentSSEntityList);

    @Mapping(source = "paFiscalCode", target = "organizationFiscalCode")
    @Mapping(source = "noticeId", target = "noticeNumber")
    PositionPaymentSSInfo toPositionPaymentSSInfo(PositionPaymentSSEntity positionPaymentSSEntity);

}

