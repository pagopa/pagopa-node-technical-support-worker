package it.gov.pagopa.nodetsworker.resources.mapper;

import it.gov.pagopa.nodetsworker.repository.models.PositionPaymentSSEntity;
import it.gov.pagopa.nodetsworker.resources.response.PositionPaymentSSInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.io.Serializable;
import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA)
public abstract class SnapshotMapper implements Serializable {

    @ConfigProperty(name = "db.serviceIdentifier")
    String dbServiceIdentifier;

    public abstract List<PositionPaymentSSInfo> toPositionPaymentSSInfoList(List<PositionPaymentSSEntity> positionPaymentSSEntityList);

    @Mapping(source = "paFiscalCode", target = "organizationFiscalCode")
    @Mapping(source = "noticeId", target = "noticeNumber")
    @Mapping(target = "serviceIdentifier", expression = "java(this.dbServiceIdentifier)")
    public abstract PositionPaymentSSInfo toPositionPaymentSSInfo(PositionPaymentSSEntity positionPaymentSSEntity);

}

