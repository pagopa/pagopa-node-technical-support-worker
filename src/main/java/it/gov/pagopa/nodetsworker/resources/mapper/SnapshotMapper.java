package it.gov.pagopa.nodetsworker.resources.mapper;

import it.gov.pagopa.nodetsworker.repository.model.PositionPaymentSSEntity;
import it.gov.pagopa.nodetsworker.resources.response.PositionPaymentSSInfo;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA)
public interface SnapshotMapper {

    SnapshotMapper INSTANCE = Mappers.getMapper(SnapshotMapper.class);

    List<PositionPaymentSSInfo> toPositionPaymentSSInfo(List<PositionPaymentSSEntity> fdrGetPaymentDto);

//    Metadata toMetadata(MetadataDto metadataDto);

}

